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

package com.google.pes.domain;

import static com.google.common.truth.Truth.assertThat;

import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.model.TLogReceipt;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.protobuf.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PreAuthenticationEncodingTest {
  @Test
  public void calculate_standardValues_matchesSpec() {
    ByteString serializedStatement = ByteString.copyFromUtf8("statement");
    Statement statement = new Statement(Statement.Format.JSON_INTOTO, serializedStatement);

    ByteString verificationMaterial = ByteString.copyFromUtf8("verification_material");
    ByteString signatureBytes = ByteString.copyFromUtf8("signature_bytes");
    Signature signature =
        new Signature(
            signatureBytes,
            new VerificationMaterial(verificationMaterial, VerificationMaterial.Format.X509_DER));

    TLogReceipt receipt = new TLogReceipt("log_123");

    ByteString result = PreAuthenticationEncoding.calculate(statement, signature, receipt);

    String expectedString =
        "PES" + " 9 statement" + " 21 verification_material" + " 15 signature_bytes" + " 7 log_123";

    assertThat(result).isEqualTo(ByteString.copyFromUtf8(expectedString));
  }

  @Test
  public void calculate_utf8LogId_countsBytesCorrectly() {
    Statement statement = new Statement(Statement.Format.JSON_INTOTO, ByteString.EMPTY);
    Signature signature =
        new Signature(
            ByteString.EMPTY,
            new VerificationMaterial(ByteString.EMPTY, VerificationMaterial.Format.X509_DER));

    // "abc\u20AC" -> 'a','b','c' (3 bytes) + '<euro sign>' (3 bytes in UTF-8: E2 82 AC) = 6 bytes
    String utf8Id = "abc\u20AC";
    TLogReceipt receipt = new TLogReceipt(utf8Id);

    ByteString result = PreAuthenticationEncoding.calculate(statement, signature, receipt);

    assertThat(result.toStringUtf8()).contains(" 6 " + utf8Id);
  }

  @Test
  public void calculate_emptyFields_handlesZeroLengths() {
    Statement statement = new Statement(Statement.Format.JSON_INTOTO, ByteString.EMPTY);
    Signature signature =
        new Signature(
            ByteString.EMPTY,
            new VerificationMaterial(ByteString.EMPTY, VerificationMaterial.Format.X509_DER));
    TLogReceipt receipt = new TLogReceipt("");

    ByteString result = PreAuthenticationEncoding.calculate(statement, signature, receipt);

    String expected = "PES 0  0  0  0 ";
    assertThat(result).isEqualTo(ByteString.copyFromUtf8(expected));
  }
}
