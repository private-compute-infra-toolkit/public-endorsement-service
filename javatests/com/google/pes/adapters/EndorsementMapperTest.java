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

import com.google.common.collect.ImmutableList;
import com.google.pes.domain.model.Endorsement;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.model.TLogReceipt;
import com.google.pes.v1.PublicEndorsement;
import com.google.pes.v1.Statement.Format;
import com.google.pes.v1.VerificationMaterial;
import com.google.pes.v1.X509Der;
import com.google.protobuf.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EndorsementMapperTest {

  private static final String ENDORSEMENT_NAME = "test-endorsement-name";
  private static final ByteString STATEMENT_BYTES = ByteString.copyFromUtf8("statement content");
  private static final ByteString SIG_BYTES = ByteString.copyFromUtf8("sig");
  private static final ByteString CERT_BYTES = ByteString.copyFromUtf8("cert");

  private static final String TLOG_ENTRY_ID = "entries/tlog-id";

  private static final com.google.pes.v1.Statement PROTO_STATEMENT =
      com.google.pes.v1.Statement.newBuilder()
          .setFormat(Format.JSON_INTOTO)
          .setSerialized(STATEMENT_BYTES)
          .build();

  private static final com.google.pes.v1.Signature PROTO_SIGNATURE =
      com.google.pes.v1.Signature.newBuilder()
          .setSignature(SIG_BYTES)
          .setVerificationMaterial(
              VerificationMaterial.newBuilder()
                  .setX509Certificate(X509Der.newBuilder().setDerBytes(CERT_BYTES)))
          .build();

  private static final com.google.pes.v1.TLogReceipt PROTO_TLOG_RECEIPT =
      com.google.pes.v1.TLogReceipt.newBuilder().setEntryId(TLOG_ENTRY_ID).build();

  private static final Statement DOMAIN_STATEMENT =
      new Statement(Statement.Format.JSON_INTOTO, STATEMENT_BYTES);

  private static final Signature DOMAIN_SIGNATURE =
      new Signature(
          SIG_BYTES,
          new com.google.pes.domain.model.VerificationMaterial(
              CERT_BYTES, com.google.pes.domain.model.VerificationMaterial.Format.X509_DER));

  private static final TLogReceipt DOMAIN_TLOG_RECEIPT = new TLogReceipt(TLOG_ENTRY_ID);

  @Test
  public void toDomain_mapsCorrectly() {
    PublicEndorsement protoEndorsement =
        PublicEndorsement.newBuilder()
            .setName(ENDORSEMENT_NAME)
            .setStatement(PROTO_STATEMENT)
            .setStatementSignature(PROTO_SIGNATURE)
            .addAllEndorsementSignatures(ImmutableList.of(PROTO_SIGNATURE, PROTO_SIGNATURE))
            .setTlogReceipt(PROTO_TLOG_RECEIPT)
            .build();

    Endorsement domainEndorsement = EndorsementMapper.toDomain(protoEndorsement);

    assertThat(domainEndorsement.name()).isEqualTo(ENDORSEMENT_NAME);
    assertThat(domainEndorsement.statement()).isEqualTo(DOMAIN_STATEMENT);
    assertThat(domainEndorsement.statementSignature()).isEqualTo(DOMAIN_SIGNATURE);
    assertThat(domainEndorsement.endorsementSignatures())
        .containsExactly(DOMAIN_SIGNATURE, DOMAIN_SIGNATURE)
        .inOrder();
    assertThat(domainEndorsement.tLogReceipt()).isEqualTo(DOMAIN_TLOG_RECEIPT);
  }

  @Test
  public void toProto_mapsCorrectly() {
    Endorsement domainEndorsement =
        new Endorsement(
            ENDORSEMENT_NAME,
            DOMAIN_STATEMENT,
            DOMAIN_SIGNATURE,
            ImmutableList.of(DOMAIN_SIGNATURE, DOMAIN_SIGNATURE),
            DOMAIN_TLOG_RECEIPT);

    PublicEndorsement protoEndorsement = EndorsementMapper.toProto(domainEndorsement);

    PublicEndorsement expectedProtoEndorsement =
        PublicEndorsement.newBuilder()
            .setName(ENDORSEMENT_NAME)
            .setStatement(PROTO_STATEMENT)
            .setStatementSignature(PROTO_SIGNATURE)
            .addAllEndorsementSignatures(ImmutableList.of(PROTO_SIGNATURE, PROTO_SIGNATURE))
            .setTlogReceipt(PROTO_TLOG_RECEIPT)
            .build();

    assertThat(protoEndorsement).isEqualTo(expectedProtoEndorsement);
  }
}
