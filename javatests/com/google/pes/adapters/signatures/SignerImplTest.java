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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.mbs.MbsCertificateFactory;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.TimeStampToken;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.PesSignatureException;
import com.google.pes.domain.ports.TsaClient;
import com.google.pes.domain.ports.TsaException;
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
  @Mock private TsaClient tsaClient;

  private static final TimeStampToken MOCK_TOKEN =
      new TimeStampToken(ByteString.copyFrom(new byte[] {4, 5, 6}));
  private static final ByteString TEST_DATA = ByteString.copyFromUtf8("Some data to sign");

  private static final MbsCertificateFactory.CertSignatureSpec RSA_SPEC =
      new MbsCertificateFactory.CertSignatureSpec("RSA", 4096, "SHA256withRSA");

  private static final MbsCertificateFactory.CertSignatureSpec EC_SPEC =
      new MbsCertificateFactory.CertSignatureSpec("EC", 384, "SHA256withECDSA");

  @Before
  public void setUp() throws Exception {
    Security.addProvider(new BouncyCastleProvider());
    when(tsaClient.requestTimeStampToken(any(ByteString.class))).thenReturn(Optional.empty());
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
    signer = new SignatureGeneratorImpl(tsaClient);

    Signature result = signer.generate(TEST_DATA, cert, certAndKey.privateKey());

    assertThat(result.verificationMaterial().content())
        .isEqualTo(ByteString.copyFrom(cert.getEncoded()));
    assertThat(result.verificationMaterial().format())
        .isEqualTo(VerificationMaterial.Format.X509_DER);
    assertThat(result.signature()).isNotEmpty();
    assertThat(result.timeStampToken()).isEmpty();
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
    signer = new SignatureGeneratorImpl(tsaClient);

    Signature result = signer.generate(TEST_DATA, cert, certAndKey.privateKey());

    assertThat(result.verificationMaterial().content())
        .isEqualTo(ByteString.copyFrom(cert.getEncoded()));
    assertThat(result.verificationMaterial().format())
        .isEqualTo(VerificationMaterial.Format.X509_DER);
    assertThat(result.signature()).isNotEmpty();
    assertThat(result.timeStampToken()).isEmpty();
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

    when(mockPrivateKey.getAlgorithm()).thenReturn("UNSUPPORTED_ALGO");
    signer = new SignatureGeneratorImpl(tsaClient);

    PesSignatureException exception =
        assertThrows(
            PesSignatureException.class, () -> signer.generate(TEST_DATA, cert, mockPrivateKey));
    assertThat(exception).hasMessageThat().contains("Unsupported key algorithm: UNSUPPORTED_ALGO");
  }

  @Test
  public void createSignature_certificateEncodingFails_shouldThrowPesSignatureException()
      throws Exception {
    KeyPair keyPair = generateKeyPair("RSA", 2048);
    when(mockCertificate.getEncoded())
        .thenThrow(new CertificateEncodingException("Test encoding error"));
    signer = new SignatureGeneratorImpl(tsaClient);

    assertThrows(
        PesSignatureException.class,
        () -> signer.generate(TEST_DATA, mockCertificate, keyPair.getPrivate()));
  }

  @Test
  public void generate_tsaEnabled_returnsSignatureWithTimeStampToken() throws Exception {
    KeyPair keyPair = generateKeyPair("RSA", 2048);
    when(mockCertificate.getEncoded()).thenReturn(new byte[] {1, 2, 3});

    java.security.Signature rsaSigner = java.security.Signature.getInstance("SHA256withRSA");
    rsaSigner.initSign(keyPair.getPrivate());
    rsaSigner.update(TEST_DATA.toByteArray());
    ByteString expectedSignature = ByteString.copyFrom(rsaSigner.sign());

    when(tsaClient.requestTimeStampToken(expectedSignature)).thenReturn(Optional.of(MOCK_TOKEN));
    signer = new SignatureGeneratorImpl(tsaClient);

    Signature result = signer.generate(TEST_DATA, mockCertificate, keyPair.getPrivate());

    assertThat(result.signature()).isEqualTo(expectedSignature);
    assertThat(result.timeStampToken()).hasValue(MOCK_TOKEN);
    verify(tsaClient).requestTimeStampToken(expectedSignature);
  }

  @Test
  public void generate_tsaDisabled_returnsSignatureWithoutTimeStampToken() throws Exception {
    KeyPair keyPair = generateKeyPair("RSA", 2048);
    when(mockCertificate.getEncoded()).thenReturn(new byte[] {1, 2, 3});

    when(tsaClient.requestTimeStampToken(any(ByteString.class))).thenReturn(Optional.empty());
    signer = new SignatureGeneratorImpl(tsaClient);

    Signature result = signer.generate(TEST_DATA, mockCertificate, keyPair.getPrivate());

    assertThat(result.timeStampToken()).isEmpty();
    verify(tsaClient).requestTimeStampToken(result.signature());
  }

  @Test
  public void generate_tsaClientFails_returnsSignatureWithoutTimeStampToken() throws Exception {
    KeyPair keyPair = generateKeyPair("RSA", 2048);
    when(mockCertificate.getEncoded()).thenReturn(new byte[] {1, 2, 3});

    when(tsaClient.requestTimeStampToken(any(ByteString.class)))
        .thenThrow(new TsaException("TSA throws in this test case"));
    signer = new SignatureGeneratorImpl(tsaClient);

    Signature result = signer.generate(TEST_DATA, mockCertificate, keyPair.getPrivate());

    assertThat(result.timeStampToken()).isEmpty();
    verify(tsaClient).requestTimeStampToken(result.signature());
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
