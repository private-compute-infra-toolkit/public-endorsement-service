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

import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.model.TLogReceipt;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the Pre-Authentication Encoding (PAE) for PES signature.
 *
 * <p>This encoding constructs a canonical byte string derived from a Statement, its Signature, and
 * a Transparency Log Receipt, allowing for secure verification of the endorsement.
 */
public class PreAuthenticationEncoding {
  private static final ByteString PES_PREFIX = ByteString.copyFromUtf8("PES");
  private static final ByteString SPACE = ByteString.copyFrom(new byte[] {0x20});

  /**
   * Calculates the Pre-Authentication Encoding (PAE) byte string.
   *
   * <p>The PAE is calculated using the following format:
   *
   * <pre>
   * "PES" + ENCODE(statement.serialized) +
   * ENCODE(statement_signature.verification_material) +
   * ENCODE(statement_signature.signature) +
   * ENCODE(UTF8(log_id))
   * </pre>
   *
   * @param statement The statement containing the serialized data to be endorsed.
   * @param signature The signature object containing verification material and the raw signature.
   * @param tlogReceipt The transparency log receipt containing the log ID.
   * @return A {@link ByteString} containing the fully constructed PAE.
   */
  public static ByteString calculate(
      Statement statement, Signature signature, TLogReceipt tlogReceipt) {
    List<ByteString> parts = new ArrayList<>();

    parts.add(PES_PREFIX);

    parts.add(encode(statement.serialized()));
    parts.add(encode(signature.verificationMaterial().content()));
    parts.add(encode(signature.signature()));

    ByteString logIdBytes = ByteString.copyFromUtf8(tlogReceipt.logId());
    parts.add(encode(logIdBytes));

    return ByteString.copyFrom(parts);
  }

  /** Implements ENCODE(obj) -> SP + LEN(obj) + SP + obj */
  private static ByteString encode(ByteString data) {
    ByteString lengthBytes = ByteString.copyFromUtf8(String.valueOf(data.size()));

    return SPACE.concat(lengthBytes).concat(SPACE).concat(data);
  }
}
