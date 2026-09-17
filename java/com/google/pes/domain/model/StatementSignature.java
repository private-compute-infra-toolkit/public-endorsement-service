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

package com.google.pes.domain.model;

import com.google.protobuf.ByteString;
import java.util.Objects;

/** A wrapper for a single signature of byte data (used for statement signature). */
public record StatementSignature(ByteString signature, VerificationMaterial verificationMaterial) {

  public StatementSignature {
    Objects.requireNonNull(signature, "signature cannot be null");
    Objects.requireNonNull(verificationMaterial, "verificationMaterial cannot be null");
  }
}
