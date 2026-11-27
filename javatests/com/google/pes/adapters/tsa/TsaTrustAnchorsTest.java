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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.security.MessageDigest;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TsaTrustAnchorsTest {

  private static final ImmutableMap<String, String> EXPECTED_ROOT_CERTS =
      ImmutableMap.of(
          "4348a0e9444c78cb265e058d5e8944b4d84f9662bd26db257f8934a443c70161",
          "DigiCert Global Root CA",
          "552f7bdcf1a7af9e6ce672017f4f12abf77240c78e761ac203d1d9d20ac89988",
          "DigiCert Trusted Root G4",
          "cbb522d7b7f127ad6a0113865bdf1cd4102e7d0759af635a7cf4720dc963c53b",
          "GlobalSign Root CA - R3",
          "2cabeafe37d06ca22aba7391c0033d25982952c453647349763a3ab5ad6ccf69",
          "GlobalSign Root CA - R6");

  @Test
  public void getDefaultTrustAnchors_loadsExpectedAnchors() throws Exception {
    ImmutableSet<TrustAnchor> anchors = TsaTrustAnchors.getDefaultTrustAnchors();
    assertThat(anchors).hasSize(EXPECTED_ROOT_CERTS.size());

    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    Set<String> seenFingerprints = new HashSet<>();

    for (TrustAnchor anchor : anchors) {
      X509Certificate cert = anchor.getTrustedCert();
      assertThat(cert).isNotNull();

      cert.checkValidity();
      assertThat(cert.getBasicConstraints()).isNotEqualTo(-1);

      String fingerprint = HexFormat.of().formatHex(sha256.digest(cert.getEncoded()));
      String dn = cert.getSubjectX500Principal().getName();

      String expectedDnSubstring = EXPECTED_ROOT_CERTS.get(fingerprint);
      assertThat(expectedDnSubstring).isNotNull();
      assertThat(dn).contains(expectedDnSubstring);
      seenFingerprints.add(fingerprint);
    }

    assertThat(seenFingerprints).containsExactlyElementsIn(EXPECTED_ROOT_CERTS.keySet());
  }
}
