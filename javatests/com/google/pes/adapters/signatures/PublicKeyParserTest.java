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

import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.InvalidVerificationMaterialException;
import com.google.protobuf.ByteString;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PublicKeyParserTest {
  // A real Base64 encoded P256 EC Public Key in SPKI format
  private static final String EC_P256_SPKI_BASE64 =
      "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEdUDoPds26e40qnyKG/8DR7yjiYFsb0wGlx7ymp7DIVSyBXI2Sdk5SZKD5RoSav9/vZyivFS8o4vP8prVqY4oEw==";
  private static final ByteString VALID_EC_SPKI =
      ByteString.copyFrom(Base64.getDecoder().decode(EC_P256_SPKI_BASE64));

  // A real Base64 encoded X.509 Certificate in DER format
  private static final String X509_DER_BASE64 =
      "MIIBfjCCASOgAwIBAgIUPIHajTDL11Ee7jLoqxLBaPYORB8wCgYIKoZIzj0EAwIwFDESMBAGA1UEAwwJRUNEU0FDZXJ0MB4XDTI2MDMwMzEyMTIyM1oXDTI3MDMwMzEyMTIyM1owFDESMBAGA1UEAwwJRUNEU0FDZXJ0MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEdUDoPds26e40qnyKG/8DR7yjiYFsb0wGlx7ymp7DIVSyBXI2Sdk5SZKD5RoSav9/vZyivFS8o4vP8prVqY4oE6NTMFEwHQYDVR0OBBYEFDYmwPM+9avSl1/Sw3pNnu4yGquuMB8GA1UdIwQYMBaAFDYmwPM+9avSl1/Sw3pNnu4yGquuMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSQAwRgIhAOV2/cwHyKBpjIHOxv4gfy+ZZGar41SULWZWhYwImZvnAiEAlANsPBXyXgA0DTRqgRSJH6IormC6/pYliHD130E+DNU=";
  private static final ByteString VALID_X509_DER =
      ByteString.copyFrom(Base64.getDecoder().decode(X509_DER_BASE64));

  private static final ByteString INVALID_KEY_MATERIAL =
      ByteString.copyFromUtf8("invalid key material");
  private static final ByteString EMPTY_MATERIAL = ByteString.EMPTY;

  @Test
  public void parse_validEcdsaP256Sha256_success() throws Exception {
    VerificationMaterial material =
        new VerificationMaterial(VALID_EC_SPKI, VerificationMaterial.Format.ECDSA_P256_SHA256);
    PublicKey publicKey = PublicKeyParser.parse(material);
    assertThat(publicKey).isNotNull();
    assertThat(publicKey.getAlgorithm()).isEqualTo("EC");

    PublicKey expectedPublicKey =
        KeyFactory.getInstance("EC")
            .generatePublic(new X509EncodedKeySpec(VALID_EC_SPKI.toByteArray()));
    assertThat(publicKey).isEqualTo(expectedPublicKey);
  }

  @Test
  public void parse_validX509Der_success() {
    VerificationMaterial material =
        new VerificationMaterial(VALID_X509_DER, VerificationMaterial.Format.X509_DER);
    PublicKey publicKey = PublicKeyParser.parse(material);
    assertThat(publicKey).isNotNull();

    assertThat(publicKey.getAlgorithm()).isEqualTo("EC");
  }

  @Test
  public void parse_emptyContent_throwsIllegalArgumentException() {
    VerificationMaterial material =
        new VerificationMaterial(EMPTY_MATERIAL, VerificationMaterial.Format.ECDSA_P256_SHA256);
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> PublicKeyParser.parse(material));
    assertThat(exception).hasMessageThat().isEqualTo("The verification material cannot be empty!");
  }

  @Test
  public void parse_unsupportedFormat_throwsIllegalArgumentException() {
    VerificationMaterial material =
        new VerificationMaterial(VALID_EC_SPKI, VerificationMaterial.Format.FORMAT_UNSPECIFIED);
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> PublicKeyParser.parse(material));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("The provided verification material is unsupported!");
  }

  @Test
  public void parse_invalidEcdsaSpki_throwsInvalidVerificationMaterialException() {
    VerificationMaterial material =
        new VerificationMaterial(
            INVALID_KEY_MATERIAL, VerificationMaterial.Format.ECDSA_P256_SHA256);
    InvalidVerificationMaterialException exception =
        assertThrows(
            InvalidVerificationMaterialException.class, () -> PublicKeyParser.parse(material));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Failed to parse EC public key from SPKI format");
  }

  @Test
  public void parse_invalidX509Der_throwsInvalidVerificationMaterialException() {
    VerificationMaterial material =
        new VerificationMaterial(INVALID_KEY_MATERIAL, VerificationMaterial.Format.X509_DER);
    InvalidVerificationMaterialException exception =
        assertThrows(
            InvalidVerificationMaterialException.class, () -> PublicKeyParser.parse(material));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Failed to parse public key from X.509 certificate");
  }
}
