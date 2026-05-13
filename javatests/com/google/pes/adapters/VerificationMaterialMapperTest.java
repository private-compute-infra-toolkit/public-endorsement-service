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

package com.google.pes.adapters;

import static com.google.common.truth.Truth.assertThat;

import com.google.pes.v1.EcdsaP256PublicKey;
import com.google.pes.v1.VerificationMaterial;
import com.google.pes.v1.VerificationMaterial.VerificationMaterialCase;
import com.google.pes.v1.X509Der;
import com.google.protobuf.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VerificationMaterialMapperTest {

  private static final ByteString CERT_BYTES = ByteString.copyFromUtf8("test-certificate");
  private static final ByteString PUBLIC_KEY_BYTES = ByteString.copyFromUtf8("test-public-key");

  @Test
  public void toDomain_whenX509Certificate_mapsCorrectly() {
    VerificationMaterial proto =
        VerificationMaterial.newBuilder()
            .setX509Certificate(X509Der.newBuilder().setDerBytes(CERT_BYTES))
            .build();

    com.google.pes.domain.model.VerificationMaterial domain =
        VerificationMaterialMapper.toDomain(proto);

    assertThat(domain.content()).isEqualTo(CERT_BYTES);
    assertThat(domain.format())
        .isEqualTo(com.google.pes.domain.model.VerificationMaterial.Format.X509_DER);
  }

  @Test
  public void toDomain_whenECDSA_P256_mapsCorrectly() {
    VerificationMaterial proto =
        VerificationMaterial.newBuilder()
            .setEcdsaP256Sha256(EcdsaP256PublicKey.newBuilder().setDerBytes(PUBLIC_KEY_BYTES))
            .build();

    com.google.pes.domain.model.VerificationMaterial domain =
        VerificationMaterialMapper.toDomain(proto);

    assertThat(domain.content()).isEqualTo(PUBLIC_KEY_BYTES);
    assertThat(domain.format())
        .isEqualTo(com.google.pes.domain.model.VerificationMaterial.Format.ECDSA_P256_SHA256);
  }

  @Test
  public void toDomain_whenNotSet_mapsCorrectly() {
    VerificationMaterial proto = VerificationMaterial.getDefaultInstance();

    com.google.pes.domain.model.VerificationMaterial domain =
        VerificationMaterialMapper.toDomain(proto);

    assertThat(domain.content()).isEqualTo(ByteString.EMPTY);
    assertThat(domain.format())
        .isEqualTo(com.google.pes.domain.model.VerificationMaterial.Format.FORMAT_UNSPECIFIED);
  }

  @Test
  public void toProto_whenX509Der_mapsCorrectly() {
    com.google.pes.domain.model.VerificationMaterial domain =
        new com.google.pes.domain.model.VerificationMaterial(
            CERT_BYTES, com.google.pes.domain.model.VerificationMaterial.Format.X509_DER);

    VerificationMaterial proto = VerificationMaterialMapper.toProto(domain);

    assertThat(proto.getVerificationMaterialCase())
        .isEqualTo(VerificationMaterialCase.X509_CERTIFICATE);
    assertThat(proto.getX509Certificate().getDerBytes()).isEqualTo(CERT_BYTES);
  }

  @Test
  public void toProto_whenECDSA_P256_mapsCorrectly() {
    com.google.pes.domain.model.VerificationMaterial domain =
        new com.google.pes.domain.model.VerificationMaterial(
            PUBLIC_KEY_BYTES,
            com.google.pes.domain.model.VerificationMaterial.Format.ECDSA_P256_SHA256);

    VerificationMaterial proto = VerificationMaterialMapper.toProto(domain);

    assertThat(proto.getVerificationMaterialCase())
        .isEqualTo(VerificationMaterialCase.ECDSA_P256_SHA256);
    assertThat(proto.getEcdsaP256Sha256().getDerBytes()).isEqualTo(PUBLIC_KEY_BYTES);
  }

  @Test
  public void toProto_whenUnspecified_mapsCorrectly() {
    com.google.pes.domain.model.VerificationMaterial domain =
        new com.google.pes.domain.model.VerificationMaterial(
            ByteString.EMPTY,
            com.google.pes.domain.model.VerificationMaterial.Format.FORMAT_UNSPECIFIED);

    VerificationMaterial proto = VerificationMaterialMapper.toProto(domain);

    assertThat(proto.getVerificationMaterialCase())
        .isEqualTo(VerificationMaterialCase.VERIFICATIONMATERIAL_NOT_SET);
  }
}
