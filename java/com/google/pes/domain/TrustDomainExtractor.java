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

package com.google.pes.domain;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import org.bouncycastle.asn1.x509.GeneralName;

/** Utility class to extract SPIFFE ID trust domain from root certificates. */
public final class TrustDomainExtractor {

  private static final int SAN_TAG_INDEX = 0;
  private static final int SAN_VALUE_INDEX = 1;

  private TrustDomainExtractor() {}

  /**
   * Extracts the trust domain from the Subject Alternative Name (SAN) of the root certificate.
   *
   * @throws CertificateParsingException if the certificate cannot be parsed.
   * @throws IllegalArgumentException if the trust domain cannot be found.
   */
  public static String extract(X509Certificate rootCertificate)
      throws CertificateParsingException, java.net.URISyntaxException {
    Collection<List<?>> sans = rootCertificate.getSubjectAlternativeNames();
    if (sans != null) {
      for (List<?> san : sans) {
        if (isUriSan(san)) {
          return tryParseTrustDomain(san);
        }
      }
    }
    throw new IllegalArgumentException(
        "Root certificate SAN does not contain a valid SPIFFE ID trust domain.");
  }

  private static String tryParseTrustDomain(List<?> san) throws java.net.URISyntaxException {
    Object value = san.get(SAN_VALUE_INDEX);
    if (!(value instanceof String)) {
      throw new IllegalArgumentException(
          String.format(
              "URI SAN value is not a String. Expected: String, Actual: %s",
              value != null ? value.getClass().getName() : "null"));
    }
    String spiffeId = (String) value;
    return extractTrustDomainFromSpiffeId(spiffeId);
  }

  private static boolean isUriSan(List<?> san) {
    return san.size() >= 2
        && san.get(SAN_TAG_INDEX) instanceof Integer
        && (Integer) san.get(SAN_TAG_INDEX) == GeneralName.uniformResourceIdentifier;
  }

  private static String extractTrustDomainFromSpiffeId(String spiffeId)
      throws java.net.URISyntaxException {
    if (!spiffeId.startsWith("spiffe://")) {
      throw new IllegalArgumentException(
          String.format(
              "SPIFFE ID URI does not start with expected scheme. Expected prefix: 'spiffe://',"
                  + " Actual URI: '%s'",
              spiffeId));
    }
    java.net.URI uri = new java.net.URI(spiffeId);
    String host = uri.getHost();
    if (host == null || host.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "SPIFFE ID URI does not contain a valid host/trust domain. Actual SPIFFE ID: '%s'",
              spiffeId));
    }
    return extractTrustDomainFromHost(host);
  }

  private static String extractTrustDomainFromHost(String host) {
    if (!host.startsWith("pes.")) {
      throw new IllegalArgumentException(
          String.format(
              "SPIFFE ID trust domain does not start with expected prefix. Expected prefix: 'pes.',"
                  + " Actual host/trust domain: '%s'",
              host));
    }
    return host;
  }
}
