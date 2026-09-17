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

import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.TimeStampToken;
import java.util.Optional;

/** Maps between domain {@link Signature} and proto {@link com.google.pes.v1.Signature}. */
public final class SignatureMapper {

  private SignatureMapper() {}

  public static Signature toDomain(com.google.pes.v1.Signature proto) {
    Optional<TimeStampToken> timeStampToken =
        proto.hasTimeStampToken()
            ? Optional.of(new TimeStampToken(proto.getTimeStampToken().getDerBytes()))
            : Optional.empty();

    return new Signature(
        proto.getSignature(),
        VerificationMaterialMapper.toDomain(proto.getVerificationMaterial()),
        timeStampToken);
  }

  public static com.google.pes.v1.Signature toProto(Signature domain) {
    com.google.pes.v1.Signature.Builder builder =
        com.google.pes.v1.Signature.newBuilder()
            .setSignature(domain.signature())
            .setVerificationMaterial(
                VerificationMaterialMapper.toProto(domain.verificationMaterial()));

    domain
        .timeStampToken()
        .map(token -> com.google.pes.v1.TimeStampToken.newBuilder().setDerBytes(token.derBytes()))
        .ifPresent(builder::setTimeStampToken);

    return builder.build();
  }
}
