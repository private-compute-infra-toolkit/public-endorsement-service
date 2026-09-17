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

package com.google.pes.adapters.tsa;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;

/**
 * Utility class providing trusted root certificate {@link TrustAnchor}s for RFC 3161 Time-Stamping
 * Authorities (DigiCert, GlobalSign).
 */
public final class TsaTrustAnchors {
  private static final String RESOURCE_NAME = "/com/google/pes/resources/tsa_root_certs.pem";
  private static final ImmutableSet<TrustAnchor> DEFAULT_TRUST_ANCHORS = loadDefaultTrustAnchors();

  private TsaTrustAnchors() {}

  /**
   * Returns an immutable set of default {@link TrustAnchor}s containing the root certificates for
   * DigiCert and GlobalSign.
   */
  public static ImmutableSet<TrustAnchor> getDefaultTrustAnchors() {
    return DEFAULT_TRUST_ANCHORS;
  }

  private static ImmutableSet<TrustAnchor> loadDefaultTrustAnchors() {
    try (InputStream in = TsaTrustAnchors.class.getResourceAsStream(RESOURCE_NAME)) {
      return CertificateFactory.getInstance("X.509").generateCertificates(in).stream()
          .map(cert -> new TrustAnchor((X509Certificate) cert, null))
          .collect(toImmutableSet());
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to load TSA root certificates from " + RESOURCE_NAME, e);
    }
  }
}
