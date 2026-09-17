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

import com.google.common.collect.ImmutableSet;
import java.security.MessageDigest;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TsaTrustAnchorsTest {

  private static final String DIGICERT_GLOBAL_ROOT_SHA256 =
      "4348a0e9444c78cb265e058d5e8944b4d84f9662bd26db257f8934a443c70161";
  private static final String GLOBALSIGN_ROOT_R3_SHA256 =
      "cbb522d7b7f127ad6a0113865bdf1cd4102e7d0759af635a7cf4720dc963c53b";

  @Test
  public void getDefaultTrustAnchors_loadsExpectedAnchors() throws Exception {
    ImmutableSet<TrustAnchor> anchors = TsaTrustAnchors.getDefaultTrustAnchors();
    assertThat(anchors).hasSize(2);

    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    boolean hasDigiCert = false;
    boolean hasGlobalSign = false;

    for (TrustAnchor anchor : anchors) {
      X509Certificate cert = anchor.getTrustedCert();
      assertThat(cert).isNotNull();

      cert.checkValidity();
      assertThat(cert.getBasicConstraints()).isNotEqualTo(-1);

      String fingerprint = HexFormat.of().formatHex(sha256.digest(cert.getEncoded()));
      String dn = cert.getSubjectX500Principal().getName();

      if (fingerprint.equalsIgnoreCase(DIGICERT_GLOBAL_ROOT_SHA256)) {
        assertThat(dn).contains("DigiCert Global Root CA");
        hasDigiCert = true;
      } else if (fingerprint.equalsIgnoreCase(GLOBALSIGN_ROOT_R3_SHA256)) {
        assertThat(dn).contains("GlobalSign Root CA - R3");
        hasGlobalSign = true;
      } else {
        throw new AssertionError("Unexpected trust anchor certificate: " + dn);
      }
    }

    assertThat(hasDigiCert).isTrue();
    assertThat(hasGlobalSign).isTrue();
  }
}
