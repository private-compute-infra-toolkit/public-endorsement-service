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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.bouncycastle.asn1.x509.GeneralName;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TrustDomainExtractorTest {

  private X509Certificate mockCert;

  @Before
  public void setUp() {
    mockCert = mock(X509Certificate.class);
  }

  @Test
  public void extract_validSpiffeIdSan_succeeds() throws Exception {
    Collection<List<?>> sans = new ArrayList<>();
    sans.add(
        Arrays.asList(GeneralName.uniformResourceIdentifier, "spiffe://pes.pcit.goog/workload"));
    when(mockCert.getSubjectAlternativeNames()).thenReturn(sans);

    String trustDomain = TrustDomainExtractor.extract(mockCert);

    assertThat(trustDomain).isEqualTo("pes.pcit.goog");
  }

  @Test
  public void extract_noSans_throwsException() throws Exception {
    when(mockCert.getSubjectAlternativeNames()).thenReturn(null);

    assertThrows(IllegalArgumentException.class, () -> TrustDomainExtractor.extract(mockCert));
  }

  @Test
  public void extract_emptySans_throwsException() throws Exception {
    when(mockCert.getSubjectAlternativeNames()).thenReturn(new ArrayList<>());

    assertThrows(IllegalArgumentException.class, () -> TrustDomainExtractor.extract(mockCert));
  }

  @Test
  public void extract_nonUriSans_throwsException() throws Exception {
    Collection<List<?>> sans = new ArrayList<>();
    sans.add(Arrays.asList(GeneralName.dNSName, "pes.pcit.goog"));
    sans.add(Arrays.asList(GeneralName.directoryName, "CN=PES"));
    when(mockCert.getSubjectAlternativeNames()).thenReturn(sans);

    assertThrows(IllegalArgumentException.class, () -> TrustDomainExtractor.extract(mockCert));
  }

  @Test
  public void extract_multipleSansSomeValid_succeeds() throws Exception {
    Collection<List<?>> sans = new ArrayList<>();
    sans.add(Arrays.asList(GeneralName.dNSName, "pes.pcit.goog"));
    sans.add(
        Arrays.asList(GeneralName.uniformResourceIdentifier, "spiffe://pes.pcit.goog/workload"));
    when(mockCert.getSubjectAlternativeNames()).thenReturn(sans);

    String trustDomain = TrustDomainExtractor.extract(mockCert);

    assertThat(trustDomain).isEqualTo("pes.pcit.goog");
  }

  @Test
  public void extract_malformedSpiffeId_throwsException() throws Exception {
    Collection<List<?>> sans = new ArrayList<>();
    sans.add(
        Arrays.asList(GeneralName.uniformResourceIdentifier, "invalid-spiffe://pes.pcit.goog"));
    when(mockCert.getSubjectAlternativeNames()).thenReturn(sans);

    assertThrows(IllegalArgumentException.class, () -> TrustDomainExtractor.extract(mockCert));
  }

  @Test
  public void extract_invalidTrustDomainHost_throwsException() throws Exception {
    Collection<List<?>> sans = new ArrayList<>();
    sans.add(
        Arrays.asList(
            GeneralName.uniformResourceIdentifier, "spiffe://invalid.pcit.goog/workload"));
    when(mockCert.getSubjectAlternativeNames()).thenReturn(sans);

    assertThrows(IllegalArgumentException.class, () -> TrustDomainExtractor.extract(mockCert));
  }
}
