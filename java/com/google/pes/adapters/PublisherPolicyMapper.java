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

import com.google.pes.domain.model.PublisherPolicy;
import com.google.pes.domain.model.PublisherPolicy.OidcTokenClaims;

/** Maps {@link com.google.pes.policy.v1.PublisherPolicy} to domain {@link PublisherPolicy}. */
public final class PublisherPolicyMapper {

  private PublisherPolicyMapper() {}

  public static PublisherPolicy toDomain(com.google.pes.policy.v1.PublisherPolicy proto) {
    return new PublisherPolicy(
        proto.getPublisherId(),
        proto.getOidcTokenClaimsList().stream()
            .map(claims -> new OidcTokenClaims(claims.getIssuer(), claims.getSubject()))
            .toList(),
        proto.getVerificationMaterialList().stream()
            .map(VerificationMaterialMapper::toDomain)
            .toList());
  }
}
