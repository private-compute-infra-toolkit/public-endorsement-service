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

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.mbs.domain.MeasurementBoundCertificateProvider;
import com.google.pes.domain.metric.Metrics;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

/** Reports the validity of the root PES certificate to the Metrics system. */
@Singleton
public class CertificateValidityReporter extends AbstractScheduledService {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final MeasurementBoundCertificateProvider certificateProvider;
  private final Metrics metrics;

  @Inject
  public CertificateValidityReporter(
      MeasurementBoundCertificateProvider certificateProvider, Metrics metrics) {
    this.certificateProvider = certificateProvider;
    this.metrics = metrics;
  }

  @Override
  protected void runOneIteration() {
    try {
      X509Certificate certificate =
          certificateProvider.getActiveTrustPackage().bundles().get(0).getCertificate();
      Instant expiry = certificate.getNotAfter().toInstant();
      Instant now = Instant.now();
      Duration remaining = Duration.between(now, expiry);
      // Ensure we don't report negative validity if it's expired
      if (remaining.isNegative()) {
        remaining = Duration.ZERO;
      }
      metrics.setRootCertificateValidity(remaining);
      logger.atInfo().log(
          "Reported certificate validity metric: %d seconds remaining", remaining.toSeconds());
    } catch (IllegalStateException e) {
      logger.atWarning().log(
          "Certificate not ready yet, skipping certificate validity update: %s", e.getMessage());
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Failed to update certificate validity metric");
    }
  }

  @Override
  protected Scheduler scheduler() {
    // Stagger startup by 30 seconds to allow CertificateMonitor to complete the initial
    // load/generation and ensure steady-state reporting runs shortly after periodic 1-minute
    // reloads.
    return Scheduler.newFixedRateSchedule(Duration.ofSeconds(30), Duration.ofMinutes(1));
  }
}
