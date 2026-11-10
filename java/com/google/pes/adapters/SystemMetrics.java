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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.mbs.domain.CrossSignedCertificateId;
import com.google.mbs.domain.Metrics.ConfigEvent;
import com.google.mbs.domain.Metrics.GenerationEvent;
import com.google.mbs.domain.Metrics.LoadEvent;
import com.google.mbs.domain.Metrics.MbsEvent;
import com.google.pes.domain.metric.AuthenticationStatus;
import com.google.pes.domain.metric.Metrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Singleton
public class SystemMetrics implements Metrics, com.google.mbs.domain.Metrics {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String PREFIX = "pes.";
  private static final String ACTIVE_SELF_SIGNED_CERT_METRIC =
      PREFIX + "mbsActiveSelfSignedCertificate";
  private static final String ACTIVE_CROSS_SIGNED_CERT_METRIC =
      PREFIX + "mbsActiveCrossSignedCertificate";
  private static final String EPOCH_TAG = "epoch";
  private static final int MAX_APPROVED_PUBLISHERS = 10;

  // Caps the number of distinct "epoch" tag values exported per gauge over the
  // process lifetime (not concurrently active ones) to bound metrics export cost.
  // Resets on restart. We expect <10 in practice.
  // Note: MeterFilter.maximumAllowableTags matches by meter name prefix, so any
  // future meter sharing these names as a prefix and carrying an "epoch" tag
  // shares the same budget.
  @VisibleForTesting static final int MAX_ACTIVE_CERTIFICATE_TAGS = 100;

  private final PrometheusMeterRegistry registry;
  private final Counter[] authenticationCounter;
  private final AtomicInteger[] mbsStatusValues;
  private final AtomicInteger[] mbsGenerationStatusValues;
  private final AtomicInteger[] mbsLoadStatusValues;
  private final AtomicInteger[] mbsConfigStatusValues;
  private final MultiGauge mbsActiveSelfSignedCertificates;
  private final MultiGauge mbsActiveCrossSignedCertificates;
  private final Set<String> approvedPublishers = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<String, Counter> endorsementCounters = new ConcurrentHashMap<>();
  private final AtomicLong rootCertificateValiditySeconds = new AtomicLong(0);
  private final AtomicBoolean rootCertificateValiditySecondsRegistered = new AtomicBoolean(false);

  @Inject
  public SystemMetrics(PrometheusMeterRegistry registry) {
    this.registry = registry;
    configureCertificateTagLimitFilters();
    this.authenticationCounter =
        createCounters(PREFIX + "authenticationStatus", "status", AuthenticationStatus.class);
    this.mbsStatusValues = createGauges(PREFIX + "mbsStatus", "status", MbsEvent.class);
    this.mbsGenerationStatusValues =
        createGauges(PREFIX + "mbsGenerationStatus", "status", GenerationEvent.class);
    this.mbsLoadStatusValues = createGauges(PREFIX + "mbsLoadStatus", "status", LoadEvent.class);
    this.mbsConfigStatusValues =
        createGauges(PREFIX + "mbsConfigStatus", "status", ConfigEvent.class);
    this.mbsActiveSelfSignedCertificates =
        MultiGauge.builder(ACTIVE_SELF_SIGNED_CERT_METRIC)
            .description("Active self-signed certificate epochs")
            .register(registry);
    this.mbsActiveCrossSignedCertificates =
        MultiGauge.builder(ACTIVE_CROSS_SIGNED_CERT_METRIC)
            .description("Active cross-signed certificates")
            .register(registry);
  }

  private <E extends Enum<E>> Counter[] createCounters(
      String name, String tagKey, Class<E> enumClass) {
    E[] constants = enumClass.getEnumConstants();
    Counter[] array = new Counter[constants.length];
    for (E item : constants) {
      array[item.ordinal()] =
          Counter.builder(name)
              .tag(tagKey, item.name().toLowerCase())
              .description("Metrics tracking for " + name)
              .register(registry);
    }
    return array;
  }

  private <E extends Enum<E>> AtomicInteger[] createGauges(
      String name, String tagKey, Class<E> enumClass) {
    E[] constants = enumClass.getEnumConstants();
    AtomicInteger[] array = new AtomicInteger[constants.length];
    for (E item : constants) {
      array[item.ordinal()] = new AtomicInteger(0);
      Gauge.builder(name, array[item.ordinal()], AtomicInteger::get)
          .tag(tagKey, item.name().toLowerCase())
          .description("Metrics tracking for " + name)
          .register(registry);
    }
    return array;
  }

  @Override
  public void incrementAuthenticationCounter(AuthenticationStatus status) {
    if (status != null) {
      authenticationCounter[status.ordinal()].increment();
    }
  }

