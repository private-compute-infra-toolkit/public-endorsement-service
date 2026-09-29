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

package com.google.pes.domain.ports;

import com.google.pes.domain.model.TimeStampToken;
import com.google.protobuf.ByteString;
import java.util.Optional;

/**
 * Port for interacting with an RFC 3161 Time-Stamping Authority (TSA).
 *
 * <p>Implementations of this interface must be thread-safe.
 */
public interface TsaClient {

  /**
   * Requests an RFC 3161 timestamp token for the provided data.
   *
   * <p>TODO: RFC 3161 response validation and CMS signature verification against trusted root
   * certificates.
   *
   * @param data the non-empty data to be timestamped
   * @return an {@link Optional} containing the {@link TimeStampToken} for {@code data}, or {@link
   *     Optional#empty()} strictly when TSA timestamping is disabled by configuration (never null)
   * @throws NullPointerException if {@code data} is null
   * @throws IllegalArgumentException if {@code data} is empty
   * @throws TsaException if communicating with the TSA or parsing the response fails
   */
  Optional<TimeStampToken> requestTimeStampToken(ByteString data);
}
