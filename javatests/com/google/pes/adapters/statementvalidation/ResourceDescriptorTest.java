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

package com.google.pes.adapters.statementvalidation;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ResourceDescriptorTest {

  private static final String VALID_SHA256 =
      "8c938394c5962194d1449ee17b4db5fdf5a78729b38ebacf26de9bed4027e351";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void constructor_validSha256_succeeds() {
    Map<String, String> digest = Map.of("sha256", VALID_SHA256, "other_key", "other_value");
    ResourceDescriptor descriptor = new ResourceDescriptor("my_artifact", digest);

    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getName()).isEqualTo("my_artifact");
  }

  @Test
  public void constructor_nullName_succeeds() {
    ResourceDescriptor descriptor = new ResourceDescriptor(null, Map.of("sha256", VALID_SHA256));

    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getName()).isNull();
  }

  @Test
  public void constructor_missingSha256_throwsIllegalArgumentException() {
    Map<String, String> digest = Map.of("other_key", "other_value");
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> new ResourceDescriptor("my_artifact", digest));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Subject digest must contain at least one supported digest algorithm");
  }

  @Test
  public void constructor_nullOrEmptyDigest_throwsIllegalArgumentException() {
    IllegalArgumentException nullThrown =
        assertThrows(
            IllegalArgumentException.class, () -> new ResourceDescriptor("my_artifact", null));
    assertThat(nullThrown).hasMessageThat().contains("Subject digest cannot be null or empty");

    IllegalArgumentException emptyThrown =
        assertThrows(
            IllegalArgumentException.class, () -> new ResourceDescriptor("my_artifact", Map.of()));
    assertThat(emptyThrown).hasMessageThat().contains("Subject digest cannot be null or empty");
  }

  @Test
  public void constructor_invalidSha256_throwsIllegalArgumentException() {
    Map<String, String> nullSha = new HashMap<>();
    nullSha.put("sha256", null);

    Map<String, Map<String, String>> invalidShaDigests =
        Map.of(
            "null_value", nullSha,
            "empty", Map.of("sha256", ""),
            "short", Map.of("sha256", "1234"),
            "uppercase", Map.of("sha256", VALID_SHA256.toUpperCase()),
            "non_hex", Map.of("sha256", "g".repeat(64)),
            "with_whitespace", Map.of("sha256", VALID_SHA256 + "\n"));

    for (Map<String, String> digest : invalidShaDigests.values()) {
      assertThrows(
          IllegalArgumentException.class, () -> new ResourceDescriptor("my_artifact", digest));
    }
  }

  @Test
  public void jsonDeserialization_validJson_succeeds() throws Exception {
    String json =
        "{\n"
            + "  \"name\": \"my_artifact\",\n"
            + "  \"uri\": \"https://example.com/bin\",\n"
            + "  \"digest\": {\n"
            + "    \"sha256\": \""
            + VALID_SHA256
            + "\",\n"
            + "    \"ignored_key\": \"ignored_value\"\n"
            + "  }\n"
            + "}";
    ResourceDescriptor descriptor = MAPPER.readValue(json, ResourceDescriptor.class);

    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getName()).isEqualTo("my_artifact");
  }

  @Test
  public void jsonDeserialization_invalidDigest_throwsException() {
    String missingDigest = "{\"name\": \"my_artifact\"}";
    String emptyDigest = "{\"name\": \"my_artifact\", \"digest\": {}}";
    String invalidSha = "{\"name\": \"my_artifact\", \"digest\": {\"sha256\": \"bad\"}}";

    assertThrows(Exception.class, () -> MAPPER.readValue(missingDigest, ResourceDescriptor.class));
    assertThrows(Exception.class, () -> MAPPER.readValue(emptyDigest, ResourceDescriptor.class));
    assertThrows(Exception.class, () -> MAPPER.readValue(invalidSha, ResourceDescriptor.class));
  }
}
