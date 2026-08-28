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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Describes a software artifact (resource) in an in-toto statement.
 *
 * @see <a
 *     href="https://github.com/in-toto/attestation/blob/main/spec/v1/resource_descriptor.md">in-toto
 *     ResourceDescriptor</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceDescriptor {
  /**
   * Supported in-toto digest algorithm patterns.
   *
   * @see <a href="https://github.com/in-toto/attestation/blob/main/spec/v1/digest_set.md">in-toto
   *     DigestSet</a>
   */
  private static final Map<String, Pattern> INTOTO_DIGEST_PATTERNS =
      Map.of("sha256", Pattern.compile("^[0-9a-f]{64}$"));

  private final String name;
  private final Map<String, String> digest;

  @JsonCreator
  public ResourceDescriptor(
      @JsonProperty(value = "name") String name,
      @JsonProperty(value = "digest") Map<String, String> digest) {
    validateDigest(digest);
    this.name = name;
    this.digest = digest;
  }

  private void validateDigest(Map<String, String> digest) {
    if (digest == null || digest.isEmpty()) {
      throw new IllegalArgumentException("Subject digest cannot be null or empty.");
    }
    boolean foundValid = false;
    for (Map.Entry<String, Pattern> entry : INTOTO_DIGEST_PATTERNS.entrySet()) {
      String algorithm = entry.getKey();
      Pattern pattern = entry.getValue();
      if (digest.containsKey(algorithm)) {
        String value = digest.get(algorithm);
        if (value == null || !pattern.matcher(value).matches()) {
          throw new IllegalArgumentException(
              String.format(
                  "Subject digest for '%s' does not match the expected format: %s",
                  algorithm, value));
        }
        foundValid = true;
      }
    }
    if (!foundValid) {
      throw new IllegalArgumentException(
          "Subject digest must contain at least one supported digest algorithm: "
              + INTOTO_DIGEST_PATTERNS.keySet());
    }
  }

  public String getName() {
    return name;
  }
}
