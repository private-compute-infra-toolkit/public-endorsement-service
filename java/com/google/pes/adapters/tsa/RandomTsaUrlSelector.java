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

import com.google.common.annotations.VisibleForTesting;
import com.google.pes.domain.model.TsaConfig;
import com.google.pes.domain.ports.TsaConfigProvider;
import com.google.pes.domain.ports.TsaException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Implementation of {@link TsaUrlSelector} that selects a TSA URL uniformly at random from a {@link
 * TsaConfigProvider} each time a URL is requested.
 */
@Singleton
public class RandomTsaUrlSelector implements TsaUrlSelector {
  private final TsaConfigProvider configProvider;
  private final Supplier<RandomGenerator> randomGeneratorSupplier;

  @Inject
  public RandomTsaUrlSelector(TsaConfigProvider configProvider) {
    this(configProvider, ThreadLocalRandom::current);
  }

  @VisibleForTesting
  RandomTsaUrlSelector(TsaConfigProvider configProvider, RandomGenerator randomGenerator) {
    this(configProvider, () -> randomGenerator);
    Objects.requireNonNull(randomGenerator, "randomGenerator cannot be null");
  }

  private RandomTsaUrlSelector(
      TsaConfigProvider configProvider, Supplier<RandomGenerator> randomGeneratorSupplier) {
    this.configProvider = Objects.requireNonNull(configProvider, "configProvider cannot be null");
    this.randomGeneratorSupplier =
        Objects.requireNonNull(randomGeneratorSupplier, "randomGeneratorSupplier cannot be null");
  }

  @Override
  public Optional<URI> selectUrl() {
    TsaConfig config = configProvider.getConfig();
    if (config == null) {
      throw new TsaException("TSA configuration is null");
    }
    if (!config.enabled()) {
      return Optional.empty();
    }
    List<URI> uris = config.tsaUrls();
    if (uris.isEmpty()) {
      throw new TsaException("No TSA URLs available in configuration");
    }
    if (uris.size() == 1) {
      return Optional.of(uris.get(0));
    }
    return Optional.of(uris.get(randomGeneratorSupplier.get().nextInt(uris.size())));
  }
}
