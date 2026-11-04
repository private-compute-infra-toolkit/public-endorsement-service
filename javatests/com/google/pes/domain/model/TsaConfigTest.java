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

package com.google.pes.domain.model;

import static com.google.common.truth.Truth.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TsaConfigTest {

  @Test
  public void toString_redactsTsaUrls() {
    URI secretUri = URI.create("https://secret-tsa.example.com/timestamp?token=123");
    TsaConfig config = new TsaConfig(true, List.of(secretUri));

    String str = config.toString();

    assertThat(str).isEqualTo("TsaConfig[enabled=true, tsaUrls.size=1]");
    assertThat(str).doesNotContain(secretUri.toString());
    assertThat(str).doesNotContain(secretUri.getHost());
  }
}