  @Override
  public void approvePublisher(String publisherId) {
    if (publisherId == null) {
      return;
    }
    if (approvedPublishers.contains(publisherId)) {
      return;
    }
    synchronized (this) {
      if (approvedPublishers.size() < MAX_APPROVED_PUBLISHERS) {
        approvedPublishers.add(publisherId);
      } else {
        logger.atWarning().log(
            "Max approved publishers limit (%d) reached. Rejecting publisher: %s",
            MAX_APPROVED_PUBLISHERS, publisherId);
      }
    }
  }

  @Override
  public void incrementEndorsementCounter(String publisherId) {
    if (publisherId != null && approvedPublishers.contains(publisherId)) {
      endorsementCounters
          .computeIfAbsent(
              publisherId,
              id ->
                  Counter.builder(PREFIX + "endorsementGeneration")
                      .tag("publisher_id", id)
                      .description("Number of endorsements generated per publisher")
                      .register(registry))
          .increment();
    }
  }

  @Override
  public void recordEvent(MbsEvent event) {
    if (event != null) {
      for (MbsEvent e : MbsEvent.class.getEnumConstants()) {
        mbsStatusValues[e.ordinal()].set(e == event ? 1 : 0);
      }
    }
  }

  @Override
  public void recordGenerationEvent(GenerationEvent event) {
    if (event != null) {
      for (GenerationEvent e : GenerationEvent.class.getEnumConstants()) {
        mbsGenerationStatusValues[e.ordinal()].set(e == event ? 1 : 0);
      }
    }
  }

  @Override
  public void recordLoadEvent(LoadEvent event) {
    if (event != null) {
      for (LoadEvent e : LoadEvent.class.getEnumConstants()) {
        mbsLoadStatusValues[e.ordinal()].set(e == event ? 1 : 0);
      }
    }
  }

  @Override
  public void recordConfigEvent(ConfigEvent event) {
    if (event != null) {
      for (ConfigEvent e : ConfigEvent.class.getEnumConstants()) {
        mbsConfigStatusValues[e.ordinal()].set(e == event ? 1 : 0);
      }
    }
  }

  @Override
  public void recordActiveSelfSignedCertificates(Set<Integer> epochIds) {
    if (epochIds != null) {
      overwriteRows(
          mbsActiveSelfSignedCertificates, EPOCH_TAG, epochIds.stream().map(String::valueOf));
    }
  }

  @Override
  public void recordActiveCrossSignedCertificates(Set<CrossSignedCertificateId> certificateIds) {
    if (certificateIds != null) {
      overwriteRows(
          mbsActiveCrossSignedCertificates,
          EPOCH_TAG,
          certificateIds.stream().map(CrossSignedCertificateId::toKeyString));
    }
  }

  private static void overwriteRows(MultiGauge gauge, String tagKey, Stream<String> tagValues) {
    gauge.register(
        tagValues.map(value -> MultiGauge.Row.of(Tags.of(tagKey, value), 1)).toList(),
        /* overwrite= */ true);
  }

  @Override
  public void setRootCertificateValidity(Duration remaining) {
    rootCertificateValiditySeconds.set(remaining.toSeconds());
    if (rootCertificateValiditySecondsRegistered.compareAndSet(false, true)) {
      Gauge.builder(
              PREFIX + "root_certificate_validity_seconds",
              rootCertificateValiditySeconds,
              AtomicLong::get)
          .description("Seconds remaining until the root certificate expires")
          .register(registry);
    }
  }

  private void configureCertificateTagLimitFilters() {
    MeterFilter onLimitReached = denyWithWarning();
    registry
        .config()
        .meterFilter(
            MeterFilter.maximumAllowableTags(
                ACTIVE_SELF_SIGNED_CERT_METRIC,
                EPOCH_TAG,
                MAX_ACTIVE_CERTIFICATE_TAGS,
                onLimitReached))
        .meterFilter(
            MeterFilter.maximumAllowableTags(
                ACTIVE_CROSS_SIGNED_CERT_METRIC,
                EPOCH_TAG,
                MAX_ACTIVE_CERTIFICATE_TAGS,
                onLimitReached));
  }

  private static MeterFilter denyWithWarning() {
    return new MeterFilter() {
      @Override
      public MeterFilterReply accept(Meter.Id id) {
        logger.atWarning().atMostEvery(2, TimeUnit.MINUTES).log(
            "Tag count limit (%d) reached for %s; dropping %s",
            MAX_ACTIVE_CERTIFICATE_TAGS, id.getName(), id.getTags());
        return MeterFilterReply.DENY;
      }
    };
  }
}
