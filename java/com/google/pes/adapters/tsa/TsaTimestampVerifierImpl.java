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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.pes.domain.ports.TsaException;
import com.google.protobuf.ByteString;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Store;

/**
 * Implementation of {@link TsaTimestampVerifier} using Bouncy Castle and JCA PKIX path validation.
 */
@Singleton
public class TsaTimestampVerifierImpl implements TsaTimestampVerifier {
  public static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(15);

  private static final Provider BC_PROVIDER = new BouncyCastleProvider();
  private static final JcaSimpleSignerInfoVerifierBuilder SIGNER_INFO_VERIFIER_BUILDER =
      new JcaSimpleSignerInfoVerifierBuilder().setProvider(BC_PROVIDER);
  private static final JcaX509CertificateConverter CERT_CONVERTER =
      new JcaX509CertificateConverter();
  private static final ImmutableMap<ASN1ObjectIdentifier, String> APPROVED_HASH_ALGORITHMS =
      ImmutableMap.of(
          NISTObjectIdentifiers.id_sha256, "SHA-256",
          NISTObjectIdentifiers.id_sha384, "SHA-384",
          NISTObjectIdentifiers.id_sha512, "SHA-512");
  private static final ImmutableSet<ASN1ObjectIdentifier> APPROVED_SIGNATURE_ALGORITHMS =
      ImmutableSet.of(
          PKCSObjectIdentifiers.sha256WithRSAEncryption,
          PKCSObjectIdentifiers.sha384WithRSAEncryption,
          PKCSObjectIdentifiers.sha512WithRSAEncryption,
          X9ObjectIdentifiers.ecdsa_with_SHA256,
          X9ObjectIdentifiers.ecdsa_with_SHA384,
          X9ObjectIdentifiers.ecdsa_with_SHA512);
  private static final ImmutableSet<ASN1ObjectIdentifier> APPROVED_CMS_ENCRYPTION_ALGORITHMS =
      ImmutableSet.<ASN1ObjectIdentifier>builder()
          .addAll(APPROVED_SIGNATURE_ALGORITHMS)
          .add(PKCSObjectIdentifiers.rsaEncryption)
          .add(X9ObjectIdentifiers.id_ecPublicKey)
          .build();
  private static final int MIN_RSA_KEY_BITS = 2048;
  private static final int MIN_EC_FIELD_BITS = 256;
  private static final ImmutableMap<Integer, String> FAILURE_INFO_FLAGS =
      ImmutableMap.<Integer, String>builder()
          .put(PKIFailureInfo.badAlg, "badAlg")
          .put(PKIFailureInfo.badMessageCheck, "badMessageCheck")
          .put(PKIFailureInfo.badRequest, "badRequest")
          .put(PKIFailureInfo.badTime, "badTime")
          .put(PKIFailureInfo.badCertId, "badCertId")
          .put(PKIFailureInfo.badDataFormat, "badDataFormat")
          .put(PKIFailureInfo.wrongAuthority, "wrongAuthority")
          .put(PKIFailureInfo.incorrectData, "incorrectData")
          .put(PKIFailureInfo.missingTimeStamp, "missingTimeStamp")
          .put(PKIFailureInfo.badPOP, "badPOP")
          .put(PKIFailureInfo.timeNotAvailable, "timeNotAvailable")
          .put(PKIFailureInfo.unacceptedPolicy, "unacceptedPolicy")
          .put(PKIFailureInfo.unacceptedExtension, "unacceptedExtension")
          .put(PKIFailureInfo.addInfoNotAvailable, "addInfoNotAvailable")
          .put(PKIFailureInfo.systemFailure, "systemFailure")
          .buildOrThrow();

  private final InstantSource instantSource;
  private final ImmutableSet<TrustAnchor> trustAnchors;

  @Inject
  public TsaTimestampVerifierImpl(InstantSource instantSource, Set<TrustAnchor> trustAnchors) {
    this.instantSource = Objects.requireNonNull(instantSource, "instantSource cannot be null");
    Objects.requireNonNull(trustAnchors, "trustAnchors cannot be null");
    if (trustAnchors.isEmpty()) {
      throw new IllegalArgumentException("trustAnchors cannot be empty");
    }
    this.trustAnchors = ImmutableSet.copyOf(trustAnchors);
  }

