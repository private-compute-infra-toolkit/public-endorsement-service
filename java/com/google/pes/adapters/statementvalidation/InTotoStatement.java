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
import java.util.List;

/**
 * Represents the outer envelope of an in-toto v1 Statement.
 *
 * @see <a href="https://in-toto.io/Statement/v1">in-toto Statement v1</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InTotoStatement {
  private static final String TYPE_V1 = "https://in-toto.io/Statement/v1";
  private static final String OAK_PREDICATE_V1_TYPE =
      "https://project-oak.github.io/oak/tr/endorsement/v1";

  private final List<ResourceDescriptor> subject;
  private final OakPredicate predicate;

  @JsonCreator
  public InTotoStatement(
      @JsonProperty(value = "_type", required = true) String type,
      @JsonProperty(value = "subject", required = true) List<ResourceDescriptor> subject,
      @JsonProperty(value = "predicateType", required = true) String predicateType,
      @JsonProperty(value = "predicate", required = true) OakPredicate predicate) {
    if (type == null) {
      throw new IllegalArgumentException("Field '_type' cannot be null!");
    }
    if (!TYPE_V1.equals(type)) {
      throw new IllegalArgumentException(
          String.format("Statement _type is not '%s'. Got: %s", TYPE_V1, type));
    }

    if (predicateType == null) {
      throw new IllegalArgumentException("Field 'predicateType' cannot be null!");
    }
    if (!OAK_PREDICATE_V1_TYPE.equals(predicateType)) {
      throw new IllegalArgumentException(
          String.format(
              "Statement predicateType is not '%s'. Got: %s",
              OAK_PREDICATE_V1_TYPE, predicateType));
    }

    if (subject == null || subject.size() != 1) {
      throw new IllegalArgumentException(
          "The 'subject' array must be not empty and include exactly one resource descriptor.");
    }

    this.subject = subject;
    this.predicate = predicate;
  }

  public List<ResourceDescriptor> getSubject() {
    return subject;
  }

  public OakPredicate getPredicate() {
    return predicate;
  }
}
