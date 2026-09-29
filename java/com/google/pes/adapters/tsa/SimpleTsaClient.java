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

package com.google.pes.adapters.tsa;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.ByteStreams;
import com.google.pes.domain.model.TimeStampToken;
import com.google.pes.domain.ports.TsaClient;
import com.google.pes.domain.ports.TsaException;
import com.google.protobuf.ByteString;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;

/**
 * Implementation of {@link TsaClient} that requests RFC 3161 timestamp tokens from a TSA endpoint
 * selected via {@link TsaUrlSelector}.
 */
@Singleton
public class SimpleTsaClient implements TsaClient {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Pattern CONTROL_CHARS_PATTERN =
      Pattern.compile("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final int NONCE_BIT_LENGTH = 128;
  private static final int MAX_ERROR_BODY_SNIPPET_BYTES = 512;
  @VisibleForTesting static final int MAX_RESPONSE_BYTES = 64 * 1024;

  @VisibleForTesting
  static final String TIMESTAMP_QUERY_CONTENT_TYPE = "application/timestamp-query";

  @VisibleForTesting
  static final String TIMESTAMP_REPLY_CONTENT_TYPE = "application/timestamp-reply";

  @VisibleForTesting static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

  private final TsaUrlSelector urlSelector;
  private final HttpClient httpClient;

  @Inject
  public SimpleTsaClient(TsaUrlSelector urlSelector, HttpClient httpClient) {
    this.urlSelector = Objects.requireNonNull(urlSelector, "urlSelector cannot be null");
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
  }

  @Override
  public Optional<TimeStampToken> requestTimeStampToken(ByteString data) {
    Objects.requireNonNull(data, "data cannot be null");
    if (data.isEmpty()) {
      throw new IllegalArgumentException("data cannot be empty");
    }

    Optional<URI> tsaUriOpt = urlSelector.selectUrl();
    if (tsaUriOpt == null) {
      throw new TsaException("TsaUrlSelector returned null");
    }
    if (tsaUriOpt.isEmpty()) {
      return Optional.empty();
    }
    URI tsaUri = tsaUriOpt.get();
    if ((!"https".equalsIgnoreCase(tsaUri.getScheme())
            && !"http".equalsIgnoreCase(tsaUri.getScheme()))
        || tsaUri.getHost() == null
        || tsaUri.getHost().isBlank()) {
      throw new TsaException("TSA URI must use HTTP or HTTPS scheme and specify a host: " + tsaUri);
    }

    try {
      MessageDigest md = MessageDigest.getInstance("SHA-384");
      for (ByteBuffer buffer : data.asReadOnlyByteBufferList()) {
        md.update(buffer);
      }
      byte[] digest = md.digest();
      return Optional.of(requestTimeStampToken(tsaUri, NISTObjectIdentifiers.id_sha384, digest));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-384 algorithm not available", e);
    } catch (TsaException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TsaException("Interrupted while obtaining timestamp token from TSA: " + tsaUri, e);
    } catch (IOException e) {
      throw new TsaException("Failed to obtain timestamp token from TSA: " + tsaUri, e);
    }
  }

  /**
   * Sends an RFC 3161 TimeStampRequest to the given TSA endpoint and extracts the timestamp token
   * from the response.
   *
   * @param tsaUri the URI of the TSA endpoint
   * @param hashAlgorithmOid the algorithm OID of the digest
   * @param digest the message digest
   * @return the extracted domain {@link TimeStampToken}
   */
  private TimeStampToken requestTimeStampToken(
      URI tsaUri, ASN1ObjectIdentifier hashAlgorithmOid, byte[] digest)
      throws IOException, InterruptedException {
    TimeStampRequest request = createTimestampRequest(hashAlgorithmOid, digest);
    byte[] responseBytes = sendHttpRequest(tsaUri, request.getEncoded());
    return parseAndExtractToken(responseBytes, tsaUri);
  }

  private static TimeStampRequest createTimestampRequest(
      ASN1ObjectIdentifier hashAlgorithmOid, byte[] digest) {
    TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
    reqGen.setCertReq(true);

    BigInteger nonce = new BigInteger(NONCE_BIT_LENGTH, SECURE_RANDOM);
    return reqGen.generate(hashAlgorithmOid, digest, nonce);
  }

  private byte[] sendHttpRequest(URI tsaUri, byte[] requestBytes)
      throws IOException, InterruptedException {
    logger.atFine().log("Sending RFC 3161 timestamp request to: %s", tsaUri);

    HttpRequest httpRequest;
    try {
      httpRequest =
          HttpRequest.newBuilder()
              .uri(tsaUri)
              .timeout(DEFAULT_TIMEOUT)
              .header("Content-Type", TIMESTAMP_QUERY_CONTENT_TYPE)
              .header("Accept", TIMESTAMP_REPLY_CONTENT_TYPE)
              .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes))
              .build();
    } catch (IllegalArgumentException e) {
      throw new TsaException("Invalid TSA URI: " + tsaUri, e);
    }