  @Override
  public com.google.pes.domain.model.TimeStampToken extractTokenAndVerify(
      TimeStampResponse response, TimeStampRequest request) {
    Objects.requireNonNull(response, "response cannot be null");
    Objects.requireNonNull(request, "request cannot be null");

    verifyResponseStatus(response);
    TimeStampToken token = extractTimeStampToken(response);
    verifyResponseMatchesRequest(response, request);
    verifyTimeSkew(token);
    verify(token);
    return toDomainToken(token);
  }

  @Override
  public void verify(TimeStampToken token, ByteString expectedData) {
    Objects.requireNonNull(token, "token cannot be null");
    Objects.requireNonNull(expectedData, "expectedData cannot be null");

    verifyMessageImprint(token, expectedData);
    verify(token);
  }

  @Override
  public void verify(com.google.pes.domain.model.TimeStampToken token, ByteString expectedData) {
    Objects.requireNonNull(token, "token cannot be null");
    Objects.requireNonNull(expectedData, "expectedData cannot be null");

    TimeStampToken bcToken;
    try {
      ASN1Primitive asn1 = ASN1Primitive.fromByteArray(token.derBytes().toByteArray());
      if (asn1 == null) {
        throw new TsaException("TSA timestamp token DER bytes cannot be empty");
      }
      bcToken = new TimeStampToken(ContentInfo.getInstance(asn1));
    } catch (TsaException e) {
      throw e;
    } catch (IOException | TSPException | RuntimeException e) {
      throw new TsaException("Failed to parse TSA timestamp token", e);
    }
    verify(bcToken, expectedData);
  }

  private void verify(TimeStampToken token) {
    Collection<X509CertificateHolder> candidates = extractSignerCertificates(token);
    List<TsaException> failures = new ArrayList<>();
    boolean verified =
        candidates.stream().anyMatch(candidate -> tryVerifyCandidate(token, candidate, failures));
    if (!verified) {
      throw combineExceptions(failures);
    }
  }

  private boolean tryVerifyCandidate(
      TimeStampToken token, X509CertificateHolder candidate, List<TsaException> failures) {
    try {
      verifyTokenSignature(token, candidate);
      verifyCertificateChain(token, candidate);
      return true;
    } catch (TsaException e) {
      failures.add(e);
      return false;
    }
  }

  private static TsaException combineExceptions(List<TsaException> failures) {
    if (failures.size() == 1) {
      return failures.get(0);
    }
    String combinedMessage =
        failures.stream()
            .map(Throwable::getMessage)
            .collect(joining("; ", "Failed to verify any signer certificate candidate: ", ""));
    TsaException combined = new TsaException(combinedMessage, failures.get(0));
    failures.stream().skip(1).forEach(combined::addSuppressed);
    return combined;
  }

