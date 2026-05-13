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

package com.google.pes.domain;

import com.google.pes.domain.model.PublisherPolicy;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.ports.PolicyProvider;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.Optional;

public class PublisherVerifier {
  private final PolicyProvider policyProvider;

  @Inject
  PublisherVerifier(PolicyProvider policyProvider) {
    this.policyProvider = policyProvider;
  }

  /**
   * Verifies that the provided parameters match the stored policy.
   *
   * @param publisherId The ID of the publisher to verify.
   * @param identity The OIDC identity of the client.
   * @param signature The signature object containing verification material.
   * @throws IllegalArgumentException if the policy is missing or values do not match.
   */
  public void verify(String publisherId, CallerIdentity identity, Signature signature) {
    String escapedPublisherId = CallerIdentity.escape(publisherId);
    Optional<PublisherPolicy> policyOpt = policyProvider.get(escapedPublisherId);

    if (policyOpt.isEmpty()) {
      throw new IllegalArgumentException(
          "No policy found for Publisher ID: "
              + publisherId
              + " escaped to: "
              + escapedPublisherId);
    }

    PublisherPolicy policy = policyOpt.get();
    boolean isOidcTokenMatchingPolicy =
        policy.oidcTokenClaims().stream()
            .anyMatch(
                claims ->
                    Objects.equals(identity.issuer(), claims.issuer())
                        && Objects.equals(identity.subject(), claims.subject()));

    if (!isOidcTokenMatchingPolicy) {
      throw new IllegalArgumentException(
          String.format(
              "Client OIDC identity mismatch. No matching claims found for Issuer: %s, Subject: %s",
              identity.issuer(), identity.subject()));
    }

    if (!Objects.equals(publisherId, policy.publisherId())) {
      throw new IllegalArgumentException(
          String.format(
              "Publisher ID mismatch. Expected: %s, Got: %s", policy.publisherId(), publisherId));
    }

    if (!policy.verificationMaterialList().contains(signature.verificationMaterial())) {
      throw new IllegalArgumentException(
          "The provided verification material is not authorized for this client role.");
    }
  }
}
