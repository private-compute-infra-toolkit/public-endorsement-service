/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.pes.adapters.statementvalidation;

import static com.google.pes.adapters.statementvalidation.Claim.LEGACY_PUBLISHER_CLAIM_TYPE;
import static com.google.pes.adapters.statementvalidation.Claim.PUBLISHER_CLAIM_TYPE;
import static com.google.pes.adapters.statementvalidation.Claim.PUBLISHER_ID_KEY;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.pes.domain.ports.PublisherIdProvider;
import com.google.protobuf.ByteString;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.stream.Collectors;

/** Provider for publisher IDs extracted and validated from in-toto statements. */
public final class JsonPublisherIdProvider implements PublisherIdProvider {
  private final ObjectReader reader;

  @Inject
  JsonPublisherIdProvider(ObjectMapper mapper) {
    this.reader = mapper.readerFor(InTotoStatement.class);
  }

  /**
   * Parses and validates an in-toto statement from a ByteString to extract the publisher ID.
   *
   * <p>This method performs the following validations:
   *
   * <ol>
   *   <li>Ensures the input is valid JSON and matches the in-toto Statement v1 schema.
   *   <li>Verifies the statement contains exactly one resource descriptor in the 'subject'.
   *   <li>Checks that the 'predicate' includes a publisher claim with a valid 'publisher_id'.
   * </ol>
   *
   * @param statementBytes The ByteString containing the JSON in-toto statement.
   * @return The validated publisher_id string.
   * @throws IllegalArgumentException if the statement is invalid or if the publisher claim is
   *     missing.
   */
  @Override
  public String getValidPublisherId(ByteString statementBytes) {
    InTotoStatement statement;
    try {
      statement = reader.readValue(statementBytes.toByteArray());
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format("Failed to parse input as JSON: %s", e.getMessage()), e);
    }

    return statement.getPredicate().getClaims().stream()
        .filter(
            claim ->
                claim.getType().equals(PUBLISHER_CLAIM_TYPE)
                    || claim.getType().equals(LEGACY_PUBLISHER_CLAIM_TYPE))
        .collect(
            Collectors.collectingAndThen(
                Collectors.toList(),
                list -> {
                  if (list.size() != 1) {
                    throw new IllegalArgumentException(
                        "Expected exactly one publisher claim, but found: " + list.size());
                  }
                  return list.get(0).getAnnotations().get(PUBLISHER_ID_KEY);
                }));
  }
}
