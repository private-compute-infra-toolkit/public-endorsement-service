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

import com.google.pes.domain.model.Signature;
import com.google.pes.domain.ports.InvalidSignatureException;
import com.google.pes.domain.ports.SignatureVerifier;
import com.google.protobuf.ByteString;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;

public class SignatureVerifierImpl implements SignatureVerifier {
  /**
   * Verifies a signature assuming SHA-256 hashing. Auto-detects Key Algorithm (RSA vs EC) from the
   * PublicKey.
   */
  @Override
  public void verify(Signature signature, ByteString signedData) {
    PublicKey publicKey = PublicKeyParser.parse(signature.verificationMaterial());
    try {
      if (!verifySignature(signedData, signature.signature(), publicKey)) {
        throw new InvalidSignatureException("The signature is invalid!");
      }
    } catch (SignatureException e) {
      throw new InvalidSignatureException("The signature verification failed!", e);
    } catch (InvalidKeyException e) {
      throw new InvalidSignatureException("Provided key is invalid!", e);
    }
  }

  private boolean verifySignature(ByteString data, ByteString signature, PublicKey publicKey)
      throws SignatureException, InvalidKeyException {

    String signingAlgorithm = deduceAlgorithm(publicKey);

    java.security.Signature verifier;
    try {
      verifier = java.security.Signature.getInstance(signingAlgorithm);
    } catch (NoSuchAlgorithmException impossible) {
      // The algorithm is chosen from a list of known algorithms. It has to exist.
      throw new AssertionError(impossible);
    }

    verifier.initVerify(publicKey);
    verifier.update(data.toByteArray());

    return verifier.verify(signature.toByteArray());
  }

  private static String deduceAlgorithm(PublicKey publicKey) {
    String keyAlgorithm = publicKey.getAlgorithm();

    // Standard JCA names returned by the getAlgorithm()
    return switch (keyAlgorithm) {
      case "RSA" -> "SHA256withRSA";
      case "EC" -> "SHA256withECDSA";
      default -> throw new IllegalArgumentException("Unsupported Public Key Type: " + keyAlgorithm);
    };
  }
}
