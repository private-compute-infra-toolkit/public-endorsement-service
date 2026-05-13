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

import com.google.pes.domain.model.TLogReceipt;

/** Maps between domain {@link TLogReceipt} and proto {@link com.google.pes.v1.TLogReceipt}. */
public final class TLogReceiptMapper {

  private TLogReceiptMapper() {}

  public static TLogReceipt toDomain(com.google.pes.v1.TLogReceipt proto) {
    return new TLogReceipt(proto.getEntryId());
  }

  public static com.google.pes.v1.TLogReceipt toProto(TLogReceipt domain) {
    return com.google.pes.v1.TLogReceipt.newBuilder().setEntryId(domain.logId()).build();
  }
}
