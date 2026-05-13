/*
 * Copyright 2025 Google LLC
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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.Set;

/** Represents the identity of the caller extracted from the OIDC token. */
public record CallerIdentity(String issuer, String subject, Set<String> audiences) {
  public CallerIdentity {
    requireNonNull(issuer);
    requireNonNull(subject);
    requireNonNull(audiences);
    checkArgument(!issuer.isBlank(), "issuer cannot be blank");
    checkArgument(!subject.isBlank(), "subject cannot be blank");
  }

  public String getClientId() {
    return escape(issuer) + "/" + escape(subject);
  }

  public static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (isSafe(c)) {
        sb.append(c);
      } else {
        sb.append(String.format("_%04x", (int) c));
      }
    }
    return sb.toString();
  }

  private static boolean isSafe(char c) {
    // Alphanumeric, hyphen, and period are safe and don't conflict with our escape char '_'
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || c == '-'
        || c == '.';
  }
}
