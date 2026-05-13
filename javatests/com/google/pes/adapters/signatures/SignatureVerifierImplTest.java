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

package com.google.pes.adapters.signatures;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.model.Statement.Format;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.InvalidSignatureException;
import com.google.pes.domain.ports.InvalidVerificationMaterialException;
import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SignatureVerifierImplTest {

  private SignatureVerifierImpl verifier;

  private static final ByteString TEST_DATA = ByteString.copyFromUtf8("Test statement data");
  private static final Statement TEST_STATEMENT = new Statement(Format.JSON_INTOTO, TEST_DATA);
  private static final String SHA_WITH_RSA = "SHA256withRSA";
  private static final String SHA_WITH_ECDSA = "SHA256withECDSA";
  private static final String SHA_WITH_DSA = "SHA256withDSA";

  @Before
  public void setUp() {
    Security.addProvider(new BouncyCastleProvider());
    verifier = new SignatureVerifierImpl();
  }

  @Test
  public void verify_validCertRsaSignature_doesNotThrow() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    byte[] certBytes = createSelfSignedCertificate(keyPair, SHA_WITH_RSA);
    ByteString signatureBytes = signData(TEST_DATA, keyPair.getPrivate(), SHA_WITH_RSA);

    Signature signature =
        new Signature(
            signatureBytes,
            new VerificationMaterial(
                ByteString.copyFrom(certBytes), VerificationMaterial.Format.X509_DER));

    verifier.verify(signature, TEST_STATEMENT.serialized());
  }

  @Test
  public void verify_validEcdsaStandAloneKey_doesNotThrow() throws Exception {
    KeyPair keyPair = generateEcKeyPair();
    byte[] keyBytes = keyPair.getPublic().getEncoded();
    ByteString signatureBytes = signData(TEST_DATA, keyPair.getPrivate(), SHA_WITH_ECDSA);

    Signature signature =
        new Signature(
            signatureBytes,
            new VerificationMaterial(
                ByteString.copyFrom(keyBytes), VerificationMaterial.Format.ECDSA_P256_SHA256));

    verifier.verify(signature, TEST_STATEMENT.serialized());
  }

  @Test
  public void verify_validCertEcSignature_doesNotThrow() throws Exception {
    KeyPair keyPair = generateEcKeyPair();
    byte[] certBytes = createSelfSignedCertificate(keyPair, SHA_WITH_ECDSA);
    ByteString signatureBytes = signData(TEST_DATA, keyPair.getPrivate(), SHA_WITH_ECDSA);
    Signature signature =
        new Signature(
            signatureBytes,
            new VerificationMaterial(
                ByteString.copyFrom(certBytes), VerificationMaterial.Format.X509_DER));

    verifier.verify(signature, TEST_STATEMENT.serialized());
  }

  @Test
  public void verify_invalidCertificateEncoding_throwsInvalidMaterialException() {
    Signature signature =
        new Signature(
            ByteString.EMPTY,
            new VerificationMaterial(
                ByteString.copyFromUtf8("This is not a valid DER certificate"),
                VerificationMaterial.Format.X509_DER));

    assertThrows(
        InvalidVerificationMaterialException.class,
        () -> verifier.verify(signature, TEST_STATEMENT.serialized()));
  }

  @Test
  public void verify_invalidStandaloneEcdsaKey_throwsInvalidMaterialException() {
    Signature signature =
        new Signature(
            ByteString.EMPTY,
            new VerificationMaterial(
                ByteString.copyFromUtf8("This is not a valid Der encoded key"),
                VerificationMaterial.Format.ECDSA_P256_SHA256));

    assertThrows(
        InvalidVerificationMaterialException.class,
        () -> verifier.verify(signature, TEST_STATEMENT.serialized()));
  }

  @Test
  public void verify_signatureTampered_throwsInvalidSignatureException() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    byte[] certBytes = createSelfSignedCertificate(keyPair, SHA_WITH_RSA);
    ByteString originalSignatureBytes = signData(TEST_DATA, keyPair.getPrivate(), SHA_WITH_RSA);
    ByteString tamperedSignature =
        originalSignatureBytes.concat(ByteString.copyFromUtf8("tampered"));
    Signature signature =
        new Signature(
            tamperedSignature,
            new VerificationMaterial(
                ByteString.copyFrom(certBytes), VerificationMaterial.Format.X509_DER));

    assertThrows(
        InvalidSignatureException.class,
        () -> verifier.verify(signature, TEST_STATEMENT.serialized()));
  }

  @Test
  public void verify_statementDataTampered_throwsInvalidSignatureException() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    byte[] certBytes = createSelfSignedCertificate(keyPair, SHA_WITH_RSA);
    ByteString signatureBytes = signData(TEST_DATA, keyPair.getPrivate(), SHA_WITH_RSA);

    Signature signature =
        new Signature(
            signatureBytes,
            new VerificationMaterial(
                ByteString.copyFrom(certBytes), VerificationMaterial.Format.X509_DER));

    Statement tamperedStatement =
        new Statement(
            Format.JSON_INTOTO, TEST_DATA.concat(ByteString.copyFromUtf8("tampered data")));

    assertThrows(
        InvalidSignatureException.class,
        () -> verifier.verify(signature, tamperedStatement.serialized()));
  }

  @Test
  public void verify_unsupportedPublicKeyType_throwsIllegalArgumentException() throws Exception {
    KeyPair keyPair = generateDsaKeyPair(); // DSA is not supported by deduceAlgorithm
    byte[] certBytes = createSelfSignedCertificate(keyPair, SHA_WITH_DSA);

    Signature signature =
        new Signature(
            ByteString.EMPTY,
            new com.google.pes.domain.model.VerificationMaterial(
                ByteString.copyFrom(certBytes),
                com.google.pes.domain.model.VerificationMaterial.Format.X509_DER));

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> verifier.verify(signature, TEST_STATEMENT.serialized()));
    assertThat(e).hasMessageThat().contains("Unsupported Public Key Type");
  }

  @Test
  public void verify_emptySignatureBytes_throwsInvalidSignatureException() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    byte[] certBytes = createSelfSignedCertificate(keyPair, SHA_WITH_RSA);

    Signature signature =
        new Signature(
            ByteString.EMPTY,
            new VerificationMaterial(
                ByteString.copyFrom(certBytes), VerificationMaterial.Format.X509_DER));

    assertThrows(
        InvalidSignatureException.class,
        () -> verifier.verify(signature, TEST_STATEMENT.serialized()));
  }

  @Test
  public void verify_emptyVerificationMaterial_throwsInvalidArgumentException() {
    Signature signature =
        new Signature(
            ByteString.copyFromUtf8("signature"),
            new VerificationMaterial(ByteString.EMPTY, VerificationMaterial.Format.X509_DER));

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> verifier.verify(signature, TEST_STATEMENT.serialized()));
    assertThat(e).hasMessageThat().contains("material cannot be empty");
  }

  @Test
  public void verify_unsupportedVerificationMethod_throwsIllegalArgumentException() {
    Signature signatureWithUnspecifiedMethod =
        new Signature(
            ByteString.copyFromUtf8("signature"),
            new VerificationMaterial(
                ByteString.copyFromUtf8("material"),
                VerificationMaterial.Format.FORMAT_UNSPECIFIED));

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> verifier.verify(signatureWithUnspecifiedMethod, TEST_STATEMENT.serialized()));
    assertThat(e).hasMessageThat().contains("verification material is unsupported");
  }

  private KeyPair generateKeyPair(String algorithm, int keySize) throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm);
    kpg.initialize(keySize, new SecureRandom());
    return kpg.generateKeyPair();
  }

  private KeyPair generateRsaKeyPair() throws Exception {
    return generateKeyPair("RSA", 2048);
  }

  private KeyPair generateEcKeyPair() throws Exception {
    return generateKeyPair("EC", 256);
  }

  private KeyPair generateDsaKeyPair() throws Exception {
    return generateKeyPair("DSA", 1024);
  }

  private byte[] createSelfSignedCertificate(KeyPair keyPair, String signatureAlgorithm)
      throws Exception {
    X500Name issuer = new X500Name("CN=Test Issuer");
    X500Name subject = new X500Name("CN=Test Subject");
    BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
    Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24);
    Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365);

    SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

    X509v3CertificateBuilder certBuilder =
        new X509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, spki);

    ContentSigner contentSigner =
        new JcaContentSignerBuilder(signatureAlgorithm)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.getPrivate());

    return new JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .getCertificate(certBuilder.build(contentSigner))
        .getEncoded();
  }

  private ByteString signData(ByteString data, PrivateKey privateKey, String algorithm)
      throws Exception {
    java.security.Signature signer = java.security.Signature.getInstance(algorithm);
    signer.initSign(privateKey);
    signer.update(data.toByteArray());
    return ByteString.copyFrom(signer.sign());
  }
}
