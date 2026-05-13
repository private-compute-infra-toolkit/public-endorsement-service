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

import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.InvalidVerificationMaterialException;
import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

/** Provides utility methods to extract a {@link PublicKey} from {@link VerificationMaterial}. */
public class PublicKeyParser {

  /**
   * Extracts and returns a {@link PublicKey} from the given {@link VerificationMaterial}.
   *
   * <p>The method supports different extraction logic based on the {@link
   * VerificationMaterial.Format}.
   *
   * @param verificationMaterial The verification material containing the key content and format.
   * @return The extracted {@link PublicKey}.
   * @throws IllegalArgumentException if the verification material content is empty or the format is
   *     unsupported.
   * @throws InvalidVerificationMaterialException if the key material is invalid or cannot be
   *     parsed.
   */
  public static PublicKey parse(VerificationMaterial verificationMaterial) {
    if (verificationMaterial.content().isEmpty()) {
      throw new IllegalArgumentException("The verification material cannot be empty!");
    }

    return switch (verificationMaterial.format()) {
      case ECDSA_P256_SHA256 -> {
        try {
          yield parseFromEcSpki(verificationMaterial.content().toByteArray());
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
          throw new InvalidVerificationMaterialException(
              "Failed to parse EC public key from SPKI format", e);
        }
      }
      case X509_DER -> {
        try {
          yield parseFromCertificate(verificationMaterial.content().toByteArray());
        } catch (CertificateException e) {
          throw new InvalidVerificationMaterialException(
              "Failed to parse public key from X.509 certificate", e);
        }
      }
      default ->
          throw new IllegalArgumentException("The provided verification material is unsupported!");
    };
  }

  private static PublicKey parseFromEcSpki(byte[] spkiBytes)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    X509EncodedKeySpec spec = new X509EncodedKeySpec(spkiBytes);

    KeyFactory kf = KeyFactory.getInstance("EC");
    return kf.generatePublic(spec);
  }

  private static PublicKey parseFromCertificate(byte[] certBytes) throws CertificateException {
    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
    X509Certificate cert =
        (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
    return cert.getPublicKey();
  }
}