  private void verifyMessageImprint(TimeStampToken token, ByteString expectedData) {
    ASN1ObjectIdentifier algOid = token.getTimeStampInfo().getMessageImprintAlgOID();
    String hashAlgName = APPROVED_HASH_ALGORITHMS.get(algOid);
    if (hashAlgName == null) {
      throw new TsaException("Unsupported or unapproved TSA hash algorithm OID: " + algOid);
    }

    byte[] expectedDigest;
    try {
      MessageDigest md = MessageDigest.getInstance(hashAlgName);
      for (ByteBuffer buffer : expectedData.asReadOnlyByteBufferList()) {
        md.update(buffer);
      }
      expectedDigest = md.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Hash algorithm not available: " + hashAlgName, e);
    }

    byte[] actualDigest = token.getTimeStampInfo().getMessageImprintDigest();
    if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
      throw new TsaException("TSA token message imprint digest does not match expected data");
    }
  }

  private void verifyResponseStatus(TimeStampResponse response) {
    int status;
    String statusString;
    PKIFailureInfo failInfo;
    try {
      status = response.getStatus();
      statusString = response.getStatusString();
      failInfo = response.getFailInfo();
    } catch (RuntimeException e) {
      throw new TsaException("Failed to read TSA response status", e);
    }
    if (status != PKIStatus.GRANTED && status != PKIStatus.GRANTED_WITH_MODS) {
      throw new TsaException(
          String.format(
              "TSA rejected request. Status: %d, info: %s, failureInfo: %s",
              status, statusString, formatFailInfo(failInfo)));
    }
  }

  private void verifyResponseMatchesRequest(TimeStampResponse response, TimeStampRequest request) {
    if (request.getNonce() == null) {
      throw new TsaException("TimeStampRequest must contain a nonce");
    }
    if (!APPROVED_HASH_ALGORITHMS.containsKey(request.getMessageImprintAlgOID())) {
      throw new TsaException(
          "Unsupported or unapproved TSA hash algorithm OID: " + request.getMessageImprintAlgOID());
    }
    try {
      response.validate(request);
    } catch (TSPException | RuntimeException e) {
      throw new TsaException("TSA response validation failed", e);
    }
  }

  private TimeStampToken extractTimeStampToken(TimeStampResponse response) {
    TimeStampToken token = response.getTimeStampToken();
    if (token == null) {
      String failInfoStr = formatFailInfo(response.getFailInfo());
      throw new TsaException(
          String.format(
              "TSA response did not contain a timestamp token. Status: %d, info: %s,"
                  + " failureInfo: %s",
              response.getStatus(), response.getStatusString(), failInfoStr));
    }
    return token;
  }

  private com.google.pes.domain.model.TimeStampToken toDomainToken(TimeStampToken token) {
    try {
      return new com.google.pes.domain.model.TimeStampToken(
          ByteString.copyFrom(token.getEncoded()));
    } catch (IOException e) {
      throw new TsaException("Failed to encode verified TSA timestamp token", e);
    }
  }

  private void verifyTimeSkew(TimeStampToken token) {
    Instant genTime = token.getTimeStampInfo().getGenTime().toInstant();
    Instant now = instantSource.instant();
    Duration skew = Duration.between(now, genTime).abs();
    if (skew.compareTo(MAX_CLOCK_SKEW) > 0) {
      throw new TsaException(
          String.format(
              "TSA timestamp genTime (%s) exceeds maximum allowed clock skew (%s) from"
                  + " current time (%s)",
              genTime, MAX_CLOCK_SKEW, now));
    }
  }

  @SuppressWarnings("unchecked")
  private Collection<X509CertificateHolder> extractSignerCertificates(TimeStampToken token) {
    Collection<X509CertificateHolder> matches;
    try {
      Store<X509CertificateHolder> certStore = token.getCertificates();
      matches = certStore.getMatches(token.getSID());
    } catch (RuntimeException e) {
      throw new TsaException("Failed to extract signer certificates from TSA token", e);
    }
    if (matches.isEmpty()) {
      throw new TsaException("TSA token did not include signer certificate");
    }
    return matches;
  }

  private void verifyTokenSignature(TimeStampToken token, X509CertificateHolder signerCertHolder) {
    for (SignerInformation signerInfo : token.toCMSSignedData().getSignerInfos().getSigners()) {
      ASN1ObjectIdentifier digestAlgOid = new ASN1ObjectIdentifier(signerInfo.getDigestAlgOID());
      if (!APPROVED_HASH_ALGORITHMS.containsKey(digestAlgOid)) {
        throw new TsaException(
            "Unsupported or unapproved TSA CMS digest algorithm OID: " + digestAlgOid);
      }
      ASN1ObjectIdentifier encryptionAlgOid =
          new ASN1ObjectIdentifier(signerInfo.getEncryptionAlgOID());
      if (!APPROVED_CMS_ENCRYPTION_ALGORITHMS.contains(encryptionAlgOid)) {
        throw new TsaException(
            "Unsupported or unapproved TSA CMS signature algorithm OID: " + encryptionAlgOid);
      }
    }
    try {
      SignerInformationVerifier verifier = SIGNER_INFO_VERIFIER_BUILDER.build(signerCertHolder);
      token.validate(verifier);
    } catch (TSPException
        | OperatorCreationException
        | GeneralSecurityException
        | RuntimeException e) {
      throw new TsaException("Failed to validate TSA token CMS signature", e);
    }
  }

  private void verifyCertificateChain(
      TimeStampToken token, X509CertificateHolder signerCertHolder) {
    try {
      validateCertificateChain(
          signerCertHolder,
          token.getCertificates(),
          trustAnchors,
          token.getTimeStampInfo().getGenTime());
    } catch (GeneralSecurityException | RuntimeException e) {
      throw new TsaException("Failed to validate TSA certificate chain", e);
    }
  }

  /**
   * Validates that {@code signerCertHolder} chains up to one of the configured {@link TrustAnchor}
   * certificates using JCA PKIX path validation at {@code validationDate}, including checking that
   * the trust anchor certificate itself is valid at {@code validationDate}.
   */
  @SuppressWarnings("unchecked")
  private static void validateCertificateChain(
      X509CertificateHolder signerCertHolder,
      Store<X509CertificateHolder> certStore,
      Set<TrustAnchor> anchors,
      Date validationDate)
      throws GeneralSecurityException {
    Set<TrustAnchor> validAnchors =
        anchors.stream()
            .filter(anchor -> isTrustAnchorValidAt(anchor, validationDate))
            .collect(toImmutableSet());
    if (validAnchors.isEmpty()) {
      throw new CertPathBuilderException(
          "No configured trust anchors are valid at " + validationDate);
    }

    X509Certificate signerX509Cert = CERT_CONVERTER.getCertificate(signerCertHolder);
    List<X509Certificate> x509Certs = new ArrayList<>();
    for (X509CertificateHolder holder : certStore.getMatches(null)) {
      try {
        x509Certs.add(CERT_CONVERTER.getCertificate(holder));
      } catch (CertificateException e) {
        // Skip malformed bystander certificates in the CMS bag so they do not abort path
        // validation for a valid candidate.
      }
    }

    X509CertSelector targetConstraints = new X509CertSelector();
    targetConstraints.setCertificate(signerX509Cert);

    PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(validAnchors, targetConstraints);
    // Online CRL/OCSP revocation checking is disabled because verification runs inside an isolated
    // enclave environment without outbound network access to external CA revocation endpoints.
    pkixParams.setRevocationEnabled(false);
    pkixParams.setDate(validationDate);
    pkixParams.addCertStore(
        CertStore.getInstance("Collection", new CollectionCertStoreParameters(x509Certs)));

    PKIXCertPathBuilderResult result =
        (PKIXCertPathBuilderResult) CertPathBuilder.getInstance("PKIX").build(pkixParams);

    validatePublicKey(signerX509Cert.getPublicKey());
    for (Certificate cert : result.getCertPath().getCertificates()) {
      X509Certificate x509Cert = (X509Certificate) cert;
      ASN1ObjectIdentifier sigAlgOid = new ASN1ObjectIdentifier(x509Cert.getSigAlgOID());
      if (!APPROVED_SIGNATURE_ALGORITHMS.contains(sigAlgOid)) {
        throw new CertPathBuilderException(
            "Unsupported or unapproved certificate signature algorithm OID: " + sigAlgOid);
      }
      validatePublicKey(x509Cert.getPublicKey());
    }
    X509Certificate anchorCert = result.getTrustAnchor().getTrustedCert();
    if (anchorCert != null) {
      validatePublicKey(anchorCert.getPublicKey());
    } else if (result.getTrustAnchor().getCAPublicKey() != null) {
      validatePublicKey(result.getTrustAnchor().getCAPublicKey());
    }
  }

  private static void validatePublicKey(PublicKey publicKey) throws CertPathBuilderException {
    if (publicKey instanceof RSAPublicKey rsaKey) {
      int bitLength = rsaKey.getModulus().bitLength();
      if (bitLength < MIN_RSA_KEY_BITS) {
        throw new CertPathBuilderException(
            "RSA key size must be at least " + MIN_RSA_KEY_BITS + " bits, but was " + bitLength);
      }
    } else if (publicKey instanceof ECPublicKey ecKey) {
      int fieldSize = ecKey.getParams().getCurve().getField().getFieldSize();
      if (fieldSize < MIN_EC_FIELD_BITS) {
        throw new CertPathBuilderException(
            "EC key field size must be at least "
                + MIN_EC_FIELD_BITS
                + " bits, but was "
                + fieldSize);
      }
    } else {
      throw new CertPathBuilderException(
          "Unsupported certificate public key algorithm: " + publicKey.getAlgorithm());
    }
  }

  private static boolean isTrustAnchorValidAt(TrustAnchor anchor, Date validationDate) {
    X509Certificate anchorCert = anchor.getTrustedCert();
    if (anchorCert == null) {
      return true;
    }
    try {
      anchorCert.checkValidity(validationDate);
      return true;
    } catch (GeneralSecurityException e) {
      return false;
    }
  }

  private static String formatFailInfo(PKIFailureInfo failInfo) {
    if (failInfo == null) {
      return "none";
    }
    int value = failInfo.intValue();
    List<String> reasons =
        FAILURE_INFO_FLAGS.entrySet().stream()
            .filter(entry -> (value & entry.getKey()) != 0)
            .map(Map.Entry::getValue)
            .toList();
    return String.format(
        "0x%x (%s)", value, reasons.isEmpty() ? "unknown" : String.join(", ", reasons));
  }
}
