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

package com.google.pes.server;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.pes.domain.metric.Metrics;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class CertificateValidityReporterTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private X509Certificate mockCertificate;
  @Mock private Metrics mockMetrics;

  private CertificateValidityReporter reporter;

  @Before
  public void setUp() {
    com.google.mbs.domain.MeasurementBoundCertificateProvider provider =
        () ->
            new com.google.mbs.domain.TrustPackage(
                java.util.List.of(
                    new com.google.mbs.domain.MeasurementBoundCertificate(
                        mockCertificate, null, null)),
                java.util.List.of());
    reporter = new CertificateValidityReporter(provider, mockMetrics);
  }

  @Test
  public void runOneIteration_reportsCorrectValidity() throws Exception {
    // Arrange: Set expiry to 10 days from now
    Instant now = Instant.now();
    Instant expiry = now.plus(Duration.ofDays(10));
    when(mockCertificate.getNotAfter()).thenReturn(Date.from(expiry));

    // Act
    reporter.runOneIteration();

    // Assert
    ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(mockMetrics).setRootCertificateValidity(durationCaptor.capture());

    Duration reportedDuration = durationCaptor.getValue();
    // Allow some tolerance for execution time, but it should be 10 days (or 9 if it crossed a day
    // boundary, but unlikely in ms)
    // To be safe, we check it is close to 10 days.
    assertThat(reportedDuration.toSeconds())
        .isAtLeast(Duration.ofDays(10).minusMinutes(1).toSeconds());
    assertThat(reportedDuration.toSeconds()).isAtMost(Duration.ofDays(10).toSeconds());
  }

  @Test
  public void runOneIteration_whenExpired_reportsZero() throws Exception {
    // Arrange: Set expiry to 10 days ago
    Instant now = Instant.now();
    Instant expiry = now.minus(Duration.ofDays(10));
    when(mockCertificate.getNotAfter()).thenReturn(Date.from(expiry));

    // Act
    reporter.runOneIteration();

    // Assert
    verify(mockMetrics).setRootCertificateValidity(Duration.ZERO);
  }

  @Test
  public void runOneIteration_certificateNotReady_handlesGracefully() {
    com.google.mbs.domain.MeasurementBoundCertificateProvider unreadyProvider =
        () -> {
          throw new IllegalStateException("Certificate has not been initialized yet");
        };
    reporter = new CertificateValidityReporter(unreadyProvider, mockMetrics);

    // Should not throw exception
    reporter.runOneIteration();

    org.mockito.Mockito.verifyNoInteractions(mockMetrics);
  }
}
