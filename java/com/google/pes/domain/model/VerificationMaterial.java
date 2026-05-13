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

/** Represents a verification material used to verify signatures. */
public record VerificationMaterial(ByteString content, Format format) {
  public enum Format {
    /** Undefined and hence invalid format of the verification material. */
    FORMAT_UNSPECIFIED,
    /** X.509 certificate in DER format. */
    X509_DER,
    /** ECDSA Public key on P-256 curve in SPKI format. */
    ECDSA_P256_SHA256
  }
}
