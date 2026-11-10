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

import com.google.mbs.domain.CrossSignedCertificateId;
import com.google.mbs.domain.Metrics.ConfigEvent;
import com.google.mbs.domain.Metrics.GenerationEvent;
import com.google.mbs.domain.Metrics.LoadEvent;
import com.google.mbs.domain.Metrics.MbsEvent;
import com.google.pes.domain.metric.AuthenticationStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    for (MbsEvent event : MbsEvent.values()) {
      double value =
          registry.get("pes.mbsStatus").tag("status", event.name().toLowerCase()).gauge().value();
      assertThat(value).isEqualTo(0.0);
    }

    for (GenerationEvent event : GenerationEvent.values()) {
      double value =
          registry
              .get("pes.mbsGenerationStatus")
              .tag("status", event.name().toLowerCase())
              .gauge()
              .value();
      assertThat(value).isEqualTo(0.0);
    }

    for (LoadEvent event : LoadEvent.values()) {
      double value =
          registry
              .get("pes.mbsLoadStatus")
              .tag("status", event.name().toLowerCase())
              .gauge()
              .value();
      assertThat(value).isEqualTo(0.0);
    }

    for (ConfigEvent event : ConfigEvent.values()) {
      double value =
          registry
              .get("pes.mbsConfigStatus")
              .tag("status", event.name().toLowerCase())
              .gauge()
              .value();
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
  public void recordMbsEvents_updatesExclusiveStateGauges() {
    systemMetrics.recordGenerationEvent(GenerationEvent.KMS_OPERATION_FAILED);
    assertThat(
            registry
                .get("pes.mbsGenerationStatus")
                .tag("status", "kms_operation_failed")
                .gauge()
                .value())
        .isEqualTo(1.0);
    assertThat(registry.get("pes.mbsGenerationStatus").tag("status", "success").gauge().value())
        .isEqualTo(0.0);

    systemMetrics.recordGenerationEvent(GenerationEvent.SUCCESS);
    assertThat(
            registry
                .get("pes.mbsGenerationStatus")
                .tag("status", "kms_operation_failed")
                .gauge()
                .value())
        .isEqualTo(0.0);
    assertThat(registry.get("pes.mbsGenerationStatus").tag("status", "success").gauge().value())
        .isEqualTo(1.0);

    systemMetrics.recordLoadEvent(LoadEvent.BACKUP_NOT_FOUND);
    assertThat(registry.get("pes.mbsLoadStatus").tag("status", "backup_not_found").gauge().value())
        .isEqualTo(1.0);
    assertThat(registry.get("pes.mbsLoadStatus").tag("status", "success").gauge().value())
        .isEqualTo(0.0);

    systemMetrics.recordConfigEvent(ConfigEvent.INVALID_CONFIGURATION_FILE);
    assertThat(
            registry
                .get("pes.mbsConfigStatus")
                .tag("status", "invalid_configuration_file")
                .gauge()
                .value())
        .isEqualTo(1.0);
    assertThat(registry.get("pes.mbsConfigStatus").tag("status", "success").gauge().value())
        .isEqualTo(0.0);

    String prometheusScrape = registry.scrape();
    assertThat(prometheusScrape).contains("pes_mbsGenerationStatus{status=\"success\"} 1.0");
    assertThat(prometheusScrape).contains("pes_mbsLoadStatus{status=\"backup_not_found\"} 1.0");
    assertThat(prometheusScrape)
        .contains("pes_mbsConfigStatus{status=\"invalid_configuration_file\"} 1.0");

    // Null inputs are safely ignored
    systemMetrics.recordEvent(MbsEvent.SUCCESS);
    systemMetrics.recordEvent(null);
    systemMetrics.recordGenerationEvent(null);
    systemMetrics.recordLoadEvent(null);
    systemMetrics.recordConfigEvent(null);
    assertThat(registry.get("pes.mbsStatus").tag("status", "success").gauge().value())
        .isEqualTo(1.0);
  }

  @Test
  public void recordActiveSelfSignedCertificates_reportsOnlyMostRecentValues() {
    String metric = "pes.mbsActiveSelfSignedCertificate";

    systemMetrics.recordActiveSelfSignedCertificates(Set.of(0, 1));
    assertThat(activeEpochGauges(metric)).containsExactly("0", 1.0, "1", 1.0);

    systemMetrics.recordActiveSelfSignedCertificates(Set.of(1, 2));
    assertThat(activeEpochGauges(metric)).containsExactly("1", 1.0, "2", 1.0);
  }

  @Test
  public void recordActiveCrossSignedCertificates_reportsOnlyMostRecentValues() {
    String metric = "pes.mbsActiveCrossSignedCertificate";
    CrossSignedCertificateId cert01 = new CrossSignedCertificateId(0, 1);
    CrossSignedCertificateId cert10 = new CrossSignedCertificateId(1, 0);
    CrossSignedCertificateId cert12 = new CrossSignedCertificateId(1, 2);

    systemMetrics.recordActiveCrossSignedCertificates(Set.of(cert01, cert10));
    assertThat(activeEpochGauges(metric)).containsExactly("0_1", 1.0, "1_0", 1.0);

    systemMetrics.recordActiveCrossSignedCertificates(Set.of(cert10, cert12));
    assertThat(activeEpochGauges(metric)).containsExactly("1_0", 1.0, "1_2", 1.0);
  }

  @Test
  public void recordActiveSelfSignedCertificates_capsLifetimeTagCount() {
    int retired = SystemMetrics.MAX_ACTIVE_CERTIFICATE_TAGS - 10;
    systemMetrics.recordActiveSelfSignedCertificates(epochs(0, retired));

    systemMetrics.recordActiveSelfSignedCertificates(epochs(retired, retired + 50));

    Map<String, Double> active = activeEpochGauges("pes.mbsActiveSelfSignedCertificate");
    assertThat(active).hasSize(10);
    assertThat(active.values()).containsExactlyElementsIn(Collections.nCopies(10, 1.0));
  }

  @Test
  public void recordActiveCrossSignedCertificates_capsLifetimeTagCount() {
    int retired = SystemMetrics.MAX_ACTIVE_CERTIFICATE_TAGS - 10;
    systemMetrics.recordActiveCrossSignedCertificates(crossSignedCerts(0, retired));

    systemMetrics.recordActiveCrossSignedCertificates(crossSignedCerts(retired, retired + 50));

    Map<String, Double> active = activeEpochGauges("pes.mbsActiveCrossSignedCertificate");
    assertThat(active).hasSize(10);
    assertThat(active.values()).containsExactlyElementsIn(Collections.nCopies(10, 1.0));
  }

  private static Set<Integer> epochs(int startInclusive, int endExclusive) {
    return IntStream.range(startInclusive, endExclusive).boxed().collect(Collectors.toSet());
  }

  private static Set<CrossSignedCertificateId> crossSignedCerts(
      int startInclusive, int endExclusive) {
    return IntStream.range(startInclusive, endExclusive)
        .mapToObj(i -> new CrossSignedCertificateId(0, i + 1))
        .collect(Collectors.toSet());
  }

  private Map<String, Double> activeEpochGauges(String metricName) {
    return registry.find(metricName).gauges().stream()
        .collect(Collectors.toMap(g -> g.getId().getTag("epoch"), Gauge::value));
  }
}
