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

import com.google.pes.domain.model.Endorsement;
import com.google.pes.v1.PublicEndorsement;
import java.util.stream.Collectors;

/** Maps between domain {@link Endorsement} and proto {@link PublicEndorsement}. */
public final class EndorsementMapper {

  private EndorsementMapper() {}

  public static Endorsement toDomain(PublicEndorsement proto) {
    return new Endorsement(
        proto.getName(),
        StatementMapper.toDomain(proto.getStatement()),
        SignatureMapper.toDomain(proto.getStatementSignature()),
        proto.getEndorsementSignaturesList().stream()
            .map(SignatureMapper::toDomain)
            .collect(Collectors.toList()),
        TLogReceiptMapper.toDomain(proto.getTlogReceipt()));
  }

  public static PublicEndorsement toProto(Endorsement domain) {
    return PublicEndorsement.newBuilder()
        .setName(domain.name())
        .setStatement(StatementMapper.toProto(domain.statement()))
        .setStatementSignature(SignatureMapper.toProto(domain.statementSignature()))
        .addAllEndorsementSignatures(
            domain.endorsementSignatures().stream()
                .map(SignatureMapper::toProto)
                .collect(Collectors.toList()))
        .setTlogReceipt(TLogReceiptMapper.toProto(domain.tLogReceipt()))
        .build();
  }
}
