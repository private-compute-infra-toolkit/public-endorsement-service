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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.mbs.qualifier.MbsRoot;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.PesSignatureException;
import com.google.pes.domain.ports.SignatureGenerator;
import com.google.protobuf.ByteString;
import jakarta.inject.Inject;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Optional;

public class SignatureGeneratorImpl implements SignatureGenerator {
  private final X509Certificate certificate;
  private final PrivateKey privateKey;

  @Inject
  SignatureGeneratorImpl(@MbsRoot X509Certificate certificate, @MbsRoot PrivateKey privateKey) {
    this.certificate = checkNotNull(certificate, "Certificate cannot be null.");
    this.privateKey = checkNotNull(privateKey, "PrivateKey cannot be null.");
  }

  @Override
  public Signature generate(ByteString data) {
    byte[] certificateDer = getCertificateBytes(certificate);

    String algorithm = getSigningAlgorithm(privateKey.getAlgorithm());
    byte[] signatureBytes = sign(data, privateKey, algorithm);

    return new Signature(
        ByteString.copyFrom(signatureBytes),
        new VerificationMaterial(
            ByteString.copyFrom(certificateDer), VerificationMaterial.Format.X509_DER),
        Optional.empty());
  }

  private byte[] getCertificateBytes(X509Certificate certificate) {
    try {
      return certificate.getEncoded();
    } catch (CertificateEncodingException e) {
      throw new PesSignatureException("Failed to retrieve DER encoded certificate", e);
    }
  }

  private byte[] sign(ByteString data, PrivateKey privateKey, String algorithm) {
    try {
      java.security.Signature signer = java.security.Signature.getInstance(algorithm);

      signer.initSign(privateKey);
      signer.update(data.toByteArray());
      return signer.sign();

    } catch (GeneralSecurityException e) {
      // Catches NoSuchAlgorithm, InvalidKey, SignatureException
      throw new PesSignatureException("Signing operation failed", e);
    }
  }

  private String getSigningAlgorithm(String keyAlgorithm) {
    return switch (keyAlgorithm) {
      case "RSA" -> "SHA256withRSA";
      case "EC" -> "SHA256withECDSA";
      case "DSA" -> "SHA256withDSA";
      default -> throw new PesSignatureException("Unsupported key algorithm: " + keyAlgorithm);
    };
  }
}