    HttpResponse<InputStream> httpResponse =
        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

    byte[] responseBytes;
    try (InputStream body = httpResponse.body()) {
      if (httpResponse.uri() != null
          && !"https".equalsIgnoreCase(httpResponse.uri().getScheme())
          && !"http".equalsIgnoreCase(httpResponse.uri().getScheme())) {
        throw new TsaException(
            "TSA response redirected to non-HTTP/HTTPS URI: " + httpResponse.uri());
      }

      if (httpResponse.statusCode() != 200) {
        String bodySnippet;
        try {
          byte[] errorBytes = readBytes(body, MAX_ERROR_BODY_SNIPPET_BYTES);
          if (errorBytes.length == 0) {
            bodySnippet = "<empty body>";
          } else {
            bodySnippet =
                CONTROL_CHARS_PATTERN
                    .matcher(new String(errorBytes, StandardCharsets.UTF_8))
                    .replaceAll(" ");
          }
        } catch (IOException e) {
          bodySnippet = "<failed to read error body: " + e.getMessage() + ">";
        }
        throw new TsaException(
            String.format(
                "TSA server at %s returned HTTP %d: %s",
                tsaUri, httpResponse.statusCode(), bodySnippet));
      }

      Optional<String> contentType = httpResponse.headers().firstValue("Content-Type");
      if (contentType.isEmpty() || !isTimestampReplyContentType(contentType.get())) {
        throw new TsaException(
            String.format(
                "TSA server at %s returned invalid Content-Type: %s (expected %s)",
                tsaUri, contentType.orElse("<none>"), TIMESTAMP_REPLY_CONTENT_TYPE));
      }

      responseBytes = readBoundedBody(body, MAX_RESPONSE_BYTES, tsaUri);
    }

    if (responseBytes.length == 0) {
      throw new TsaException("Received empty response body from TSA: " + tsaUri);
    }

    return responseBytes;
  }

  private static TimeStampToken parseAndExtractToken(byte[] responseBytes, URI tsaUri)
      throws IOException {
    TimeStampResponse tsResponse;
    try {
      tsResponse = new TimeStampResponse(responseBytes);
    } catch (IOException | TSPException | RuntimeException e) {
      // Bouncy Castle's ASN.1 parser can throw unchecked exceptions (e.g.,
      // IllegalArgumentException,
      // ClassCastException) when parsing malformed or truncated DER bytes.
      throw new TsaException("Malformed RFC 3161 response from TSA: " + tsaUri, e);
    }

    int status = tsResponse.getStatus();
    if (status != PKIStatus.GRANTED && status != PKIStatus.GRANTED_WITH_MODS) {
      PKIFailureInfo failInfo = tsResponse.getFailInfo();
      throw new TsaException(
          String.format(
              "TSA at %s rejected timestamp request. Status: %d, statusString: %s, failInfo: %s",
              tsaUri,
              status,
              tsResponse.getStatusString(),
              failInfo != null ? failInfo.intValue() : "<none>"));
    }

    org.bouncycastle.tsp.TimeStampToken token = tsResponse.getTimeStampToken();
    if (token == null) {
      throw new TsaException(
          String.format(
              "TSA response from %s did not contain a timestamp token. Status: %d, info: %s",
              tsaUri, status, tsResponse.getStatusString()));
    }

    // TODO: Validation of TimeStampResponse against TimeStampRequest and CMS signature
    // verification against trusted root certificates.
    TimeStampToken domainToken = new TimeStampToken(ByteString.copyFrom(token.getEncoded()));

    logger.atFine().log(
        "Successfully retrieved RFC 3161 timestamp token from %s (genTime: %s)",
        tsaUri, token.getTimeStampInfo().getGenTime());

    return domainToken;
  }

  private static boolean isTimestampReplyContentType(String headerValue) {
    int semicolonIdx = headerValue.indexOf(';');
    String baseContentType =
        (semicolonIdx >= 0 ? headerValue.substring(0, semicolonIdx) : headerValue).trim();
    return TIMESTAMP_REPLY_CONTENT_TYPE.equalsIgnoreCase(baseContentType);
  }

  private static byte[] readBytes(InputStream in, int maxBytes) throws IOException {
    if (in == null) {
      return new byte[0];
    }
    return ByteStreams.toByteArray(ByteStreams.limit(in, maxBytes));
  }

  private static byte[] readBoundedBody(InputStream in, int maxBytes, URI tsaUri)
      throws IOException {
    byte[] bytes = readBytes(in, maxBytes + 1);
    if (bytes.length > maxBytes) {
      throw new TsaException(
          String.format(
              "TSA response from %s exceeded maximum allowed size of %d bytes", tsaUri, maxBytes));
    }
    return bytes;
  }
}
