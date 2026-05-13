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

import com.google.pes.domain.model.Endorsement;
import com.google.pes.domain.model.TLogReceipt;

/** Port for interacting with a Transparency Log. */
public interface TLog {
  /**
   * Posts an endorsement to the Transparency Log.
   *
   * @param endorsement The endorsement to be posted.
   * @return A receipt from the Transparency Log confirming the entry was posted successfully.
   */
  TLogReceipt post(Endorsement endorsement);
}
