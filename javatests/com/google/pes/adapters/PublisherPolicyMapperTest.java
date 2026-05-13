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

import com.google.pes.domain.model.PublisherPolicy;
import com.google.protobuf.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PublisherPolicyMapperTest {

  @Test
  public void toDomain_mapsFieldsCorrectly() {
    com.google.pes.policy.v1.PublisherPolicy proto =
        com.google.pes.policy.v1.PublisherPolicy.newBuilder()
            .setPublisherId("pub1")
            .addOidcTokenClaims(
                com.google.pes.policy.v1.OidcTokenClaims.newBuilder()
                    .setIssuer("iss1")
                    .setSubject("sub1")
                    .build())
            .addOidcTokenClaims(
                com.google.pes.policy.v1.OidcTokenClaims.newBuilder()
                    .setIssuer("iss2")
                    .setSubject("sub2")
                    .build())
            .addVerificationMaterial(
                com.google.pes.v1.VerificationMaterial.newBuilder()
                    .setEcdsaP256Sha256(
                        com.google.pes.v1.EcdsaP256PublicKey.newBuilder()
                            .setDerBytes(ByteString.copyFromUtf8("material1"))
                            .build())
                    .build())
            .build();

    PublisherPolicy domain = PublisherPolicyMapper.toDomain(proto);

    assertThat(domain.publisherId()).isEqualTo("pub1");
    assertThat(domain.oidcTokenClaims()).hasSize(2);
    assertThat(domain.oidcTokenClaims().get(0).issuer()).isEqualTo("iss1");
    assertThat(domain.oidcTokenClaims().get(0).subject()).isEqualTo("sub1");
    assertThat(domain.oidcTokenClaims().get(1).issuer()).isEqualTo("iss2");
    assertThat(domain.oidcTokenClaims().get(1).subject()).isEqualTo("sub2");
    assertThat(domain.verificationMaterialList()).hasSize(1);
    assertThat(domain.verificationMaterialList().get(0).content().toStringUtf8())
        .isEqualTo("material1");
  }
}
