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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.pes.adapters.EndorsementMapper;
import com.google.pes.domain.model.Endorsement;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.model.TLogReceipt;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.InvalidSignatureException;
import com.google.pes.domain.ports.SignatureVerifier;
import com.google.pes.domain.ports.TLogException;
import com.google.pes.v1.PublicEndorsement;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.google.tledger.v1.Entry;
import java.io.IOException;
import java.util.Collections;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class TLedgerTest {

  private static final String TLEDGER_URL = "http://fake-tledger.com";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private CloseableHttpClient mockHttpClient;
  @Mock private SignatureVerifier mockSignatureVerifier;
  @Mock private TLedgerCertificatFetcher mockCertificateFetcher;
  @Captor private ArgumentCaptor<HttpPost> httpPostCaptor;
  @Captor private ArgumentCaptor<HttpGet> httpGetCaptor;

  private TLedger tLedger;
  private Endorsement testEndorsement;

  @Before
  public void setUp() {
    tLedger =
        new TLedger(TLEDGER_URL, mockHttpClient, mockSignatureVerifier, mockCertificateFetcher);
    testEndorsement = createTestEndorsement();
  }

  @Test
  public void post_sendsCorrectlyFormattedEntry() throws Exception {
    String dummyJsonResponse = "{\"name\": \"entries/HiMom\"}";
    when(mockHttpClient.execute(any(HttpPost.class), any(BasicHttpClientResponseHandler.class)))
        .thenReturn(dummyJsonResponse);
    when(mockCertificateFetcher.fetch()).thenReturn(ByteString.copyFromUtf8("cert"));

    tLedger.post(testEndorsement);

    verify(mockHttpClient)
        .execute(httpPostCaptor.capture(), any(BasicHttpClientResponseHandler.class));
    HttpPost capturedRequest = httpPostCaptor.getValue();
    String jsonBody = EntityUtils.toString(capturedRequest.getEntity());
    Entry.Builder entryBuilder = Entry.newBuilder();
    JsonFormat.parser().merge(jsonBody, entryBuilder);
    Entry sentEntry = entryBuilder.build();

    PublicEndorsement sentPublicEndorsement =
        PublicEndorsement.parseFrom(sentEntry.getRawEntry(), ExtensionRegistry.getEmptyRegistry());
    PublicEndorsement expectedPublicEndorsement =
        PublicEndorsement.newBuilder(EndorsementMapper.toProto(testEndorsement))
            .clearEndorsementSignatures()
            .clearTlogReceipt()
            .build();

    assertThat(sentPublicEndorsement).isEqualTo(expectedPublicEndorsement);
    assertThat(capturedRequest.getUri().toString()).isEqualTo(TLEDGER_URL + "/v1/entries");
  }

  @Test
  public void post_validEndorsement_returnsTLogReceipt() throws IOException {
    String receiptId = "entries/12345";
    ByteString certBytes = ByteString.copyFromUtf8("cert");
    ByteString signatureBytes = ByteString.copyFromUtf8("sig");
    ByteString rawEntryBytes = ByteString.copyFromUtf8("raw");
    Entry responseEntry =
        Entry.newBuilder()
            .setName(receiptId)
            .setSignature(signatureBytes)
            .setRawEntry(rawEntryBytes)
            .build();
    String validJsonResponse = JsonFormat.printer().print(responseEntry);

    when(mockHttpClient.execute(any(HttpPost.class), any(BasicHttpClientResponseHandler.class)))
        .thenReturn(validJsonResponse);
    when(mockCertificateFetcher.fetch()).thenReturn(certBytes);

    TLogReceipt receipt = tLedger.post(testEndorsement);

    assertThat(receipt).isNotNull();
    assertThat(receipt.logId()).isEqualTo(receiptId);
    verify(mockSignatureVerifier)
        .verify(
            eq(
                new Signature(
                    signatureBytes,
                    new VerificationMaterial(certBytes, VerificationMaterial.Format.X509_DER))),
            eq(rawEntryBytes));
  }

  @Test
  public void post_httpClientThrowsHttpResponseException_throwsTLogException() throws IOException {
    when(mockHttpClient.execute(any(HttpPost.class), any(BasicHttpClientResponseHandler.class)))
        .thenThrow(new HttpResponseException(500, "Internal Server Error"));

    TLogException exception =
        assertThrows(TLogException.class, () -> tLedger.post(testEndorsement));

    assertThat(exception).hasCauseThat().isInstanceOf(HttpResponseException.class);
  }

  @Test
  public void post_httpClientThrowsIOException_throwsTLogException() throws IOException {
    when(mockHttpClient.execute(any(HttpPost.class), any(BasicHttpClientResponseHandler.class)))
        .thenThrow(new IOException("Connection timed out"));

    TLogException exception =
        assertThrows(TLogException.class, () -> tLedger.post(testEndorsement));

    assertThat(exception).hasCauseThat().isInstanceOf(IOException.class);
    assertThat(exception).hasCauseThat().isNotInstanceOf(HttpResponseException.class);
  }

  @Test
  public void post_invalidJsonResponse_throwsTLogException() throws IOException {
    String invalidJsonResponse = "this is not valid json";
    when(mockHttpClient.execute(any(HttpPost.class), any(BasicHttpClientResponseHandler.class)))
        .thenReturn(invalidJsonResponse);

    TLogException exception =
        assertThrows(TLogException.class, () -> tLedger.post(testEndorsement));

    assertThat(exception).hasCauseThat().isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  public void post_invalidSignature_throwsTLogException() throws IOException {
    String dummyJsonResponse = "{\"name\": \"entries/HiMom\"}";
    when(mockHttpClient.execute(any(HttpPost.class), any(BasicHttpClientResponseHandler.class)))
        .thenReturn(dummyJsonResponse);
    when(mockCertificateFetcher.fetch()).thenReturn(ByteString.copyFromUtf8("cert"));
    doThrow(new InvalidSignatureException("Invalid signature"))
        .when(mockSignatureVerifier)
        .verify(any(), any());

    TLogException exception =
        assertThrows(TLogException.class, () -> tLedger.post(testEndorsement));

    assertThat(exception).hasMessageThat().contains("Failed to verify TLedger signature");
    assertThat(exception).hasCauseThat().isInstanceOf(InvalidSignatureException.class);
  }

  @Test
  public void isHealthy_returnsTrue_whenTLedgerReturns200() throws Exception {
    when(mockHttpClient.execute(any(HttpGet.class), any(BasicHttpClientResponseHandler.class)))
        .thenReturn("OK");

    boolean healthy = tLedger.isHealthy();

    assertThat(healthy).isTrue();
    verify(mockHttpClient)
        .execute(httpGetCaptor.capture(), any(BasicHttpClientResponseHandler.class));
    assertThat(httpGetCaptor.getValue().getUri().toString()).isEqualTo(TLEDGER_URL + "/healthz");
  }

  @Test
  public void isHealthy_returnsFalse_whenTLedgerReturnsNon2xx() throws Exception {
    when(mockHttpClient.execute(any(HttpGet.class), any(BasicHttpClientResponseHandler.class)))
        .thenThrow(new HttpResponseException(500, "Internal Server Error"));

    boolean healthy = tLedger.isHealthy();

    assertThat(healthy).isFalse();
  }

  @Test
  public void isHealthy_returnsFalse_whenTLedgerThrowsIOException() throws Exception {
    when(mockHttpClient.execute(any(HttpGet.class), any(BasicHttpClientResponseHandler.class)))
        .thenThrow(new IOException("Connection reset"));

    boolean healthy = tLedger.isHealthy();

    assertThat(healthy).isFalse();
  }

  private Endorsement createTestEndorsement() {
    Statement testStatement =
        new Statement(Statement.Format.JSON_INTOTO, ByteString.copyFromUtf8("statement"));

    Signature testSignature =
        new Signature(
            ByteString.copyFromUtf8("test-signature-bytes"),
            new VerificationMaterial(
                ByteString.copyFromUtf8("test-key-material"),
                VerificationMaterial.Format.X509_DER));

    return new Endorsement(
        "endorsement/123",
        testStatement,
        testSignature,
        Collections.emptyList(),
        new TLogReceipt(""));
  }
}
