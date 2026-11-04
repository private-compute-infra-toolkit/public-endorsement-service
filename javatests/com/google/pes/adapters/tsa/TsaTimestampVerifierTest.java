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

import com.google.common.collect.ImmutableSet;
import com.google.pes.domain.ports.TsaException;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TsaTimestampVerifierTest {
  private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();
  private static final byte[] SAMPLE_DATA =
      "test payload data to timestamp".getBytes(StandardCharsets.UTF_8);
  private static final ByteString SAMPLE_BYTE_STRING = ByteString.copyFrom(SAMPLE_DATA);

  private static KeyPair tsaKeyPair;
  private static X509Certificate tsaCert;
  private static ImmutableSet<TrustAnchor> testTrustAnchors;
  private static TimeStampTokenGenerator tokenGenerator;

  @BeforeClass
  public static void setUpClass() throws Exception {
    tsaKeyPair = generateRsaKeyPair();
    long now = System.currentTimeMillis();
    X500Name issuer = new X500Name("CN=Test TSA, O=Test Org, C=US");
    tsaCert =
        createCertificate(
            issuer,
            BigInteger.ONE,
            new Date(now - 10000),
            new Date(now + 365L * 24 * 3600 * 1000),
            issuer,
            tsaKeyPair.getPublic(),
            tsaKeyPair.getPrivate(),
            null,
            true);
    testTrustAnchors = ImmutableSet.of(new TrustAnchor(tsaCert, null));
    tokenGenerator = createTokenGenerator(tsaKeyPair.getPrivate(), tsaCert, List.of(tsaCert), null);
  }

  private static KeyPair generateRsaKeyPair() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BC_PROVIDER);
    kpg.initialize(2048);
    return kpg.generateKeyPair();
  }

  private static X509Certificate createCertificate(
      X500Name issuer,
      BigInteger serial,
      Date notBefore,
      Date notAfter,
      X500Name subject,
      PublicKey subjectPublicKey,
      PrivateKey issuerPrivateKey,
      Boolean basicConstraintsCa,
      Boolean timeStampingEkuCritical)
      throws Exception {
    SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(subjectPublicKey.getEncoded());
    X509v3CertificateBuilder certBuilder =
        new X509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, spki);
    if (basicConstraintsCa != null) {
      certBuilder.addExtension(
          Extension.basicConstraints, true, new BasicConstraints(basicConstraintsCa));
    }
    if (timeStampingEkuCritical != null) {
      certBuilder.addExtension(
          Extension.extendedKeyUsage,
          timeStampingEkuCritical,
          new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
    }
    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BC_PROVIDER)
            .build(issuerPrivateKey);
    return new JcaX509CertificateConverter()
        .setProvider(BC_PROVIDER)
        .getCertificate(certBuilder.build(signer));
  }

  private static TimeStampTokenGenerator createTokenGenerator(
      PrivateKey signerPrivateKey,
      X509Certificate signerCertForInfo,
      List<X509Certificate> embeddedCerts,
      Date signingTime)
      throws Exception {
    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BC_PROVIDER)
            .build(signerPrivateKey);
    DigestCalculator digestCalculator =
        new JcaDigestCalculatorProviderBuilder()
            .setProvider(BC_PROVIDER)
            .build()
            .get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));

    // Bouncy Castle's TimeStampTokenGenerator constructor validates that the associated
    // certificate has a critical id-kp-timeStamping EKU before computing its ESSCertIDv2 hash.
    // Override getExtension during generator construction so we can generate test tokens whose
    // ESSCertIDv2 hash matches certificates with missing or non-critical EKU extensions.
    X509CertificateHolder certHolderForGen =
        new X509CertificateHolder(signerCertForInfo.getEncoded()) {
          @Override
          public Extension getExtension(ASN1ObjectIdentifier oid) {
            if (Extension.extendedKeyUsage.equals(oid)) {
              try {
                return new Extension(
                    Extension.extendedKeyUsage,
                    true,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping).getEncoded());
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
            return super.getExtension(oid);
          }
        };

    JcaSignerInfoGeneratorBuilder signerInfoBuilder =
        new JcaSignerInfoGeneratorBuilder(
            new JcaDigestCalculatorProviderBuilder().setProvider(BC_PROVIDER).build());
    if (signingTime != null) {
      Attribute signingTimeAttr =
          new Attribute(CMSAttributes.signingTime, new DERSet(new Time(signingTime)));
      signerInfoBuilder.setSignedAttributeGenerator(
          new DefaultSignedAttributeTableGenerator(new AttributeTable(signingTimeAttr)));
    }
    SignerInfoGenerator signerInfoGenerator = signerInfoBuilder.build(signer, certHolderForGen);

    TimeStampTokenGenerator gen =
        new TimeStampTokenGenerator(
            signerInfoGenerator, digestCalculator, new ASN1ObjectIdentifier("1.2.3.4.5"));
    if (!embeddedCerts.isEmpty()) {
      List<X509CertificateHolder> holders = new ArrayList<>();
      for (X509Certificate cert : embeddedCerts) {
        holders.add(new X509CertificateHolder(cert.getEncoded()));
      }
      gen.addCertificates(new JcaCertStore(holders));
    }
    return gen;
  }

  private static TimeStampRequest createSampleRequest(byte[] data, BigInteger nonce)
      throws Exception {
    TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
    reqGen.setCertReq(true);
    byte[] digest = MessageDigest.getInstance("SHA-384").digest(data);
    return reqGen.generate(NISTObjectIdentifiers.id_sha384, digest, nonce);
  }

  private static TimeStampResponse createResponse(
      TimeStampRequest request, TimeStampTokenGenerator gen, Date genTime) throws Exception {
    TimeStampResponseGenerator respGen = new TimeStampResponseGenerator(gen, TSPAlgorithms.ALLOWED);
    return respGen.generate(request, BigInteger.valueOf(123456), genTime);
  }

  @Test
  public void extractTokenAndVerify_validResponseAndRequest_succeeds() throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGenerator, new Date());

    com.google.pes.domain.model.TimeStampToken domainToken =
        verifier.extractTokenAndVerify(response, request);

    byte[] derBytes = domainToken.derBytes().toByteArray();
    assertThat(derBytes).isNotEmpty();
    TimeStampToken token = new TimeStampToken(new CMSSignedData(derBytes));
    assertThat(token.getTimeStampInfo().getSerialNumber()).isEqualTo(BigInteger.valueOf(123456));
    assertThat(token.getTimeStampInfo().getMessageImprintAlgOID())
        .isEqualTo(NISTObjectIdentifiers.id_sha384);
    assertThat(token.getTimeStampInfo().getMessageImprintDigest())
        .isEqualTo(MessageDigest.getInstance("SHA-384").digest(SAMPLE_DATA));
  }

  @Test
  public void extractTokenAndVerify_certExpiredNowButValidAtSigningTime_succeeds()
      throws Exception {
    Instant fixedNow = Instant.now();
    // Timestamp was created 5 minutes ago (within 15-minute MAX_CLOCK_SKEW)
    Instant genTimeInstant = fixedNow.minus(Duration.ofMinutes(5));
    // Both Root CA and End-Entity TSA cert were valid from 10h ago to 2m ago:
    // -> Expired at verification time (fixedNow), but VALID at signing time (genTimeInstant).
    Date notBefore = Date.from(fixedNow.minus(Duration.ofHours(10)));
    Date notAfter = Date.from(fixedNow.minus(Duration.ofMinutes(2)));

    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Root CA Valid At Signing, O=Test Org, C=US");
    X509Certificate rootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            notBefore,
            notAfter,
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    KeyPair endEntityKeyPair = generateRsaKeyPair();
    X500Name endEntityName = new X500Name("CN=TSA Valid At Signing, O=Test Org, C=US");
    X509Certificate endEntityCert =
        createCertificate(
            rootName,
            BigInteger.valueOf(2),
            notBefore,
            notAfter,
            endEntityName,
            endEntityKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            false,
            true);

    Date genTimeDate = Date.from(genTimeInstant);
    TimeStampTokenGenerator tokenGen =
        createTokenGenerator(
            endEntityKeyPair.getPrivate(), endEntityCert, List.of(endEntityCert), genTimeDate);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.fixed(fixedNow), ImmutableSet.of(new TrustAnchor(rootCert, null)));
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGen, genTimeDate);

    com.google.pes.domain.model.TimeStampToken domainToken =
        verifier.extractTokenAndVerify(response, request);
    assertThat(domainToken.derBytes()).isNotEmpty();
  }

  @Test
  public void extractTokenAndVerify_timestampGenTimeExceedsClockSkew_throwsTsaException()
      throws Exception {
    Instant fixedNow = Instant.now();
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.fixed(fixedNow), testTrustAnchors);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    Date skewedDate =
        Date.from(fixedNow.minus(TsaTimestampVerifierImpl.MAX_CLOCK_SKEW.plusMinutes(5)));
    TimeStampResponse response = createResponse(request, tokenGenerator, skewedDate);

    TsaException ex =
        assertThrows(TsaException.class, () -> verifier.extractTokenAndVerify(response, request));
    assertThat(ex).hasMessageThat().contains("exceeds maximum allowed clock skew");
  }

  @Test
  public void extractTokenAndVerify_certNotRootedInTrustedRoot_throwsTsaException()
      throws Exception {
    KeyPair otherRootKeyPair = generateRsaKeyPair();
    long now = System.currentTimeMillis();
    X500Name otherRootName = new X500Name("CN=Other Trusted Root CA, O=Test Org, C=US");
    X509Certificate otherRootCert =
        createCertificate(
            otherRootName,
            BigInteger.TEN,
            new Date(now - 10000),
            new Date(now + 365L * 24 * 3600 * 1000),
            otherRootName,
            otherRootKeyPair.getPublic(),
            otherRootKeyPair.getPrivate(),
            true,
            null);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(otherRootCert, null)));
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGenerator, new Date());

    TsaException ex =
        assertThrows(TsaException.class, () -> verifier.extractTokenAndVerify(response, request));
    assertThat(ex).hasMessageThat().contains("Failed to validate TSA certificate chain");
  }

  @Test
  public void extractTokenAndVerify_rootCertInTrustListButExpiredAtSigningTime_throwsTsaException()
      throws Exception {
    long now = System.currentTimeMillis();

    // Root CA in trust list expired 1 hour prior to genTime (moment of signing)
    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Expired Root CA, O=Test Org, C=US");
    X509Certificate expiredRootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            new Date(now - 7200_000L),
            new Date(now - 3600_000L),
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    KeyPair endEntityKeyPair = generateRsaKeyPair();
    X500Name endEntityName = new X500Name("CN=End Entity TSA, O=Test Org, C=US");
    X509Certificate endEntityCert =
        createCertificate(
            rootName,
            BigInteger.valueOf(2),
            new Date(now - 10000),
            new Date(now + 365L * 24 * 3600 * 1000),
            endEntityName,
            endEntityKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            false,
            true);

    TimeStampTokenGenerator tokenGen =
        createTokenGenerator(
            endEntityKeyPair.getPrivate(), endEntityCert, List.of(endEntityCert), null);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(expiredRootCert, null)));
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGen, new Date(now));

    TsaException ex =
        assertThrows(TsaException.class, () -> verifier.extractTokenAndVerify(response, request));
    assertThat(ex).hasMessageThat().contains("Failed to validate TSA certificate chain");
  }

  @Test
  public void extractTokenAndVerify_signerCertExpiredAtSigningTime_throwsTsaException()
      throws Exception {
    long now = System.currentTimeMillis();

    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Valid Root CA, O=Test Org, C=US");
    X509Certificate validRootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            new Date(now - 7200_000L),
            new Date(now + 365L * 24 * 3600 * 1000),
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    // End-entity TSA cert expired 1 hour prior to genTime (moment of signing)
    KeyPair endEntityKeyPair = generateRsaKeyPair();
    X500Name endEntityName = new X500Name("CN=Expired End Entity TSA, O=Test Org, C=US");
    X509Certificate expiredEndEntityCert =
        createCertificate(
            rootName,
            BigInteger.valueOf(2),
            new Date(now - 7200_000L),
            new Date(now - 3600_000L),
            endEntityName,
            endEntityKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            false,
            true);

    TimeStampTokenGenerator tokenGen =
        createTokenGenerator(
            endEntityKeyPair.getPrivate(),
            expiredEndEntityCert,
            List.of(expiredEndEntityCert),
            null);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(validRootCert, null)));
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGen, new Date(now));

    assertThrows(TsaException.class, () -> verifier.extractTokenAndVerify(response, request));
  }

  @Test
  public void extractTokenAndVerify_invalidNonceInResponse_throwsTsaException() throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequest expectedRequest = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(1111));
    TimeStampRequest mismatchedNonceReq =
        createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(2222));
    TimeStampResponse response = createResponse(mismatchedNonceReq, tokenGenerator, new Date());

    assertThrows(
        TsaException.class, () -> verifier.extractTokenAndVerify(response, expectedRequest));
  }

  @Test
  public void extractTokenAndVerify_noNonceInRequest_throwsTsaException() throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequest requestWithoutNonce = createSampleRequest(SAMPLE_DATA, null);
    TimeStampResponse response = createResponse(requestWithoutNonce, tokenGenerator, new Date());

    TsaException ex =
        assertThrows(
            TsaException.class,
            () -> verifier.extractTokenAndVerify(response, requestWithoutNonce));
    assertThat(ex).hasMessageThat().contains("nonce");
  }

  @Test
  public void extractTokenAndVerify_imprintMismatch_throwsTsaException() throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequest expectedRequest = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampRequest mismatchedRequest =
        createSampleRequest(
            "other data".getBytes(StandardCharsets.UTF_8), BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(mismatchedRequest, tokenGenerator, new Date());

    assertThrows(
        TsaException.class, () -> verifier.extractTokenAndVerify(response, expectedRequest));
  }

  @Test
  public void extractTokenAndVerify_missingOrNonCriticalTimeStampingEku_throwsTsaException()
      throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    long now = System.currentTimeMillis();
    X500Name issuer = new X500Name("CN=Test TSA, O=Test Org, C=US");

    // 1. Missing EKU extension
    X509Certificate certWithoutEku =
        createCertificate(
            issuer,
            BigInteger.ONE,
            new Date(now - 10000),
            new Date(now + 365L * 24 * 3600 * 1000),
            issuer,
            tsaKeyPair.getPublic(),
            tsaKeyPair.getPrivate(),
            false,
            null);
    TimeStampResponse noEkuResponse =
        createResponse(
            request,
            createTokenGenerator(
                tsaKeyPair.getPrivate(), certWithoutEku, List.of(certWithoutEku), null),
            new Date());
    TsaException noEkuEx =
        assertThrows(
            TsaException.class, () -> verifier.extractTokenAndVerify(noEkuResponse, request));
    assertThat(noEkuEx).hasCauseThat().hasMessageThat().contains("ExtendedKeyUsage");

    // 2. Non-critical EKU extension
    X509Certificate nonCriticalEkuCert =
        createCertificate(
            issuer,
            BigInteger.ONE,
            new Date(now - 10000),
            new Date(now + 365L * 24 * 3600 * 1000),
            issuer,
            tsaKeyPair.getPublic(),
            tsaKeyPair.getPrivate(),
            false,
            false);
    TimeStampResponse nonCriticalEkuResponse =
        createResponse(
            request,
            createTokenGenerator(
                tsaKeyPair.getPrivate(), nonCriticalEkuCert, List.of(nonCriticalEkuCert), null),
            new Date());
    TsaException nonCriticalEx =
        assertThrows(
            TsaException.class,
            () -> verifier.extractTokenAndVerify(nonCriticalEkuResponse, request));
    assertThat(nonCriticalEx).hasCauseThat().hasMessageThat().contains("ExtendedKeyUsage");
  }

  @Test
  public void extractTokenAndVerify_tsaRejectionStatus_throwsTsaException() throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponseGenerator respGen =
        new TimeStampResponseGenerator(tokenGenerator, TSPAlgorithms.ALLOWED);
    TimeStampResponse response =
        respGen.generateFailResponse(2, PKIFailureInfo.badAlg, "Request rejected");

    TsaException ex =
        assertThrows(TsaException.class, () -> verifier.extractTokenAndVerify(response, request));
    assertThat(ex).hasMessageThat().contains("badAlg");
  }

  @Test
  public void verify_validTokenAndMatchingData_succeeds() throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGenerator, new Date());

    verifier.verify(response.getTimeStampToken(), SAMPLE_BYTE_STRING);
  }

  @Test
  public void verify_historicalTokenOutsideClockSkewWindow_succeeds() throws Exception {
    Instant fixedNow = Instant.now();
    // Token was issued 30 days ago (well outside the 15-minute MAX_CLOCK_SKEW window)
    Instant historicalGenTime = fixedNow.minus(Duration.ofDays(30));
    // Certs were valid from 35 days ago to 25 days ago (valid at historicalGenTime, expired today)
    Date notBefore = Date.from(fixedNow.minus(Duration.ofDays(35)));
    Date notAfter = Date.from(fixedNow.minus(Duration.ofDays(25)));

    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Historical Root CA, O=Test Org, C=US");
    X509Certificate rootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            notBefore,
            notAfter,
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    KeyPair leafKeyPair = generateRsaKeyPair();
    X500Name leafName = new X500Name("CN=Historical Leaf TSA, O=Test Org, C=US");
    X509Certificate leafCert =
        createCertificate(
            rootName,
            BigInteger.valueOf(2),
            notBefore,
            notAfter,
            leafName,
            leafKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            false,
            true);

    Date genTimeDate = Date.from(historicalGenTime);
    TimeStampTokenGenerator tokenGen =
        createTokenGenerator(leafKeyPair.getPrivate(), leafCert, List.of(leafCert), genTimeDate);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGen, genTimeDate);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.fixed(fixedNow), ImmutableSet.of(new TrustAnchor(rootCert, null)));

    // verify(token, expectedData) must succeed for historical tokens without failing clock skew
    verifier.verify(response.getTimeStampToken(), SAMPLE_BYTE_STRING);
  }

  @Test
  public void verify_dataMismatch_throwsTsaException() throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGenerator, new Date());

    TsaException ex =
        assertThrows(
            TsaException.class,
            () ->
                verifier.verify(
                    response.getTimeStampToken(), ByteString.copyFromUtf8("tampered payload")));
    assertThat(ex).hasMessageThat().contains("message imprint digest does not match");
  }

  @Test
  public void verify_unapprovedHashAlgorithm_throwsTsaException() throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
    reqGen.setCertReq(true);
    ASN1ObjectIdentifier sha1Oid = new ASN1ObjectIdentifier("1.3.14.3.2.26");
    byte[] sha1Digest = MessageDigest.getInstance("SHA-1").digest(SAMPLE_DATA);
    TimeStampRequest sha1Request = reqGen.generate(sha1Oid, sha1Digest, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(sha1Request, tokenGenerator, new Date());

    TsaException ex =
        assertThrows(
            TsaException.class,
            () -> verifier.verify(response.getTimeStampToken(), SAMPLE_BYTE_STRING));
    assertThat(ex).hasMessageThat().contains("Unsupported or unapproved TSA hash algorithm OID");
  }

  @Test
  public void verify_intermediateCaChain_succeeds() throws Exception {
    long now = System.currentTimeMillis();
    Date notBefore = new Date(now - 60_000L);
    Date notAfter = new Date(now + 365L * 24 * 3600 * 1000);

    // 1. Root CA (self-signed)
    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Test Root CA, O=Test Org, C=US");
    X509Certificate rootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            notBefore,
            notAfter,
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    // 2. Intermediate CA (signed by Root CA)
    KeyPair intermediateKeyPair = generateRsaKeyPair();
    X500Name intermediateName = new X500Name("CN=Test Intermediate TSA CA, O=Test Org, C=US");
    X509Certificate intermediateCert =
        createCertificate(
            rootName,
            BigInteger.valueOf(2),
            notBefore,
            notAfter,
            intermediateName,
            intermediateKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    // 3. Leaf TSA certificate (signed by Intermediate CA)
    KeyPair leafKeyPair = generateRsaKeyPair();
    X500Name leafName = new X500Name("CN=Test Leaf TSA Signer, O=Test Org, C=US");
    X509Certificate leafCert =
        createCertificate(
            intermediateName,
            BigInteger.valueOf(3),
            notBefore,
            notAfter,
            leafName,
            leafKeyPair.getPublic(),
            intermediateKeyPair.getPrivate(),
            false,
            true);

    // Embed both leaf and intermediate certificates in the token; only Root CA is in trustAnchors
    TimeStampTokenGenerator chainTokenGen =
        createTokenGenerator(
            leafKeyPair.getPrivate(), leafCert, List.of(leafCert, intermediateCert), null);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(rootCert, null)));
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, chainTokenGen, new Date(now));

    com.google.pes.domain.model.TimeStampToken domainToken =
        verifier.extractTokenAndVerify(response, request);
    assertThat(domainToken.derBytes()).isNotEmpty();

    verifier.verify(response.getTimeStampToken(), SAMPLE_BYTE_STRING);
  }

  @Test
  public void verify_intermediateCaExpiredAtSigningTime_throwsTsaException() throws Exception {
    long now = System.currentTimeMillis();
    Date validNotBefore = new Date(now - 7200_000L);
    Date validNotAfter = new Date(now + 365L * 24 * 3600 * 1000);
    Date expiredNotBefore = new Date(now - 7200_000L);
    Date expiredNotAfter = new Date(now - 3600_000L);

    // 1. Valid Root CA
    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Test Root CA, O=Test Org, C=US");
    X509Certificate rootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            validNotBefore,
            validNotAfter,
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    // 2. Expired Intermediate CA
    KeyPair intermediateKeyPair = generateRsaKeyPair();
    X500Name intermediateName = new X500Name("CN=Expired Intermediate CA, O=Test Org, C=US");
    X509Certificate expiredIntermediateCert =
        createCertificate(
            rootName,
            BigInteger.valueOf(2),
            expiredNotBefore,
            expiredNotAfter,
            intermediateName,
            intermediateKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    // 3. Valid Leaf TSA certificate
    KeyPair leafKeyPair = generateRsaKeyPair();
    X500Name leafName = new X500Name("CN=Test Leaf TSA Signer, O=Test Org, C=US");
    X509Certificate leafCert =
        createCertificate(
            intermediateName,
            BigInteger.valueOf(3),
            validNotBefore,
            validNotAfter,
            leafName,
            leafKeyPair.getPublic(),
            intermediateKeyPair.getPrivate(),
            false,
            true);

    TimeStampTokenGenerator chainTokenGen =
        createTokenGenerator(
            leafKeyPair.getPrivate(), leafCert, List.of(leafCert, expiredIntermediateCert), null);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(rootCert, null)));
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, chainTokenGen, new Date(now));

    TsaException ex =
        assertThrows(
            TsaException.class,
            () -> verifier.verify(response.getTimeStampToken(), SAMPLE_BYTE_STRING));
    assertThat(ex).hasMessageThat().contains("Failed to validate TSA certificate chain");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void verify_multipleCandidatesFirstFailsSecondSucceeds_succeeds() throws Exception {
    long now = System.currentTimeMillis();
    Date notBefore = new Date(now - 60_000L);
    Date notAfter = new Date(now + 365L * 24 * 3600 * 1000);

    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Multi Candidate Root CA, O=Test Org, C=US");
    X509Certificate rootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            notBefore,
            notAfter,
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    KeyPair actualSignerKeyPair = generateRsaKeyPair();
    KeyPair wrongKeyPair = generateRsaKeyPair();
    X500Name leafSubject = new X500Name("CN=Multi Candidate TSA Signer, O=Test Org, C=US");
    BigInteger matchingSerial = BigInteger.valueOf(42);

    // Candidate 1 has the same issuer & serial as Candidate 2 (so both match token.getSID()),
    // but contains wrongKeyPair's public key so CMS signature verification fails on Candidate 1.
    X509Certificate candidate1WrongKey =
        createCertificate(
            rootName,
            matchingSerial,
            notBefore,
            notAfter,
            leafSubject,
            wrongKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            false,
            true);

    // Candidate 2 has the actual signing public key and valid chain to rootCert.
    X509Certificate candidate2Valid =
        createCertificate(
            rootName,
            matchingSerial,
            notBefore,
            notAfter,
            leafSubject,
            actualSignerKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            false,
            true);

    TimeStampTokenGenerator multiCandidateGen =
        createTokenGenerator(
            actualSignerKeyPair.getPrivate(),
            candidate2Valid,
            List.of(candidate1WrongKey, candidate2Valid),
            null);

    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, multiCandidateGen, new Date(now));
    TimeStampToken token = response.getTimeStampToken();

    Collection<X509CertificateHolder> matchedCandidates =
        token.getCertificates().getMatches(token.getSID());
    assertThat(matchedCandidates).hasSize(2);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(rootCert, null)));

    verifier.verify(token, SAMPLE_BYTE_STRING);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void verify_multipleCandidatesAllFail_throwsCombinedExceptionWithSuppressed()
      throws Exception {
    long now = System.currentTimeMillis();
    Date notBefore = new Date(now - 60_000L);
    Date notAfter = new Date(now + 365L * 24 * 3600 * 1000);

    KeyPair trustedRootKeyPair = generateRsaKeyPair();
    X500Name trustedRootName = new X500Name("CN=Trusted Root CA, O=Test Org, C=US");
    X509Certificate trustedRootCert =
        createCertificate(
            trustedRootName,
            BigInteger.ONE,
            notBefore,
            notAfter,
            trustedRootName,
            trustedRootKeyPair.getPublic(),
            trustedRootKeyPair.getPrivate(),
            true,
            null);

    KeyPair untrustedRootKeyPair = generateRsaKeyPair();
    X500Name untrustedRootName = new X500Name("CN=Untrusted Root CA, O=Test Org, C=US");

    KeyPair actualSignerKeyPair = generateRsaKeyPair();
    KeyPair wrongKeyPair = generateRsaKeyPair();
    X500Name leafSubject = new X500Name("CN=All Fail TSA Signer, O=Test Org, C=US");
    BigInteger matchingSerial = BigInteger.valueOf(99);

    // Candidate 1 has matching issuer & serial, but wrong public key -> fails CMS signature check.
    X509Certificate candidate1FailsSignature =
        createCertificate(
            untrustedRootName,
            matchingSerial,
            notBefore,
            notAfter,
            leafSubject,
            wrongKeyPair.getPublic(),
            untrustedRootKeyPair.getPrivate(),
            false,
            true);

    // Candidate 2 has matching issuer & serial and correct public key (passes CMS signature check),
    // but is signed by untrustedRootKeyPair -> fails PKIX certificate chain check.
    X509Certificate candidate2FailsChain =
        createCertificate(
            untrustedRootName,
            matchingSerial,
            notBefore,
            notAfter,
            leafSubject,
            actualSignerKeyPair.getPublic(),
            untrustedRootKeyPair.getPrivate(),
            false,
            true);

    TimeStampTokenGenerator multiFailGen =
        createTokenGenerator(
            actualSignerKeyPair.getPrivate(),
            candidate2FailsChain,
            List.of(candidate1FailsSignature, candidate2FailsChain),
            null);

    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, multiFailGen, new Date(now));
    TimeStampToken token = response.getTimeStampToken();

    Collection<X509CertificateHolder> matchedCandidates =
        token.getCertificates().getMatches(token.getSID());
    assertThat(matchedCandidates).hasSize(2);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(trustedRootCert, null)));

    TsaException ex =
        assertThrows(TsaException.class, () -> verifier.verify(token, SAMPLE_BYTE_STRING));
    assertThat(ex)
        .hasMessageThat()
        .startsWith("Failed to verify any signer certificate candidate: ");
    assertThat(ex).hasMessageThat().contains("Failed to validate TSA token CMS signature");
    assertThat(ex).hasMessageThat().contains("Failed to validate TSA certificate chain");
    assertThat(ex.getSuppressed()).hasLength(1);
    assertThat(ex.getSuppressed()[0])
        .hasMessageThat()
        .contains("Failed to validate TSA certificate chain");
  }

  @Test
  public void verify_missingSignerCertInToken_throwsTsaException() throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampTokenGenerator noCertsGen =
        createTokenGenerator(tsaKeyPair.getPrivate(), tsaCert, List.of(), null);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, noCertsGen, new Date());

    TsaException ex =
        assertThrows(
            TsaException.class,
            () -> verifier.verify(response.getTimeStampToken(), SAMPLE_BYTE_STRING));
    assertThat(ex).hasMessageThat().contains("TSA token did not include signer certificate");
  }

  @Test
  public void verify_domainTokenValidAndMalformed_succeedsOrThrowsTsaException() throws Exception {
    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGenerator, new Date());

    com.google.pes.domain.model.TimeStampToken domainToken =
        verifier.extractTokenAndVerify(response, request);
    verifier.verify(domainToken, SAMPLE_BYTE_STRING);

    com.google.pes.domain.model.TimeStampToken malformedDomainToken =
        new com.google.pes.domain.model.TimeStampToken(ByteString.copyFromUtf8("invalid-der"));
    TsaException ex =
        assertThrows(
            TsaException.class, () -> verifier.verify(malformedDomainToken, SAMPLE_BYTE_STRING));
    assertThat(ex).hasMessageThat().contains("Failed to parse TSA timestamp token");

    com.google.pes.domain.model.TimeStampToken trailingBytesToken =
        new com.google.pes.domain.model.TimeStampToken(
            domainToken.derBytes().concat(ByteString.copyFromUtf8("trailing-garbage")));
    TsaException trailingEx =
        assertThrows(
            TsaException.class, () -> verifier.verify(trailingBytesToken, SAMPLE_BYTE_STRING));
    assertThat(trailingEx).hasMessageThat().contains("Failed to parse TSA timestamp token");
  }

  @Test
  public void verify_nullArguments_throwsNullPointerException() throws Exception {
    assertThrows(
        NullPointerException.class, () -> new TsaTimestampVerifierImpl(null, testTrustAnchors));
    assertThrows(
        NullPointerException.class,
        () -> new TsaTimestampVerifierImpl(InstantSource.system(), null));

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGenerator, new Date());
    com.google.pes.domain.model.TimeStampToken domainToken =
        verifier.extractTokenAndVerify(response, request);

    assertThrows(NullPointerException.class, () -> verifier.extractTokenAndVerify(null, request));
    assertThrows(NullPointerException.class, () -> verifier.extractTokenAndVerify(response, null));
    assertThrows(
        NullPointerException.class,
        () -> verifier.verify((TimeStampToken) null, SAMPLE_BYTE_STRING));
    assertThrows(
        NullPointerException.class, () -> verifier.verify(response.getTimeStampToken(), null));
    assertThrows(
        NullPointerException.class,
        () ->
            verifier.verify((com.google.pes.domain.model.TimeStampToken) null, SAMPLE_BYTE_STRING));
    assertThrows(NullPointerException.class, () -> verifier.verify(domainToken, null));
  }

  @Test
  public void verify_unapprovedCmsDigestAlgorithm_throwsTsaException() throws Exception {
    ContentSigner sha1Signer =
        new JcaContentSignerBuilder("SHA1withRSA")
            .setProvider(BC_PROVIDER)
            .build(tsaKeyPair.getPrivate());
    DigestCalculator sha1DigestCalculator =
        new JcaDigestCalculatorProviderBuilder()
            .setProvider(BC_PROVIDER)
            .build()
            .get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1));
    SignerInfoGenerator signerInfoGen =
        new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider(BC_PROVIDER).build())
            .build(sha1Signer, new X509CertificateHolder(tsaCert.getEncoded()));
    TimeStampTokenGenerator sha1CmsTokenGen =
        new TimeStampTokenGenerator(
            signerInfoGen, sha1DigestCalculator, new ASN1ObjectIdentifier("1.2.3.4.5"));
    sha1CmsTokenGen.addCertificates(
        new JcaCertStore(List.of(new X509CertificateHolder(tsaCert.getEncoded()))));

    // Request uses approved SHA-384 for messageImprint, but token CMS signature uses SHA-1
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, sha1CmsTokenGen, new Date());

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TsaException ex =
        assertThrows(
            TsaException.class,
            () -> verifier.verify(response.getTimeStampToken(), SAMPLE_BYTE_STRING));
    assertThat(ex).hasMessageThat().contains("Unsupported or unapproved TSA CMS digest algorithm");
  }

  @Test
  public void verify_weakAlgorithmInCertificateChain_throwsTsaException() throws Exception {
    long now = System.currentTimeMillis();
    Date notBefore = new Date(now - 60_000L);
    Date notAfter = new Date(now + 365L * 24 * 3600 * 1000);

    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Test Root CA, O=Test Org, C=US");
    X509Certificate rootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            notBefore,
            notAfter,
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    // Leaf certificate is signed by Root CA using MD5withRSA, which JDK PKIX disabledAlgorithms
    // rejects
    KeyPair leafKeyPair = generateRsaKeyPair();
    X500Name leafName = new X500Name("CN=MD5 Signed Leaf TSA, O=Test Org, C=US");
    X509v3CertificateBuilder certBuilder =
        new X509v3CertificateBuilder(
            rootName,
            BigInteger.valueOf(2),
            notBefore,
            notAfter,
            leafName,
            SubjectPublicKeyInfo.getInstance(leafKeyPair.getPublic().getEncoded()));
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    certBuilder.addExtension(
        Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
    ContentSigner md5Signer =
        new JcaContentSignerBuilder("MD5withRSA")
            .setProvider(BC_PROVIDER)
            .build(rootKeyPair.getPrivate());
    X509Certificate md5LeafCert =
        new JcaX509CertificateConverter()
            .setProvider(BC_PROVIDER)
            .getCertificate(certBuilder.build(md5Signer));

    TimeStampTokenGenerator md5LeafTokenGen =
        createTokenGenerator(leafKeyPair.getPrivate(), md5LeafCert, List.of(md5LeafCert), null);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, md5LeafTokenGen, new Date(now));

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(rootCert, null)));
    TsaException ex =
        assertThrows(
            TsaException.class,
            () -> verifier.verify(response.getTimeStampToken(), SAMPLE_BYTE_STRING));
    assertThat(ex).hasMessageThat().contains("Failed to validate TSA certificate chain");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void verify_multipleCandidatesFirstThrowsRuntimeExceptionSecondSucceeds_succeeds()
      throws Exception {
    long now = System.currentTimeMillis();
    Date notBefore = new Date(now - 60_000L);
    Date notAfter = new Date(now + 365L * 24 * 3600 * 1000);

    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=V1 Candidate Root CA, O=Test Org, C=US");
    X509Certificate rootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            notBefore,
            notAfter,
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    KeyPair signerKeyPair = generateRsaKeyPair();
    X500Name leafSubject = new X500Name("CN=V1 And V3 TSA Signer, O=Test Org, C=US");
    BigInteger matchingSerial = BigInteger.valueOf(77);

    // Candidate 1 is an X.509 v1 certificate with matching issuer & serial; Bouncy Castle's
    // TSPUtil.validateCertificate throws unchecked IllegalArgumentException on v1 certificates.
    X509v1CertificateBuilder v1Builder =
        new X509v1CertificateBuilder(
            rootName,
            matchingSerial,
            notBefore,
            notAfter,
            leafSubject,
            SubjectPublicKeyInfo.getInstance(signerKeyPair.getPublic().getEncoded()));
    ContentSigner rootSigner =
        new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BC_PROVIDER)
            .build(rootKeyPair.getPrivate());
    X509Certificate candidate1V1Cert =
        new JcaX509CertificateConverter()
            .setProvider(BC_PROVIDER)
            .getCertificate(v1Builder.build(rootSigner));

    // Candidate 2 is the valid X.509 v3 certificate with critical id-kp-timeStamping EKU.
    X509Certificate candidate2ValidV3Cert =
        createCertificate(
            rootName,
            matchingSerial,
            notBefore,
            notAfter,
            leafSubject,
            signerKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            false,
            true);

    TimeStampTokenGenerator multiGen =
        createTokenGenerator(
            signerKeyPair.getPrivate(),
            candidate2ValidV3Cert,
            List.of(candidate1V1Cert, candidate2ValidV3Cert),
            null);

    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, multiGen, new Date(now));
    TimeStampToken token = response.getTimeStampToken();

    Collection<X509CertificateHolder> matchedCandidates =
        token.getCertificates().getMatches(token.getSID());
    assertThat(matchedCandidates).hasSize(2);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(rootCert, null)));

    verifier.verify(token, SAMPLE_BYTE_STRING);
  }

  @Test
  public void verify_unapprovedCmsSignatureAlgorithmWithApprovedDigest_throwsTsaException()
      throws Exception {
    ContentSigner sha256Signer =
        new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BC_PROVIDER)
            .build(tsaKeyPair.getPrivate());
    DigestCalculator sha256DigestCalculator =
        new JcaDigestCalculatorProviderBuilder()
            .setProvider(BC_PROVIDER)
            .build()
            .get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));
    // Force SignerInfo's signatureAlgorithm (encryptionAlgOID) to sha1WithRSAEncryption while
    // keeping digestAlgorithm as id-sha256.
    SignerInfoGenerator signerInfoGen =
        new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider(BC_PROVIDER).build(),
                sigAlgId -> new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption))
            .build(sha256Signer, new X509CertificateHolder(tsaCert.getEncoded()));
    TimeStampTokenGenerator mismatchedSigAlgGen =
        new TimeStampTokenGenerator(
            signerInfoGen, sha256DigestCalculator, new ASN1ObjectIdentifier("1.2.3.4.5"));
    mismatchedSigAlgGen.addCertificates(
        new JcaCertStore(List.of(new X509CertificateHolder(tsaCert.getEncoded()))));

    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, mismatchedSigAlgGen, new Date());
    TimeStampToken token = response.getTimeStampToken();

    SignerInformation signerInfo =
        token.toCMSSignedData().getSignerInfos().getSigners().iterator().next();
    assertThat(signerInfo.getDigestAlgOID()).isEqualTo(NISTObjectIdentifiers.id_sha256.getId());
    assertThat(signerInfo.getEncryptionAlgOID())
        .isEqualTo(PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TsaException ex =
        assertThrows(TsaException.class, () -> verifier.verify(token, SAMPLE_BYTE_STRING));
    assertThat(ex)
        .hasMessageThat()
        .contains("Unsupported or unapproved TSA CMS signature algorithm");
  }

  @Test
  public void verify_sha1SignedCertificateInChain_throwsTsaException() throws Exception {
    long now = System.currentTimeMillis();
    Date notBefore = new Date(now - 60_000L);
    Date notAfter = new Date(now + 365L * 24 * 3600 * 1000);

    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Test Root CA, O=Test Org, C=US");
    X509Certificate rootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            notBefore,
            notAfter,
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    // Leaf certificate is signed by Root CA using SHA1withRSA
    KeyPair leafKeyPair = generateRsaKeyPair();
    X500Name leafName = new X500Name("CN=SHA1 Signed Leaf TSA, O=Test Org, C=US");
    X509v3CertificateBuilder certBuilder =
        new X509v3CertificateBuilder(
            rootName,
            BigInteger.valueOf(2),
            notBefore,
            notAfter,
            leafName,
            SubjectPublicKeyInfo.getInstance(leafKeyPair.getPublic().getEncoded()));
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    certBuilder.addExtension(
        Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
    ContentSigner sha1Signer =
        new JcaContentSignerBuilder("SHA1withRSA")
            .setProvider(BC_PROVIDER)
            .build(rootKeyPair.getPrivate());
    X509Certificate sha1LeafCert =
        new JcaX509CertificateConverter()
            .setProvider(BC_PROVIDER)
            .getCertificate(certBuilder.build(sha1Signer));

    TimeStampTokenGenerator sha1LeafTokenGen =
        createTokenGenerator(leafKeyPair.getPrivate(), sha1LeafCert, List.of(sha1LeafCert), null);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, sha1LeafTokenGen, new Date(now));

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(rootCert, null)));
    TsaException ex =
        assertThrows(
            TsaException.class,
            () -> verifier.verify(response.getTimeStampToken(), SAMPLE_BYTE_STRING));
    assertThat(ex).hasMessageThat().contains("Failed to validate TSA certificate chain");
    assertThat(ex)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Unsupported or unapproved certificate signature algorithm OID");
  }

  @Test
  public void verify_weakRsaKeySizeInCertificateChain_throwsTsaException() throws Exception {
    long now = System.currentTimeMillis();
    Date notBefore = new Date(now - 60_000L);
    Date notAfter = new Date(now + 365L * 24 * 3600 * 1000);

    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Test Root CA, O=Test Org, C=US");
    X509Certificate rootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            notBefore,
            notAfter,
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    // Leaf certificate uses a 1024-bit RSA key
    KeyPairGenerator kpg1024 = KeyPairGenerator.getInstance("RSA", BC_PROVIDER);
    kpg1024.initialize(1024);
    KeyPair weakLeafKeyPair = kpg1024.generateKeyPair();
    X500Name leafName = new X500Name("CN=1024-bit RSA Leaf TSA, O=Test Org, C=US");
    X509Certificate weakLeafCert =
        createCertificate(
            rootName,
            BigInteger.valueOf(2),
            notBefore,
            notAfter,
            leafName,
            weakLeafKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            false,
            true);

    TimeStampTokenGenerator weakKeyTokenGen =
        createTokenGenerator(
            weakLeafKeyPair.getPrivate(), weakLeafCert, List.of(weakLeafCert), null);
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, weakKeyTokenGen, new Date(now));

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(rootCert, null)));
    TsaException ex =
        assertThrows(
            TsaException.class,
            () -> verifier.verify(response.getTimeStampToken(), SAMPLE_BYTE_STRING));
    assertThat(ex).hasMessageThat().contains("Failed to validate TSA certificate chain");
    assertThat(ex).hasCauseThat().hasMessageThat().contains("RSA key size must be at least 2048");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void verify_multipleCandidatesFirstHasMalformedExtensionSecondSucceeds_succeeds()
      throws Exception {
    long now = System.currentTimeMillis();
    Date notBefore = new Date(now - 60_000L);
    Date notAfter = new Date(now + 365L * 24 * 3600 * 1000);

    KeyPair rootKeyPair = generateRsaKeyPair();
    X500Name rootName = new X500Name("CN=Malformed Ext Root CA, O=Test Org, C=US");
    X509Certificate rootCert =
        createCertificate(
            rootName,
            BigInteger.ONE,
            notBefore,
            notAfter,
            rootName,
            rootKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            true,
            null);

    KeyPair signerKeyPair = generateRsaKeyPair();
    X500Name leafSubject = new X500Name("CN=Malformed Ext TSA Signer, O=Test Org, C=US");
    BigInteger matchingSerial = BigInteger.valueOf(88);

    // Candidate 1 has valid CMS signature and EKU (so it passes verifyTokenSignature), but has a
    // malformed SubjectAlternativeName extension that causes SUN's CertificateFactory to throw
    // CertificateParsingException during validateCertificateChain.
    X509v3CertificateBuilder malformedBuilder =
        new X509v3CertificateBuilder(
            rootName,
            matchingSerial,
            notBefore,
            notAfter,
            leafSubject,
            SubjectPublicKeyInfo.getInstance(signerKeyPair.getPublic().getEncoded()));
    malformedBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    malformedBuilder.addExtension(
        Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
    malformedBuilder.addExtension(
        Extension.subjectAlternativeName, false, new byte[] {0x04, 0x02, 0x01, 0x02});
    ContentSigner rootSigner =
        new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BC_PROVIDER)
            .build(rootKeyPair.getPrivate());
    X509CertificateHolder candidate1MalformedHolder = malformedBuilder.build(rootSigner);

    // Candidate 2 is the valid X.509 v3 certificate.
    X509Certificate candidate2ValidCert =
        createCertificate(
            rootName,
            matchingSerial,
            notBefore,
            notAfter,
            leafSubject,
            signerKeyPair.getPublic(),
            rootKeyPair.getPrivate(),
            false,
            true);
    X509CertificateHolder candidate2ValidHolder =
        new X509CertificateHolder(candidate2ValidCert.getEncoded());

    TimeStampTokenGenerator multiGen =
        createTokenGenerator(signerKeyPair.getPrivate(), candidate2ValidCert, List.of(), null);
    multiGen.addCertificates(
        new JcaCertStore(List.of(candidate1MalformedHolder, candidate2ValidHolder)));

    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, multiGen, new Date(now));
    TimeStampToken token = response.getTimeStampToken();

    Collection<X509CertificateHolder> matchedCandidates =
        token.getCertificates().getMatches(token.getSID());
    assertThat(matchedCandidates).hasSize(2);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(
            InstantSource.system(), ImmutableSet.of(new TrustAnchor(rootCert, null)));

    verifier.verify(token, SAMPLE_BYTE_STRING);
  }

  @Test
  public void verify_malformedCertificatesSetInToken_throwsTsaException() throws Exception {
    TimeStampRequest request = createSampleRequest(SAMPLE_DATA, BigInteger.valueOf(9999));
    TimeStampResponse response = createResponse(request, tokenGenerator, new Date());
    ContentInfo validCi = response.getTimeStampToken().toCMSSignedData().toASN1Structure();
    SignedData validSd = SignedData.getInstance(validCi.getContent());
    ContentInfo corruptedCi =
        new ContentInfo(
            CMSObjectIdentifiers.signedData,
            new SignedData(
                validSd.getDigestAlgorithms(),
                validSd.getEncapContentInfo(),
                new DERSet(new DERSequence(new DERUTF8String("malformed-cert-sequence"))),
                validSd.getCRLs(),
                validSd.getSignerInfos()));
    TimeStampToken tokenWithMalformedCerts = new TimeStampToken(corruptedCi);

    TsaTimestampVerifier verifier =
        new TsaTimestampVerifierImpl(InstantSource.system(), testTrustAnchors);
    TsaException ex =
        assertThrows(
            TsaException.class, () -> verifier.verify(tokenWithMalformedCerts, SAMPLE_BYTE_STRING));
    assertThat(ex)
        .hasMessageThat()
        .contains("Failed to extract signer certificates from TSA token");
  }
}
