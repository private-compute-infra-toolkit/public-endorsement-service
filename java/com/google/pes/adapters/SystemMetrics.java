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

import com.google.common.flogger.FluentLogger;
import com.google.mbs.domain.Metrics.MbsEvent;
import com.google.pes.domain.metric.AuthenticationStatus;
import com.google.pes.domain.metric.Metrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class SystemMetrics implements Metrics, com.google.mbs.domain.Metrics {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String PREFIX = "pes.";
  private static final int MAX_APPROVED_PUBLISHERS = 10;

  private final PrometheusMeterRegistry registry;
  private final Counter[] authenticationCounter;
  private final AtomicInteger[] mbsStatusValues;
  private final Set<String> approvedPublishers = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<String, Counter> endorsementCounters = new ConcurrentHashMap<>();
  private final AtomicLong rootCertificateValiditySeconds = new AtomicLong(0);
  private final AtomicBoolean rootCertificateValiditySecondsRegistered = new AtomicBoolean(false);
  private final AtomicInteger certificateReloadFailed = new AtomicInteger(0);

  @Inject
  public SystemMetrics(PrometheusMeterRegistry registry) {
    this.registry = registry;
    this.authenticationCounter =
        createCounters(PREFIX + "authenticationStatus", "status", AuthenticationStatus.class);
    this.mbsStatusValues = createGauges(PREFIX + "mbsStatus", "status", MbsEvent.class);
    Gauge.builder(PREFIX + "certificate_reload_failed", certificateReloadFailed, AtomicInteger::get)
        .description("Indicates whether certificate reloading failed (0: success, 1: failed)")
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
  public void setReloadStatus(ReloadStatus status) {
    certificateReloadFailed.set(status == ReloadStatus.FAILURE ? 1 : 0);
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
}
