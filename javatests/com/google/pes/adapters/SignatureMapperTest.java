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

import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.TimeStampToken;
import com.google.pes.v1.VerificationMaterial;
import com.google.pes.v1.VerificationMaterial.VerificationMaterialCase;
import com.google.pes.v1.X509Der;
import com.google.protobuf.ByteString;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SignatureMapperTest {

  private static final ByteString SIGNATURE_BYTES = ByteString.copyFromUtf8("test-signature");
  private static final ByteString CERT_BYTES = ByteString.copyFromUtf8("test-certificate");
  private static final ByteString TOKEN_BYTES = ByteString.copyFromUtf8("test-token-bytes");

  @Test
  public void toDomain_withoutTimeStampToken_mapsCorrectly() {
    com.google.pes.v1.Signature protoSignature =
        com.google.pes.v1.Signature.newBuilder()
            .setSignature(SIGNATURE_BYTES)
            .setVerificationMaterial(
                VerificationMaterial.newBuilder()
                    .setX509Certificate(X509Der.newBuilder().setDerBytes(CERT_BYTES)))
            .build();

    Signature domainSignature = SignatureMapper.toDomain(protoSignature);

    assertThat(domainSignature.signature()).isEqualTo(SIGNATURE_BYTES);
    assertThat(domainSignature.verificationMaterial().content()).isEqualTo(CERT_BYTES);
    assertThat(domainSignature.verificationMaterial().format())
        .isEqualTo(com.google.pes.domain.model.VerificationMaterial.Format.X509_DER);
    assertThat(domainSignature.timeStampToken()).isEmpty();
  }

  @Test
  public void toDomain_withTimeStampToken_mapsCorrectly() {
    com.google.pes.v1.Signature protoSignature =
        com.google.pes.v1.Signature.newBuilder()
            .setSignature(SIGNATURE_BYTES)
            .setVerificationMaterial(
                VerificationMaterial.newBuilder()
                    .setX509Certificate(X509Der.newBuilder().setDerBytes(CERT_BYTES)))
            .setTimeStampToken(
                com.google.pes.v1.TimeStampToken.newBuilder().setDerBytes(TOKEN_BYTES))
            .build();

    Signature domainSignature = SignatureMapper.toDomain(protoSignature);

    assertThat(domainSignature.signature()).isEqualTo(SIGNATURE_BYTES);
    assertThat(domainSignature.verificationMaterial().content()).isEqualTo(CERT_BYTES);
    assertThat(domainSignature.verificationMaterial().format())
        .isEqualTo(com.google.pes.domain.model.VerificationMaterial.Format.X509_DER);
    assertThat(domainSignature.timeStampToken()).isPresent();
    assertThat(domainSignature.timeStampToken().get().derBytes()).isEqualTo(TOKEN_BYTES);
  }

  @Test
  public void toDomain_whenVerificationMaterialNotSet_mapsCorrectly() {
    com.google.pes.v1.Signature protoSignature =
        com.google.pes.v1.Signature.newBuilder().setSignature(SIGNATURE_BYTES).build();

    Signature domainSignature = SignatureMapper.toDomain(protoSignature);

    assertThat(domainSignature.signature()).isEqualTo(SIGNATURE_BYTES);
    assertThat(domainSignature.verificationMaterial().content()).isEqualTo(ByteString.EMPTY);
    assertThat(domainSignature.verificationMaterial().format())
        .isEqualTo(com.google.pes.domain.model.VerificationMaterial.Format.FORMAT_UNSPECIFIED);
    assertThat(domainSignature.timeStampToken()).isEmpty();
  }

  @Test
  public void toProto_withoutTimeStampToken_mapsCorrectly() {
    Signature domainSignature =
        new Signature(
            SIGNATURE_BYTES,
            new com.google.pes.domain.model.VerificationMaterial(
                CERT_BYTES, com.google.pes.domain.model.VerificationMaterial.Format.X509_DER),
            Optional.empty());

    com.google.pes.v1.Signature protoSignature = SignatureMapper.toProto(domainSignature);

    assertThat(protoSignature.getSignature()).isEqualTo(SIGNATURE_BYTES);
    assertThat(protoSignature.getVerificationMaterial().getVerificationMaterialCase())
        .isEqualTo(VerificationMaterialCase.X509_CERTIFICATE);
    assertThat(protoSignature.getVerificationMaterial().getX509Certificate().getDerBytes())
        .isEqualTo(CERT_BYTES);
    assertThat(protoSignature.hasTimeStampToken()).isFalse();
  }

  @Test
  public void toProto_withTimeStampToken_mapsCorrectly() {
    Signature domainSignature =
        new Signature(
            SIGNATURE_BYTES,
            new com.google.pes.domain.model.VerificationMaterial(
                CERT_BYTES, com.google.pes.domain.model.VerificationMaterial.Format.X509_DER),
            Optional.of(new TimeStampToken(TOKEN_BYTES)));

    com.google.pes.v1.Signature protoSignature = SignatureMapper.toProto(domainSignature);

    assertThat(protoSignature.getSignature()).isEqualTo(SIGNATURE_BYTES);
    assertThat(protoSignature.getVerificationMaterial().getVerificationMaterialCase())
        .isEqualTo(VerificationMaterialCase.X509_CERTIFICATE);
    assertThat(protoSignature.getVerificationMaterial().getX509Certificate().getDerBytes())
        .isEqualTo(CERT_BYTES);
    assertThat(protoSignature.hasTimeStampToken()).isTrue();
    assertThat(protoSignature.getTimeStampToken().getDerBytes()).isEqualTo(TOKEN_BYTES);
  }

  @Test
  public void toProto_whenUnspecifiedMethod_mapsCorrectly() {
    Signature domainSignature =
        new Signature(
            SIGNATURE_BYTES,
            new com.google.pes.domain.model.VerificationMaterial(
                ByteString.EMPTY,
                com.google.pes.domain.model.VerificationMaterial.Format.FORMAT_UNSPECIFIED),
            Optional.empty());

    com.google.pes.v1.Signature protoSignature = SignatureMapper.toProto(domainSignature);

    assertThat(protoSignature.getSignature()).isEqualTo(SIGNATURE_BYTES);
    assertThat(protoSignature.getVerificationMaterial().getVerificationMaterialCase())
        .isEqualTo(VerificationMaterialCase.VERIFICATIONMATERIAL_NOT_SET);
    assertThat(protoSignature.hasTimeStampToken()).isFalse();
  }
}
