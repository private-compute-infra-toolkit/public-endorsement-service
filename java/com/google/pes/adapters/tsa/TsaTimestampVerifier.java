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
import com.google.protobuf.ByteString;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

/**
 * Adapter-layer interface for verifying Bouncy Castle RFC 3161 {@link TimeStampResponse} and {@link
 * TimeStampToken} objects.
 */
public interface TsaTimestampVerifier {

  /**
   * Validates a Bouncy Castle {@link TimeStampResponse} against the corresponding {@link
   * TimeStampRequest} and performs full cryptographic and clock-skew verification on the embedded
   * timestamp token.
   *
   * @param response the Bouncy Castle timestamp response
   * @param request the Bouncy Castle timestamp request that elicited the response
   * @return the validated domain {@link com.google.pes.domain.model.TimeStampToken}
   * @throws TsaException if validation fails
   */
  com.google.pes.domain.model.TimeStampToken extractTokenAndVerify(
      TimeStampResponse response, TimeStampRequest request);

  /**
   * Verifies an existing Bouncy Castle {@link TimeStampToken} against the expected timestamped
   * data, checking that the token's message imprint algorithm OID is approved, the message imprint
   * digest matches the hash of {@code expectedData}, the CMS signature is valid, the signer
   * certificate has the critical {@code id-kp-timeStamping} extended key usage, and the PKIX
   * certificate chain was valid at {@code genTime} (the moment of signing) up to a configured trust
   * anchor.
   *
   * <p>Unlike {@link #extractTokenAndVerify}, this method does not check {@code genTime} clock skew
   * against the current wall-clock time, allowing historical and offline verification of timestamp
   * tokens.
   *
   * @param token the Bouncy Castle timestamp token to verify
   * @param expectedData the expected data that was timestamped
   * @throws TsaException if verification fails
   */
  void verify(TimeStampToken token, ByteString expectedData);

  /**
   * Verifies a domain {@link com.google.pes.domain.model.TimeStampToken} by parsing its DER-encoded
   * bytes into a Bouncy Castle {@link TimeStampToken} and delegating to {@link
   * #verify(TimeStampToken, ByteString)}.
   *
   * @param token the domain timestamp token to verify
   * @param expectedData the expected data that was timestamped
   * @throws TsaException if the token bytes are malformed or verification fails
   */
  void verify(com.google.pes.domain.model.TimeStampToken token, ByteString expectedData);
}
