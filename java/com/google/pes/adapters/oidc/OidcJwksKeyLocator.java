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

package com.google.pes.adapters.oidc;

import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Locator;
import java.security.Key;

/** A locator that finds the appropriate public key in a JWKS cache. */
public final class OidcJwksKeyLocator implements Locator<Key> {
  private final OidcJwksKeyFetcher keyFetcher;
  private final String expectedAlg;

  public OidcJwksKeyLocator(OidcJwksKeyFetcher keyFetcher, String expectedAlg) {
    this.keyFetcher = keyFetcher;
    this.expectedAlg = expectedAlg;
  }

  @Override
  public Key locate(Header header) {
    if (!(header instanceof JwsHeader)) {
      return null;
    }
    JwsHeader jwsHeader = (JwsHeader) header;
    if (expectedAlg != null && !expectedAlg.equals(jwsHeader.getAlgorithm())) {
      return null;
    }
    String kid = jwsHeader.getKeyId();
    return kid == null ? null : keyFetcher.getKey(kid).orElse(null);
  }
}
