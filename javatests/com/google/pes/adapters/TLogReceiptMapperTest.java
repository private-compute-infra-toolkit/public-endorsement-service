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

import com.google.pes.domain.model.TLogReceipt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TLogReceiptMapperTest {

  private static final String ENTRY_ID = "test-entry-id";

  @Test
  public void toDomain_mapsCorrectly() {
    com.google.pes.v1.TLogReceipt protoReceipt =
        com.google.pes.v1.TLogReceipt.newBuilder().setEntryId(ENTRY_ID).build();

    TLogReceipt domainReceipt = TLogReceiptMapper.toDomain(protoReceipt);

    assertThat(domainReceipt.logId()).isEqualTo(ENTRY_ID);
  }

  @Test
  public void toProto_mapsCorrectly() {
    TLogReceipt domainReceipt = new TLogReceipt(ENTRY_ID);

    com.google.pes.v1.TLogReceipt protoReceipt = TLogReceiptMapper.toProto(domainReceipt);

    com.google.pes.v1.TLogReceipt expectedProtoReceipt =
        com.google.pes.v1.TLogReceipt.newBuilder().setEntryId(ENTRY_ID).build();
    assertThat(protoReceipt).isEqualTo(expectedProtoReceipt);
  }

  @Test
  public void toDomain_withDefaultProtoInstance_mapsCorrectly() {
    com.google.pes.v1.TLogReceipt protoReceipt = com.google.pes.v1.TLogReceipt.getDefaultInstance();

    TLogReceipt domainReceipt = TLogReceiptMapper.toDomain(protoReceipt);

    assertThat(domainReceipt.logId()).isEmpty();
  }
}
