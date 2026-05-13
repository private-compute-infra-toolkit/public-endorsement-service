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

import com.google.pes.domain.model.Statement;
import com.google.pes.v1.Statement.Format;
import com.google.protobuf.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StatementMapperTest {

  private static final ByteString SERIALIZED_DATA = ByteString.copyFromUtf8("test statement data");

  @Test
  public void toDomain_whenJsonIntoto_mapsCorrectly() {
    com.google.pes.v1.Statement protoStatement =
        com.google.pes.v1.Statement.newBuilder()
            .setFormat(Format.JSON_INTOTO)
            .setSerialized(SERIALIZED_DATA)
            .build();

    Statement domainStatement = StatementMapper.toDomain(protoStatement);

    assertThat(domainStatement.format()).isEqualTo(Statement.Format.JSON_INTOTO);
    assertThat(domainStatement.serialized()).isEqualTo(SERIALIZED_DATA);
  }

  @Test
  public void toDomain_whenUnspecified_mapsCorrectly() {
    com.google.pes.v1.Statement protoStatement =
        com.google.pes.v1.Statement.newBuilder()
            .setFormat(Format.FORMAT_UNSPECIFIED)
            .setSerialized(SERIALIZED_DATA)
            .build();

    Statement domainStatement = StatementMapper.toDomain(protoStatement);

    assertThat(domainStatement.format()).isEqualTo(Statement.Format.FORMAT_UNSPECIFIED);
    assertThat(domainStatement.serialized()).isEqualTo(SERIALIZED_DATA);
  }

  @Test
  public void toDomain_defaultInstance_mapsCorrectly() {
    com.google.pes.v1.Statement protoStatement = com.google.pes.v1.Statement.getDefaultInstance();
    Statement domainStatement = StatementMapper.toDomain(protoStatement);

    assertThat(domainStatement.format()).isEqualTo(Statement.Format.FORMAT_UNSPECIFIED);
    assertThat(domainStatement.serialized()).isEqualTo(ByteString.EMPTY);
  }

  @Test
  public void toProto_whenJsonIntoto_mapsCorrectly() {
    Statement domainStatement = new Statement(Statement.Format.JSON_INTOTO, SERIALIZED_DATA);

    com.google.pes.v1.Statement protoStatement = StatementMapper.toProto(domainStatement);

    assertThat(protoStatement.getFormat()).isEqualTo(Format.JSON_INTOTO);
    assertThat(protoStatement.getSerialized()).isEqualTo(SERIALIZED_DATA);
  }

  @Test
  public void toProto_whenUnspecified_mapsCorrectly() {
    Statement domainStatement = new Statement(Statement.Format.FORMAT_UNSPECIFIED, SERIALIZED_DATA);

    com.google.pes.v1.Statement protoStatement = StatementMapper.toProto(domainStatement);

    assertThat(protoStatement.getFormat()).isEqualTo(Format.FORMAT_UNSPECIFIED);
    assertThat(protoStatement.getSerialized()).isEqualTo(SERIALIZED_DATA);
  }

  @Test
  public void toProto_emptyDomain_mapsCorrectly() {
    Statement domainStatement =
        new Statement(Statement.Format.FORMAT_UNSPECIFIED, ByteString.EMPTY);

    com.google.pes.v1.Statement protoStatement = StatementMapper.toProto(domainStatement);

    assertThat(protoStatement.getFormat()).isEqualTo(Format.FORMAT_UNSPECIFIED);
    assertThat(protoStatement.getSerialized()).isEqualTo(ByteString.EMPTY);
  }
}
