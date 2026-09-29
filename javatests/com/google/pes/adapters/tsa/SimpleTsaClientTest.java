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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.pes.domain.model.TimeStampToken;
import com.google.pes.domain.ports.TsaException;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.cmp.PKIStatusInfo;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.tsp.TimeStampResp;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleTsaClientTest {
  private static final URI PRIMARY_TSA_URI = URI.create("https://tsa1.example.com/timestamp");
  private static final URI SECONDARY_TSA_URI = URI.create("https://tsa2.example.com/timestamp");
  private static final ByteString SAMPLE_DATA =
      ByteString.copyFromUtf8("test payload data to timestamp");

  private static TimeStampTokenGenerator tokenGenerator;

  private HttpClient mockHttpClient;
  private HttpResponse<InputStream> mockHttpResponse;
  private TsaUrlSelector mockUrlSelector;

  @BeforeClass
  public static void setUpClass() throws Exception {
    BouncyCastleProvider bcProvider = new BouncyCastleProvider();

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", bcProvider);
    kpg.initialize(2048);
    KeyPair tsaKeyPair = kpg.generateKeyPair();

    long now = System.currentTimeMillis();
    X500Name issuer = new X500Name("CN=Test TSA, O=Test Org, C=US");
    SubjectPublicKeyInfo spki =
        SubjectPublicKeyInfo.getInstance(tsaKeyPair.getPublic().getEncoded());
    X509v3CertificateBuilder certBuilder =
        new X509v3CertificateBuilder(
            issuer,
            BigInteger.ONE,
            new Date(now - 10000),
            new Date(now + 365L * 24 * 3600 * 1000),
            issuer,
            spki);

    certBuilder.addExtension(
        Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(bcProvider)
            .build(tsaKeyPair.getPrivate());
    X509Certificate tsaCert =
        new JcaX509CertificateConverter()
            .setProvider(bcProvider)
            .getCertificate(certBuilder.build(signer));

    DigestCalculator digestCalculator =
        new JcaDigestCalculatorProviderBuilder()
            .setProvider(bcProvider)
            .build()
            .get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));

    SignerInfoGenerator signerInfoGenerator =
        new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider(bcProvider).build())
            .build(signer, tsaCert);

    tokenGenerator =
        new TimeStampTokenGenerator(
            signerInfoGenerator, digestCalculator, new ASN1ObjectIdentifier("1.2.3.4.5"));
    tokenGenerator.addCertificates(
        new JcaCertStore(
            Collections.singletonList(new X509CertificateHolder(tsaCert.getEncoded()))));
  }

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    mockHttpClient = mock(HttpClient.class);
    mockHttpResponse = mock(HttpResponse.class);
    mockUrlSelector = mock(TsaUrlSelector.class);

    HttpHeaders defaultHeaders =
        HttpHeaders.of(
            Map.of("Content-Type", List.of(SimpleTsaClient.TIMESTAMP_REPLY_CONTENT_TYPE)),
            (k, v) -> true);
    when(mockHttpResponse.headers()).thenReturn(defaultHeaders);
    when(mockHttpResponse.uri()).thenReturn(PRIMARY_TSA_URI);
  }

  private SimpleTsaClient createTestClient() {
    return new SimpleTsaClient(mockUrlSelector, mockHttpClient);
  }

  private static byte[] generateValidTsaResponse(byte[] requestBytes) throws Exception {
    TimeStampRequest request = new TimeStampRequest(requestBytes);
    TimeStampResponseGenerator respGen =
        new TimeStampResponseGenerator(tokenGenerator, TSPAlgorithms.ALLOWED);
    TimeStampResponse resp = respGen.generate(request, BigInteger.valueOf(123456), new Date());
    return resp.getEncoded();
  }

  private static byte[] extractRequestBody(HttpRequest req) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    WritableByteChannel channel = Channels.newChannel(baos);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> errorRef = new AtomicReference<>();
    req.bodyPublisher()
        .ifPresentOrElse(
            publisher ->
                publisher.subscribe(
                    new Flow.Subscriber<ByteBuffer>() {
                      @Override
                      public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                      }

                      @Override
                      public void onNext(ByteBuffer item) {
                        try {
                          channel.write(item);
                        } catch (IOException e) {
                          errorRef.set(e);
                          latch.countDown();
                        }
                      }

                      @Override
                      public void onError(Throwable throwable) {
                        errorRef.set(throwable);
                        latch.countDown();
                      }

                      @Override
                      public void onComplete() {
                        latch.countDown();
                      }
                    }),
            latch::countDown);
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
    if (errorRef.get() != null) {
      throw new AssertionError(errorRef.get());
    }
    return baos.toByteArray();
  }

  @Test
  public void requestTimeStampToken_success() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              assertThat(req.uri()).isEqualTo(PRIMARY_TSA_URI);
              assertThat(req.timeout()).hasValue(SimpleTsaClient.DEFAULT_TIMEOUT);
              assertThat(req.headers().firstValue("Content-Type"))
                  .hasValue(SimpleTsaClient.TIMESTAMP_QUERY_CONTENT_TYPE);
              assertThat(req.headers().firstValue("Accept"))
                  .hasValue(SimpleTsaClient.TIMESTAMP_REPLY_CONTENT_TYPE);

              byte[] reqBytes = extractRequestBody(req);
              TimeStampRequest tsReq = new TimeStampRequest(reqBytes);
              assertThat(tsReq.getCertReq()).isTrue();
              assertThat(tsReq.getNonce()).isNotNull();

              byte[] respBytes = generateValidTsaResponse(reqBytes);

              doReturn(200).when(mockHttpResponse).statusCode();
              doReturn(new ByteArrayInputStream(respBytes)).when(mockHttpResponse).body();
              return mockHttpResponse;
            })
        .when(mockHttpClient)
        .send(any(HttpRequest.class), any());

    Optional<TimeStampToken> tokenOpt = client.requestTimeStampToken(SAMPLE_DATA);
    assertThat(tokenOpt).isPresent();
    TimeStampToken domainToken = tokenOpt.get();
    byte[] derBytes = domainToken.derBytes().toByteArray();
    assertThat(derBytes).isNotEmpty();

    org.bouncycastle.tsp.TimeStampToken token =
        new org.bouncycastle.tsp.TimeStampToken(new CMSSignedData(derBytes));
    assertThat(token.getTimeStampInfo().getSerialNumber()).isEqualTo(BigInteger.valueOf(123456));
    assertThat(token.getTimeStampInfo().getMessageImprintAlgOID())
        .isEqualTo(NISTObjectIdentifiers.id_sha384);
    MessageDigest md = MessageDigest.getInstance("SHA-384");
    assertThat(token.getTimeStampInfo().getMessageImprintDigest())
        .isEqualTo(md.digest(SAMPLE_DATA.toByteArray()));

    verify(mockUrlSelector).selectUrl();
  }

  @Test
  public void requestTimeStampToken_grantedWithMods_success() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              byte[] reqBytes = extractRequestBody(req);
              TimeStampResponse validResp =
                  new TimeStampResponse(generateValidTsaResponse(reqBytes));
              TimeStampResp grantedWithModsResp =
                  new TimeStampResp(
                      new PKIStatusInfo(
                          PKIStatus.getInstance(
                              new org.bouncycastle.asn1.ASN1Integer(PKIStatus.GRANTED_WITH_MODS))),
                      validResp.getTimeStampToken().toCMSSignedData().toASN1Structure());

              doReturn(200).when(mockHttpResponse).statusCode();
              doReturn(new ByteArrayInputStream(grantedWithModsResp.getEncoded()))
                  .when(mockHttpResponse)
                  .body();
              return mockHttpResponse;
            })
        .when(mockHttpClient)
        .send(any(HttpRequest.class), any());

    assertThat(client.requestTimeStampToken(SAMPLE_DATA)).isPresent();
  }

  @Test
  public void requestTimeStampToken_selectorCalledEachTime() throws Exception {
    when(mockUrlSelector.selectUrl())
        .thenReturn(Optional.of(PRIMARY_TSA_URI), Optional.of(SECONDARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              byte[] reqBytes = extractRequestBody(req);
              byte[] respBytes = generateValidTsaResponse(reqBytes);

              doReturn(200).when(mockHttpResponse).statusCode();
              doReturn(new ByteArrayInputStream(respBytes)).when(mockHttpResponse).body();
              return mockHttpResponse;
            })
        .when(mockHttpClient)
        .send(any(HttpRequest.class), any());

    client.requestTimeStampToken(SAMPLE_DATA);
    client.requestTimeStampToken(SAMPLE_DATA);

    verify(mockUrlSelector, times(2)).selectUrl();
  }

  @Test
  public void requestTimeStampToken_rejectedStatusWithFailInfo_throwsTsaException()
      throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    TimeStampResponseGenerator respGen =
        new TimeStampResponseGenerator(tokenGenerator, TSPAlgorithms.ALLOWED);
    TimeStampResponse failResp =
        respGen.generateFailResponse(
            PKIStatus.REJECTION, PKIFailureInfo.badAlg, "Unsupported hash algorithm");
    byte[] respBytes = failResp.getEncoded();

    doReturn(200).when(mockHttpResponse).statusCode();
    doReturn(new ByteArrayInputStream(respBytes)).when(mockHttpResponse).body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    TsaException ex =
        assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
    assertThat(ex)
        .hasMessageThat()
        .contains(
            "TSA at https://tsa1.example.com/timestamp rejected timestamp request. Status: 2,"
                + " statusString: Unsupported hash algorithm, failInfo: "
                + PKIFailureInfo.badAlg);
  }

  @Test
  public void requestTimeStampToken_rejectedStatusWithTokenAndNoFailInfo_throwsTsaException()
      throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              byte[] reqBytes = extractRequestBody(req);
              TimeStampResponse validResp =
                  new TimeStampResponse(generateValidTsaResponse(reqBytes));
              TimeStampResp rejectedWithTokenResp =
                  new TimeStampResp(
                      new PKIStatusInfo(PKIStatus.rejection),
                      validResp.getTimeStampToken().toCMSSignedData().toASN1Structure());

              doReturn(200).when(mockHttpResponse).statusCode();
              doReturn(new ByteArrayInputStream(rejectedWithTokenResp.getEncoded()))
                  .when(mockHttpResponse)
                  .body();
              return mockHttpResponse;
            })
        .when(mockHttpClient)
        .send(any(HttpRequest.class), any());

    TsaException ex =
        assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
    assertThat(ex)
        .hasMessageThat()
        .contains(
            "TSA at https://tsa1.example.com/timestamp rejected timestamp request. Status: 2,"
                + " statusString: null, failInfo: <none>");
  }

  @Test
  public void requestTimeStampToken_grantedStatusWithoutToken_throwsTsaException()
      throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    TimeStampResp grantedWithoutToken =
        new TimeStampResp(new PKIStatusInfo(PKIStatus.granted), null);
    byte[] respBytes = grantedWithoutToken.getEncoded();

    doReturn(200).when(mockHttpResponse).statusCode();
    doReturn(new ByteArrayInputStream(respBytes)).when(mockHttpResponse).body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    TsaException ex =
        assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
    assertThat(ex).hasMessageThat().contains("did not contain a timestamp token. Status: 0");
  }

  @Test
  public void requestTimeStampToken_emptyUrlFromSelector_returnsEmptyOptional() {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.empty());
    SimpleTsaClient client = createTestClient();

    assertThat(client.requestTimeStampToken(SAMPLE_DATA)).isEmpty();
  }

  @Test
  public void requestTimeStampToken_nullUrlFromSelector_throwsTsaException() {
    when(mockUrlSelector.selectUrl()).thenReturn(null);
    SimpleTsaClient client = createTestClient();

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void
      requestTimeStampToken_httpErrorWithControlChars_sanitizesMessageAndThrowsTsaException()
          throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doReturn(500).when(mockHttpResponse).statusCode();
    doReturn(new ByteArrayInputStream("Internal\r\nServer\tError".getBytes(StandardCharsets.UTF_8)))
        .when(mockHttpResponse)
        .body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    TsaException ex =
        assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
    assertThat(ex).hasMessageThat().contains("Internal  Server Error");
  }

  @Test
  public void
      requestTimeStampToken_httpErrorWithUnicodeControlChars_sanitizesMessageAndThrowsTsaException()
          throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doReturn(500).when(mockHttpResponse).statusCode();
    doReturn(
            new ByteArrayInputStream(
                "Bad\u202EOverride\u2028LineSep\u0085NextLine".getBytes(StandardCharsets.UTF_8)))
        .when(mockHttpResponse)
        .body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    TsaException ex =
        assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
    assertThat(ex).hasMessageThat().contains("Bad Override LineSep NextLine");
  }

  @Test
  public void requestTimeStampToken_httpErrorWithEmptyBody_throwsTsaException() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doReturn(503).when(mockHttpResponse).statusCode();
    doReturn(new ByteArrayInputStream(new byte[0])).when(mockHttpResponse).body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_httpErrorBodyReadFails_throwsTsaException() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    InputStream failingBody =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Stream closed prematurely");
          }
        };

    doReturn(502).when(mockHttpResponse).statusCode();
    doReturn(failingBody).when(mockHttpResponse).body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_networkFailure_throwsTsaException() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doThrow(new IOException("Connection refused"))
        .when(mockHttpClient)
        .send(any(HttpRequest.class), any());

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_interrupted_restoresInterruptAndThrowsTsaException()
      throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doThrow(new InterruptedException("Thread interrupted"))
        .when(mockHttpClient)
        .send(any(HttpRequest.class), any());

    try {
      TsaException ex =
          assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
      assertThat(ex).hasCauseThat().isInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void requestTimeStampToken_caseInsensitiveContentType_success() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    HttpHeaders upperCaseHeaders =
        HttpHeaders.of(
            Map.of("Content-Type", List.of("APPLICATION/TIMESTAMP-REPLY")), (k, v) -> true);
    doReturn(upperCaseHeaders).when(mockHttpResponse).headers();

    doAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              byte[] reqBytes = extractRequestBody(req);
              byte[] respBytes = generateValidTsaResponse(reqBytes);

              doReturn(200).when(mockHttpResponse).statusCode();
              doReturn(new ByteArrayInputStream(respBytes)).when(mockHttpResponse).body();
              return mockHttpResponse;
            })
        .when(mockHttpClient)
        .send(any(HttpRequest.class), any());

    assertThat(client.requestTimeStampToken(SAMPLE_DATA)).isPresent();
  }

  @Test
  public void requestTimeStampToken_unexpectedContentType_throwsTsaException() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    HttpHeaders htmlHeaders =
        HttpHeaders.of(Map.of("Content-Type", List.of("text/html; charset=utf-8")), (k, v) -> true);
    doReturn(200).when(mockHttpResponse).statusCode();
    doReturn(htmlHeaders).when(mockHttpResponse).headers();
    doReturn(new ByteArrayInputStream("<html>Login</html>".getBytes(StandardCharsets.UTF_8)))
        .when(mockHttpResponse)
        .body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_missingContentTypeHeader_throwsTsaException() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    HttpHeaders emptyHeaders = HttpHeaders.of(Map.of(), (k, v) -> true);
    doReturn(200).when(mockHttpResponse).statusCode();
    doReturn(emptyHeaders).when(mockHttpResponse).headers();
    doReturn(new ByteArrayInputStream(new byte[] {1, 2, 3})).when(mockHttpResponse).body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_multiChunkByteString_success() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    ByteString multiChunkData =
        ByteString.copyFromUtf8("a".repeat(200)).concat(ByteString.copyFromUtf8("b".repeat(200)));
    assertThat(multiChunkData.asReadOnlyByteBufferList().size()).isGreaterThan(1);

    doAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              byte[] reqBytes = extractRequestBody(req);
              byte[] respBytes = generateValidTsaResponse(reqBytes);

              doReturn(200).when(mockHttpResponse).statusCode();
              doReturn(new ByteArrayInputStream(respBytes)).when(mockHttpResponse).body();
              return mockHttpResponse;
            })
        .when(mockHttpClient)
        .send(any(HttpRequest.class), any());

    Optional<TimeStampToken> tokenOpt = client.requestTimeStampToken(multiChunkData);
    assertThat(tokenOpt).isPresent();

    org.bouncycastle.tsp.TimeStampToken token =
        new org.bouncycastle.tsp.TimeStampToken(
            new CMSSignedData(tokenOpt.get().derBytes().toByteArray()));
    MessageDigest md = MessageDigest.getInstance("SHA-384");
    assertThat(token.getTimeStampInfo().getMessageImprintDigest())
        .isEqualTo(md.digest(multiChunkData.toByteArray()));
  }

  @Test
  public void requestTimeStampToken_contentTypeWithParameters_success() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    HttpHeaders headersWithParams =
        HttpHeaders.of(
            Map.of("Content-Type", List.of("application/timestamp-reply; charset=binary")),
            (k, v) -> true);
    doReturn(headersWithParams).when(mockHttpResponse).headers();

    doAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              byte[] reqBytes = extractRequestBody(req);
              byte[] respBytes = generateValidTsaResponse(reqBytes);

              doReturn(200).when(mockHttpResponse).statusCode();
              doReturn(new ByteArrayInputStream(respBytes)).when(mockHttpResponse).body();
              return mockHttpResponse;
            })
        .when(mockHttpClient)
        .send(any(HttpRequest.class), any());

    assertThat(client.requestTimeStampToken(SAMPLE_DATA)).isPresent();
  }

  @Test
  public void requestTimeStampToken_httpUri_success() throws Exception {
    URI httpUri = URI.create("http://tsa1.example.com/timestamp");
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(httpUri));
    when(mockHttpResponse.uri()).thenReturn(httpUri);
    SimpleTsaClient client = createTestClient();

    doAnswer(
            invocation -> {
              HttpRequest req = invocation.getArgument(0);
              assertThat(req.uri()).isEqualTo(httpUri);
              byte[] reqBytes = extractRequestBody(req);
              byte[] respBytes = generateValidTsaResponse(reqBytes);

              doReturn(200).when(mockHttpResponse).statusCode();
              doReturn(new ByteArrayInputStream(respBytes)).when(mockHttpResponse).body();
              return mockHttpResponse;
            })
        .when(mockHttpClient)
        .send(any(HttpRequest.class), any());

    assertThat(client.requestTimeStampToken(SAMPLE_DATA)).isPresent();
  }

  @Test
  public void requestTimeStampToken_redirectedToNonHttpOrHttpsUri_throwsTsaException()
      throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    when(mockHttpResponse.uri()).thenReturn(URI.create("ftp://insecure.example.com/timestamp"));
    doReturn(200).when(mockHttpResponse).statusCode();
    doReturn(new ByteArrayInputStream(new byte[] {1, 2, 3})).when(mockHttpResponse).body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_maxResponseBytesBoundary() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    // Exact MAX_RESPONSE_BYTES should pass size check and fail at ASN.1 parsing
    byte[] exactMax = new byte[SimpleTsaClient.MAX_RESPONSE_BYTES];
    doReturn(200).when(mockHttpResponse).statusCode();
    doReturn(new ByteArrayInputStream(exactMax)).when(mockHttpResponse).body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));

    // MAX_RESPONSE_BYTES + 1 should fail size check
    byte[] oversized = new byte[SimpleTsaClient.MAX_RESPONSE_BYTES + 1];
    doReturn(new ByteArrayInputStream(oversized)).when(mockHttpResponse).body();

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_nonHttpOrHttpsUri_throwsTsaException() {
    when(mockUrlSelector.selectUrl())
        .thenReturn(Optional.of(URI.create("ftp://tsa1.example.com/timestamp")));
    SimpleTsaClient client = createTestClient();

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_missingHostInUri_throwsTsaException() {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(URI.create("https:///timestamp")));
    SimpleTsaClient client = createTestClient();

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_malformedHttp200Payload_throwsTsaException() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doReturn(200).when(mockHttpResponse).statusCode();
    doReturn(
            new ByteArrayInputStream(
                "<html>502 Bad Gateway</html>".getBytes(StandardCharsets.UTF_8)))
        .when(mockHttpResponse)
        .body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_emptyResponseBody_throwsTsaException() throws Exception {
    when(mockUrlSelector.selectUrl()).thenReturn(Optional.of(PRIMARY_TSA_URI));
    SimpleTsaClient client = createTestClient();

    doReturn(200).when(mockHttpResponse).statusCode();
    doReturn(new ByteArrayInputStream(new byte[0])).when(mockHttpResponse).body();
    doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

    assertThrows(TsaException.class, () -> client.requestTimeStampToken(SAMPLE_DATA));
  }

  @Test
  public void requestTimeStampToken_nullArguments_throwsException() {
    SimpleTsaClient client = createTestClient();
    assertThrows(NullPointerException.class, () -> client.requestTimeStampToken(null));
  }

  @Test
  public void requestTimeStampToken_emptyData_throwsException() {
    SimpleTsaClient client = createTestClient();
    assertThrows(
        IllegalArgumentException.class, () -> client.requestTimeStampToken(ByteString.EMPTY));
  }

  @Test
  public void constructor_nullArguments_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new SimpleTsaClient(null, mockHttpClient));
    assertThrows(NullPointerException.class, () -> new SimpleTsaClient(mockUrlSelector, null));
  }
}
