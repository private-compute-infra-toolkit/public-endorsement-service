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

import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.v1.EcdsaP256PublicKey;
import com.google.pes.v1.X509Der;
import com.google.protobuf.ByteString;

/**
 * Maps between domain {@link VerificationMaterial} and proto {@link
 * com.google.pes.v1.VerificationMaterial}.
 */
public final class VerificationMaterialMapper {

  private VerificationMaterialMapper() {}

  public static VerificationMaterial toDomain(com.google.pes.v1.VerificationMaterial proto) {
    ByteString content = ByteString.EMPTY;
    VerificationMaterial.Format format = VerificationMaterial.Format.FORMAT_UNSPECIFIED;

    switch (proto.getVerificationMaterialCase()) {
      case X509_CERTIFICATE -> {
        content = proto.getX509Certificate().getDerBytes();
        format = VerificationMaterial.Format.X509_DER;
      }
      case ECDSA_P256_SHA256 -> {
        content = proto.getEcdsaP256Sha256().getDerBytes();
        format = VerificationMaterial.Format.ECDSA_P256_SHA256;
      }
      case VERIFICATIONMATERIAL_NOT_SET -> {
        format = VerificationMaterial.Format.FORMAT_UNSPECIFIED;
      }
    }

    return new VerificationMaterial(content, format);
  }

  public static com.google.pes.v1.VerificationMaterial toProto(VerificationMaterial domain) {
    com.google.pes.v1.VerificationMaterial.Builder builder =
        com.google.pes.v1.VerificationMaterial.newBuilder();

    switch (domain.format()) {
      case X509_DER ->
          builder.setX509Certificate(X509Der.newBuilder().setDerBytes(domain.content()).build());
      case ECDSA_P256_SHA256 ->
          builder.setEcdsaP256Sha256(
              EcdsaP256PublicKey.newBuilder().setDerBytes(domain.content()).build());
      case FORMAT_UNSPECIFIED -> {}
    }

    return builder.build();
  }
}
