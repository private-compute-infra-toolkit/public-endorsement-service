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

package com.google.pes.adapters;

import static com.google.common.truth.Truth.assertThat;

import com.google.pes.domain.metric.AuthenticationStatus;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SystemMetricsTest {

  private PrometheusMeterRegistry registry;
  private SystemMetrics systemMetrics;

  @Before
  public void setUp() {
    registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    systemMetrics = new SystemMetrics(registry);
  }

  @Test
  public void constructor_preRegistersAllCountersWithZeroValue() {
    for (AuthenticationStatus status : AuthenticationStatus.values()) {
      double value =
          registry
              .get("pes.authenticationStatus")
              .tag("status", status.name().toLowerCase())
              .counter()
              .count();
      assertThat(value).isEqualTo(0.0);
    }
  }

  @Test
  public void incrementAuthenticationCounter_succeeds() {
    systemMetrics.incrementAuthenticationCounter(AuthenticationStatus.SUCCESS);
    systemMetrics.incrementAuthenticationCounter(AuthenticationStatus.SUCCESS);
    systemMetrics.incrementAuthenticationCounter(AuthenticationStatus.FAILURE);
    systemMetrics.incrementAuthenticationCounter(AuthenticationStatus.FAILURE);

    double successVal =
        registry.get("pes.authenticationStatus").tag("status", "success").counter().count();
    double failureVal =
        registry.get("pes.authenticationStatus").tag("status", "failure").counter().count();

    assertThat(successVal).isEqualTo(2.0);
    assertThat(failureVal).isEqualTo(2.0);
  }

  @Test
  public void increment_nullInputs_safelyIgnored() {
    systemMetrics.incrementAuthenticationCounter(null);

    double authSuccessVal =
        registry.get("pes.authenticationStatus").tag("status", "success").counter().count();

    assertThat(authSuccessVal).isEqualTo(0.0);
  }

  @Test
  public void incrementEndorsementCounter_notApproved_doesNotIncrement() {
    systemMetrics.incrementEndorsementCounter("pub1");
    assertThat(registry.find("pes.endorsementGeneration").tag("publisher_id", "pub1").counter())
        .isNull();
  }

  @Test
  public void incrementEndorsementCounter_approved_increments() {
    systemMetrics.approvePublisher("pub1");
    systemMetrics.incrementEndorsementCounter("pub1");
    systemMetrics.incrementEndorsementCounter("pub1");

    double val =
        registry.get("pes.endorsementGeneration").tag("publisher_id", "pub1").counter().count();
    assertThat(val).isEqualTo(2.0);
  }

  @Test
  public void approvePublisher_limitEnforced() {
    for (int i = 0; i < 12; i++) {
      systemMetrics.approvePublisher("pub" + i);
    }

    // First 10 should be approved and incrementable
    for (int i = 0; i < 10; i++) {
      systemMetrics.incrementEndorsementCounter("pub" + i);
      double val =
          registry
              .get("pes.endorsementGeneration")
              .tag("publisher_id", "pub" + i)
              .counter()
              .count();
      assertThat(val).isEqualTo(1.0);
    }

    // 11th and 12th should not be approved
    systemMetrics.incrementEndorsementCounter("pub10");
    systemMetrics.incrementEndorsementCounter("pub11");

    assertThat(registry.find("pes.endorsementGeneration").tag("publisher_id", "pub10").counter())
        .isNull();
    assertThat(registry.find("pes.endorsementGeneration").tag("publisher_id", "pub11").counter())
        .isNull();
  }

  @Test
  public void approvePublisher_nullPublisher_ignored() {
    systemMetrics.approvePublisher(null);
    systemMetrics.incrementEndorsementCounter(null);
    // Should not crash and not register anything
  }

  @Test
  public void setRootCertificateValidity_registersGaugeOnFirstCall() {
    assertThat(registry.find("pes.root_certificate_validity_seconds").gauge()).isNull();

    systemMetrics.setRootCertificateValidity(Duration.ofMinutes(5));

    io.micrometer.core.instrument.Gauge gauge =
        registry.get("pes.root_certificate_validity_seconds").gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(300.0);

    systemMetrics.setRootCertificateValidity(Duration.ofMinutes(10));
    assertThat(gauge.value()).isEqualTo(600.0);
  }

  @Test
  public void setReloadStatus_updatesFailureGauge() {
    io.micrometer.core.instrument.Gauge gauge =
        registry.get("pes.certificate_reload_failed").gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(0.0);

    systemMetrics.setReloadStatus(com.google.mbs.domain.Metrics.ReloadStatus.FAILURE);
    assertThat(gauge.value()).isEqualTo(1.0);

    systemMetrics.setReloadStatus(com.google.mbs.domain.Metrics.ReloadStatus.SUCCESS);
    assertThat(gauge.value()).isEqualTo(0.0);
  }
}
