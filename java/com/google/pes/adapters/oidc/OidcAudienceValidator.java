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

import com.google.common.flogger.FluentLogger;
import com.google.mbs.domain.MeasurementBoundCertificateProvider;
import com.google.pes.domain.CallerIdentity;
import com.google.pes.domain.JwtAuth;
import com.google.pes.domain.TrustDomainExtractor;
import com.google.pes.server.AwsInstanceMetadata;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import jakarta.inject.Inject;
import java.net.URISyntaxException;
import java.security.Key;
import java.security.cert.CertificateParsingException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** OIDC audience validation. */
public final class OidcAudienceValidator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Pattern AUDIENCE_PATTERN =
      Pattern.compile("^https://([^/]+)(?:/v1/endorsements)?$");

  private final MeasurementBoundCertificateProvider certificateProvider;
  private final AwsInstanceMetadata metadata;
  private final Locator<Key> keyLocator;

  @Inject
  public OidcAudienceValidator(
      MeasurementBoundCertificateProvider certificateProvider,
      AwsInstanceMetadata metadata,
      @JwtAuth Locator<Key> keyLocator) {
    this.certificateProvider = certificateProvider;
    this.metadata = metadata;
    this.keyLocator = keyLocator;
  }

  /*
   * Validates content of bearer token
   * Returns caller identity if successful, throws exception if not
   */
  public CallerIdentity parseAndValidate(String token) throws JwtException {
    CallerIdentity identity = parseBearerToken(token);
    validateAudience(identity.audiences());
    return identity;
  }

  void validateAudience(Set<String> audiences) {
    String trustDomain = resolveTrustDomain();
    for (String audience : audiences) {
      Matcher matcher = AUDIENCE_PATTERN.matcher(audience);
      if (matcher.matches()) {
        String hostname = matcher.group(1);
        if (isValidHostname(hostname, trustDomain)) {
          return;
        }
      }
    }

    logger.atWarning().log(
        "OIDC audience binding mismatch. Expected a valid audience matching format: "
            + "https://<hostname> or https://<hostname>/v1/endorsements, where <hostname> is "
            + "either a trust domain, global or regional hostname. Found audiences: %s",
        audiences);
    throw new AudienceValidationException("OIDC audience binding validation failed.");
  }

  private boolean isValidHostname(String hostname, String trustDomain) {
    if (hostname.equals(trustDomain)) {
      return true;
    }

    String globalHostname = String.format("pes.%s.%s", metadata.environment(), metadata.domain());
    if (hostname.equals(globalHostname)) {
      return true;
    }

    String regionalHostname = String.format("%s.%s", metadata.region(), globalHostname);
    if (hostname.equals(regionalHostname)) {
      return true;
    }

    return false;
  }

  /**
   * Resolves the SPIFFE trust domain from the currently active root certificate.
   *
   * @throws IllegalStateException if the root certificate has not been loaded yet.
   */
  private String resolveTrustDomain() {
    try {
      return TrustDomainExtractor.extract(
          certificateProvider.getActiveTrustPackage().bundles().get(0).getCertificate());
    } catch (CertificateParsingException | URISyntaxException | IllegalArgumentException e) {
      throw new IllegalStateException(
          "Failed to extract trust domain from active root certificate", e);
    }
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
