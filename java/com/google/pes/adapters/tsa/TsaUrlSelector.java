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

import com.google.pes.domain.ports.TsaException;
import java.net.URI;
import java.util.Optional;

/**
 * Adapter-layer strategy interface for selecting a TSA endpoint URL.
 *
 * <p>This interface is intended for internal use within the TSA adapter package (e.g. by HTTP TSA
 * client adapters) to load-balance requests across configured endpoints, rather than being exposed
 * as a domain port.
 */
@FunctionalInterface
public interface TsaUrlSelector {

  /**
   * Selects a TSA URL.
   *
   * @return an {@link Optional} containing the selected TSA {@link URI}, or {@link
   *     Optional#empty()} if TSA is disabled in configuration
   * @throws TsaException if TSA configuration is null or if TSA is enabled but no TSA URLs are
   *     available in configuration
   */
  Optional<URI> selectUrl();
}
