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
 * Represents the predicate of an Oak endorsement statement version 1.
 *
 * @see <a href="https://project-oak.github.io/oak/tr/endorsement/v1">Oak Endorsement
 *     Specification</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OakPredicate {

  /** The period during which the endorsement is considered valid. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Validity {
    private final String notBefore;
    private final String notAfter;

    @JsonCreator
    public Validity(
        @JsonProperty(value = "notBefore", required = true) String notBefore,
        @JsonProperty(value = "notAfter", required = true) String notAfter) {
      this.notBefore = notBefore;
      this.notAfter = notAfter;
    }

    public String getNotBefore() {
      return notBefore;
    }

    public String getNotAfter() {
      return notAfter;
    }
  }

  private final String issuedOn;
  private final Validity validity;
  private final List<Claim> claims;

  @JsonCreator
  public OakPredicate(
      @JsonProperty(value = "issuedOn", required = true) String issuedOn,
      @JsonProperty(value = "validity", required = true) Validity validity,
      @JsonProperty(value = "claims", required = true) List<Claim> claims) {

    if (claims == null || claims.isEmpty()) {
      throw new IllegalArgumentException(
          "The 'claims' array must be not empty and include exactly one publisher claim");
    }

    this.issuedOn = issuedOn;
    this.validity = validity;
    this.claims = claims;
  }

  public List<Claim> getClaims() {
    return claims;
  }
}
