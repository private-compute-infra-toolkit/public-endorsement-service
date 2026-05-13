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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.mbs.MbsCertificateFactory;
import com.google.mbs.MeasurementBoundCertificate;
import com.google.mbs.MeasurementBoundCertificateProvider;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.PesSignatureException;
import com.google.protobuf.ByteString;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Optional;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class SignerImplTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private SignatureGeneratorImpl signer;

  @Mock private PrivateKey mockPrivateKey;
  @Mock private X509Certificate mockCertificate;
  @Mock private MeasurementBoundCertificateProvider certificateProvider;
  @Mock private MeasurementBoundCertificate measurementBoundCertificate;

  private static final ByteString TEST_DATA = ByteString.copyFromUtf8("Some data to sign");

  private static final MbsCertificateFactory.CertSignatureSpec RSA_SPEC =
      new MbsCertificateFactory.CertSignatureSpec("RSA", 4096, "SHA256withRSA");
  private static final MbsCertificateFactory.CertSignatureSpec EC_SPEC =
      new MbsCertificateFactory.CertSignatureSpec("EC", 384, "SHA256withECDSA");

  @Before
  public void setUp() {
    Security.addProvider(new BouncyCastleProvider());
    when(certificateProvider.loadOrGenerateCertificate()).thenReturn(measurementBoundCertificate);
    signer = new SignatureGeneratorImpl(certificateProvider);
  }

  @Test
  public void createSignature_withRSAKey_shouldProduceValidSignature() throws Exception {
    MbsCertificateFactory factory =
        MbsCertificateFactory.createSelfSignedCertificatesFactory(
            RSA_SPEC,
            new X500Name("CN=PES"),
            Duration.ofDays(30),
            Optional.empty(),
            KeyUsage.digitalSignature);
    MbsCertificateFactory.X509CertificateAndPrivateKey certAndKey = factory.generate();
    X509Certificate cert = certAndKey.certificate();
    when(measurementBoundCertificate.getCertificate()).thenReturn(cert);
    when(measurementBoundCertificate.getPrivateKey()).thenReturn(certAndKey.privateKey());

    Signature result = signer.generate(TEST_DATA);

    assertThat(result.verificationMaterial().content())
        .isEqualTo(ByteString.copyFrom(cert.getEncoded()));
    assertThat(result.verificationMaterial().format())
        .isEqualTo(VerificationMaterial.Format.X509_DER);
    assertThat(result.signature()).isNotEmpty();
    assertTrue(
        verifySignature(TEST_DATA, result.signature(), cert.getPublicKey(), "SHA256withRSA"));
  }

  @Test
  public void createSignature_withECKey_shouldProduceValidSignature() throws Exception {
    MbsCertificateFactory factory =
        MbsCertificateFactory.createSelfSignedCertificatesFactory(
            EC_SPEC,
            new X500Name("CN=PES"),
            Duration.ofDays(30),
            Optional.empty(),
            KeyUsage.digitalSignature);
    MbsCertificateFactory.X509CertificateAndPrivateKey certAndKey = factory.generate();
    X509Certificate cert = certAndKey.certificate();
    when(measurementBoundCertificate.getCertificate()).thenReturn(cert);
    when(measurementBoundCertificate.getPrivateKey()).thenReturn(certAndKey.privateKey());

    Signature result = signer.generate(TEST_DATA);

    assertThat(result.verificationMaterial().content())
        .isEqualTo(ByteString.copyFrom(cert.getEncoded()));
    assertThat(result.verificationMaterial().format())
        .isEqualTo(VerificationMaterial.Format.X509_DER);
    assertThat(result.signature()).isNotEmpty();
    assertTrue(
        verifySignature(TEST_DATA, result.signature(), cert.getPublicKey(), "SHA256withECDSA"));
  }

  @Test
  public void createSignature_unsupportedKeyAlgorithm_shouldThrowPesSignatureException()
      throws Exception {
    MbsCertificateFactory factory =
        MbsCertificateFactory.createSelfSignedCertificatesFactory(
            RSA_SPEC,
            new X500Name("CN=PES"),
            Duration.ofDays(30),
            Optional.empty(),
            KeyUsage.digitalSignature);
    MbsCertificateFactory.X509CertificateAndPrivateKey certAndKey = factory.generate();
    X509Certificate cert = certAndKey.certificate();
    when(measurementBoundCertificate.getCertificate()).thenReturn(cert);
    when(measurementBoundCertificate.getPrivateKey()).thenReturn(mockPrivateKey);

    when(mockPrivateKey.getAlgorithm()).thenReturn("UNSUPPORTED_ALGO");

    PesSignatureException exception =
        assertThrows(PesSignatureException.class, () -> signer.generate(TEST_DATA));
    assertThat(exception).hasMessageThat().contains("Unsupported key algorithm: UNSUPPORTED_ALGO");
  }

  @Test
  public void createSignature_certificateEncodingFails_shouldThrowPesSignatureException()
      throws Exception {
    KeyPair keyPair = generateKeyPair("RSA", 2048);
    when(mockCertificate.getEncoded())
        .thenThrow(new CertificateEncodingException("Test encoding error"));
    when(measurementBoundCertificate.getCertificate()).thenReturn(mockCertificate);
    when(measurementBoundCertificate.getPrivateKey()).thenReturn(keyPair.getPrivate());

    assertThrows(PesSignatureException.class, () -> signer.generate(TEST_DATA));
  }

  private KeyPair generateKeyPair(String algorithm, int keySize) throws NoSuchAlgorithmException {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm);
    kpg.initialize(keySize);
    return kpg.generateKeyPair();
  }

  private boolean verifySignature(
      ByteString data, ByteString signature, PublicKey publicKey, String algorithm)
      throws Exception {
    java.security.Signature sig = java.security.Signature.getInstance(algorithm);
    sig.initVerify(publicKey);
    sig.update(data.toByteArray());
    return sig.verify(signature.toByteArray());
  }
}
