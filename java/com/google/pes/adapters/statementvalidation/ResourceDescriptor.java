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

/**
 * Describes a software artifact (resource) in an in-toto statement.
 *
 * @see <a
 *     href="https://github.com/in-toto/attestation/blob/main/spec/v1/resource_descriptor.md">in-toto
 *     ResourceDescriptor</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceDescriptor {
  private final String name;
  private final Map<String, String> digest;

  @JsonCreator
  public ResourceDescriptor(
      @JsonProperty(value = "name") String name,
      @JsonProperty(value = "digest") Map<String, String> digest) {
    this.name = name;
    this.digest = digest;
  }

  public String getName() {
    return name;
  }
}
