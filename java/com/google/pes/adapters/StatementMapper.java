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

import com.google.pes.domain.model.Statement;

/** Maps between domain {@link Statement} and proto {@link com.google.pes.v1.Statement}. */
public class StatementMapper {
  public static Statement toDomain(com.google.pes.v1.Statement proto) {
    return new Statement(mapFormatToRecord(proto.getFormat()), proto.getSerialized());
  }

  public static com.google.pes.v1.Statement toProto(Statement record) {
    return com.google.pes.v1.Statement.newBuilder()
        .setFormat(mapFormatToProto(record.format()))
        .setSerialized(record.serialized())
        .build();
  }

  private static Statement.Format mapFormatToRecord(
      com.google.pes.v1.Statement.Format protoFormat) {
    return switch (protoFormat) {
      case JSON_INTOTO -> Statement.Format.JSON_INTOTO;

      default -> Statement.Format.FORMAT_UNSPECIFIED;
    };
  }

  private static com.google.pes.v1.Statement.Format mapFormatToProto(
      Statement.Format recordFormat) {
    return switch (recordFormat) {
      case JSON_INTOTO -> com.google.pes.v1.Statement.Format.JSON_INTOTO;

      default -> com.google.pes.v1.Statement.Format.FORMAT_UNSPECIFIED;
    };
  }
}
