/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.pes.adapters.tlog;

import com.google.common.flogger.FluentLogger;
import com.google.pes.adapters.EndorsementMapper;
import com.google.pes.annotations.TLedgerUrl;
import com.google.pes.domain.model.Endorsement;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.TLogReceipt;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.InvalidSignatureException;
import com.google.pes.domain.ports.SignatureVerifier;
import com.google.pes.domain.ports.TLog;
import com.google.pes.domain.ports.TLogException;
import com.google.pes.v1.PublicEndorsement;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.google.tledger.v1.Entry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Implementation of the {@link TLog} port that interacts with the TLedger service over HTTP.
 *
 * <p>This adapter handles the serialization of endorsements to the TLedger entry format and manages
 * the HTTP communication with the TLedger API.
 */
@Singleton
public class TLedger implements TLog {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String TLEDGER_POST_SUFIX = "/v1/entries";

  private final String tLedgerUrl;
  private final JsonFormat.Printer jsonPrinter = JsonFormat.printer();
  private final JsonFormat.Parser jsonParser = JsonFormat.parser();
  private final CloseableHttpClient httpClient;
  private final SignatureVerifier signatureVerifier;
  private final TLedgerCertificatFetcher certificatFetcher;

  @Inject
  public TLedger(
      @TLedgerUrl String tLedgerUrl,
      CloseableHttpClient httpClient,
      SignatureVerifier signatureVerifier,
      TLedgerCertificatFetcher certificatFetcher) {
    this.tLedgerUrl = tLedgerUrl;
    this.httpClient = httpClient;
    this.signatureVerifier = signatureVerifier;
    this.certificatFetcher = certificatFetcher;
  }

  /**
   * Posts an endorsement to the TLedger service.
   *
   * <p>The endorsement is serialised to bytes and used as a raw entry inside the request
   *
   * @throws TLogException if the communication with TLedger fails or the response cannot be parsed.
   */
  @Override
  public TLogReceipt post(Endorsement publicEndorsement) {
    ByteString rawEntry = formatRawEntry(publicEndorsement);
    Entry entry = Entry.newBuilder().setRawEntry(rawEntry).build();

    String jsonBody;
    try {
      jsonBody = jsonPrinter.print(entry);
    } catch (InvalidProtocolBufferException impossible) {
      // Entry proto does not contain unknown Any types
      throw new AssertionError(impossible);
    }

    URI fullUrl = URI.create(tLedgerUrl + TLEDGER_POST_SUFIX);
    HttpPost httpPost = new HttpPost(fullUrl);
    httpPost.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

    String responseBody;
    try {
      // BasicHttpClientResponseHandler handles non-2xx status codes by throwing
      // HttpResponseException.
      responseBody = httpClient.execute(httpPost, new BasicHttpClientResponseHandler());
    } catch (HttpResponseException e) {
      throw new TLogException("TLedger API Error: " + e.getStatusCode(), e);
    } catch (IOException e) {
      throw new TLogException("Failed to communicate with Transparency Ledger", e);
    }
    logger.atInfo().log("Response: %s", responseBody);

    Entry resultEntry;
    Entry.Builder entryBuilder = Entry.newBuilder();
    try {
      jsonParser.merge(responseBody, entryBuilder);
    } catch (InvalidProtocolBufferException e) {
      throw new TLogException("Failed to parse TLedger response", e);
    }
    resultEntry = entryBuilder.build();
    verifyTLedgerSignature(resultEntry);
    return new TLogReceipt(resultEntry.getName());
  }

  public boolean isHealthy() {
    URI fullUrl = URI.create(tLedgerUrl + "/healthz");
    HttpGet httpGet = new HttpGet(fullUrl);
    try {
      // BasicHttpClientResponseHandler handles non-2xx status codes by throwing
      // HttpResponseException.
      httpClient.execute(httpGet, new BasicHttpClientResponseHandler());
      return true;
    } catch (HttpResponseException e) {
      logger.atWarning().log("TLedger health check returned status %d", e.getStatusCode());
      return false;
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to communicate with TLedger for health check");
      return false;
    }
  }

  private void verifyTLedgerSignature(Entry entry) {
    ByteString certificateBytes = certificatFetcher.fetch();
    try {
      signatureVerifier.verify(
          new Signature(
              entry.getSignature(),
              new VerificationMaterial(certificateBytes, VerificationMaterial.Format.X509_DER)),
          entry.getRawEntry());
    } catch (InvalidSignatureException e) {
      throw new TLogException(
          "Failed to verify TLedger signature for entry: " + entry.getName(), e);
    }
  }

  private ByteString formatRawEntry(Endorsement publicEndorsement) {
    return PublicEndorsement.newBuilder(EndorsementMapper.toProto(publicEndorsement))
        .clearEndorsementSignatures()
        .clearTlogReceipt()
        .build()
        .toByteString();
  }
}
