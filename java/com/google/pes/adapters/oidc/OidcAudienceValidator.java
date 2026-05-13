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

package com.google.pes.adapters.oidc;

import com.google.common.flogger.FluentLogger;
import com.google.pes.domain.CallerIdentity;
import com.google.pes.domain.JwtAuth;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import jakarta.inject.Inject;
import java.security.Key;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** OIDC audience validation. */
public final class OidcAudienceValidator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final String audienceHostname;
  private final Locator<Key> keyLocator;

  @Inject
  public OidcAudienceValidator(
      @AudienceHostname String audienceHostname, @JwtAuth Locator<Key> keyLocator) {
    this.audienceHostname = audienceHostname;
    this.keyLocator = keyLocator;
  }

  /*
   * Validates content of bearer token
   * Returns caller identity if successful, throws exception if not
   */
  public CallerIdentity parseAndValidate(String token) throws JwtException {
    String expectedAudience = String.format("https://%s/v1/endorsements", audienceHostname);

    CallerIdentity identity = parseBearerToken(token);

    if (!identity.audiences().contains(expectedAudience)) {
      logger.atSevere().log(
          "OIDC audience binding mismatch. Expected an audience of: %s. Found audiences: %s",
          expectedAudience, identity.audiences());
      // TODO: We should throw exception here
    }
    return identity;
  }

  private CallerIdentity parseBearerToken(String token) throws JwtException {
    Claims claims = Jwts.parser().keyLocator(keyLocator).build().parseClaimsJws(token).getBody();
    String issuer = claims.getIssuer();
    String subject = claims.getSubject();
    Set<String> audiences = extractAudiences(claims.getAudience());
    logger.atInfo().log(
        "Extracted issuer from JWT: %s, subject: %s, audiences: %s", issuer, subject, audiences);
    return new CallerIdentity(issuer, subject, audiences);
  }

  private Set<String> extractAudiences(Object aud) {
    if (aud == null) {
      return Collections.emptySet();
    }
    if (aud instanceof String) {
      return Collections.singleton((String) aud);
    }
    if (aud instanceof Collection) {
      Set<String> audiences = new HashSet<>();
      for (Object item : (Collection<?>) aud) {
        if (item instanceof String) {
          audiences.add((String) item);
        }
      }
      return Collections.unmodifiableSet(audiences);
    }
    return Collections.emptySet();
  }
}
