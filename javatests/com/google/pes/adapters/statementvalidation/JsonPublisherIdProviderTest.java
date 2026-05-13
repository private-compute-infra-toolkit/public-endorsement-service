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

package com.google.pes.adapters.statementvalidation;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JsonPublisherIdProviderTest {

  private final JsonPublisherIdProvider validator = new JsonPublisherIdProvider(new ObjectMapper());
  private static final String TEST_DATA_BASE_DIR = "com/google/pes/testdata/intoto_v1_examples";

  private static final String VALID_PREDICATE_JSON =
      """
      "predicateType": "https://project-oak.github.io/oak/tr/endorsement/v1",
      "predicate": {
        "issuedOn": "2024-10-07T06:44:22.459000Z",
        "validity": {
          "notBefore": "2024-10-07T06:44:22.459000Z",
          "notAfter": "2025-10-07T06:44:22.459000Z"
        },
        "claims": [
          {
            "type": "https://github.com/pcit/pes/docs/claims/v1/publisher.md",
            "annotations": { "publisher_id": "google.com" }
          }
        ]
      }
      """;

  private ByteString loadTestData(String filePath) throws Exception {
    String resourcePath = "/" + TEST_DATA_BASE_DIR + "/" + filePath;
    InputStream inputStream = getClass().getResourceAsStream(resourcePath);
    if (inputStream == null) {
      throw new RuntimeException("Cannot find test data file: " + resourcePath);
    }
    return ByteString.readFrom(inputStream);
  }

  @Test
  public void getValidPublisherId_correct1_succeeds() throws Exception {
    ByteString statementBytes = loadTestData("valid/correct_only_publisher_claim.json");
    String publisherId = validator.getValidPublisherId(statementBytes);
    assertThat(publisherId).isEqualTo("google.com");
  }

  @Test
  public void getValidPublisherId_correct2_succeeds() throws Exception {
    ByteString statementBytes = loadTestData("valid/correct_multiple_claims.json");
    String publisherId = validator.getValidPublisherId(statementBytes);
    assertThat(publisherId).isEqualTo("release@google.com");
  }

  @Test
  public void getValidPublisherId_missingPublisherClaim_throws() throws Exception {
    ByteString statementBytes = loadTestData("invalid/missing_publisher_claim.json");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
    assertThat(e).hasMessageThat().contains("publisher claim");
  }

  @Test
  public void getValidPublisherId_missingPublisherId_throws() throws Exception {
    ByteString statementBytes = loadTestData("invalid/missing_publisher_id.json");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
    assertThat(e).hasMessageThat().contains("publisher_id");
  }

  @Test
  public void getValidPublisherId_multiplePublisherClaims_throws() throws Exception {
    ByteString statementBytes = loadTestData("invalid/multiple_publisher_claim.json");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
    assertThat(e).hasMessageThat().contains("publisher claim");
  }

  @Test
  public void getValidPublisherId_wrongStatementType_throws() throws Exception {
    ByteString statementBytes =
        ByteString.copyFromUtf8(
            "{ \"_type\": \"wrongType\", \"subject\": [{\"name\":\"n\", \"digest\":{\"sha\" :"
                + " \"not-sha\"}}], "
                + VALID_PREDICATE_JSON
                + " }");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
    assertThat(e).hasMessageThat().contains("_type");
  }

  @Test
  public void getValidPublisherId_wrongPredicateType_throws() throws Exception {
    String validJson = loadTestData("valid/correct_only_publisher_claim.json").toStringUtf8();
    String invalidJson =
        validJson.replace(
            "\"predicateType\": \"https://project-oak.github.io/oak/tr/endorsement/v1\"",
            "\"predicateType\": \"wrongType\"");
    ByteString statementBytes = ByteString.copyFromUtf8(invalidJson);

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
    assertThat(e).hasMessageThat().contains("predicateType");
  }

  @Test
  public void getValidPublisherId_invalidJson_throws() {
    ByteString statementBytes = ByteString.copyFromUtf8("{ \"key\": \"value\", ");
    assertThrows(
        IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
  }

  @Test
  public void getValidPublisherId_emptyBytes_throws() {
    ByteString statementBytes = ByteString.EMPTY;
    assertThrows(
        IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
  }

  @Test
  public void getValidPublisherId_notJsonObject_throws() {
    ByteString statementBytes = ByteString.copyFromUtf8("[]");
    assertThrows(
        IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
  }

  @Test
  public void getValidPublisherId_missingType_throws() throws Exception {
    String validJson = loadTestData("valid/correct_only_publisher_claim.json").toStringUtf8();
    String invalidJson = validJson.replace("_type", "not-_type");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.getValidPublisherId(ByteString.copyFromUtf8(invalidJson)));
    assertThat(e).hasMessageThat().contains("_type");
  }

  @Test
  public void getValidPublisherId_subjectNotArray_throws() {
    ByteString statementBytes =
        ByteString.copyFromUtf8(
            "{ \"_type\": \"https://in-toto.io/Statement/v1\", \"subject\": {}, "
                + VALID_PREDICATE_JSON
                + " }");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
    assertThat(e).hasMessageThat().contains("subject");
  }

  @Test
  public void getValidPublisherId_subjectEmptyArray_throws() {
    ByteString statementBytes =
        ByteString.copyFromUtf8(
            "{ \"_type\": \"https://in-toto.io/Statement/v1\", \"subject\": [], "
                + VALID_PREDICATE_JSON
                + " }");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
    assertThat(e).hasMessageThat().contains("subject");
  }

  @Test
  public void getValidPublisherId_subjectElementNotObject_throws() {
    ByteString statementBytes =
        ByteString.copyFromUtf8(
            "{ \"_type\": \"https://in-toto.io/Statement/v1\", \"subject\": [\"test\"], "
                + VALID_PREDICATE_JSON
                + " }");
    assertThrows(
        IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
  }

  @Test
  public void getValidPublisherId_multipleSubjects_throws() throws Exception {
    ByteString statementBytes = loadTestData("invalid/invalid_multiple_resources.json");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
    assertThat(e).hasMessageThat().contains("subject");
  }

  @Test
  public void getValidPublisherId_missingClaims_throws() throws Exception {
    String validJson = loadTestData("valid/correct_only_publisher_claim.json").toStringUtf8();
    String invalidJson = validJson.replace("claims", "not-claims");
    ByteString statementBytes = ByteString.copyFromUtf8(invalidJson);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
    assertThat(e).hasMessageThat().contains("claims");
  }

  @Test
  public void getValidPublisherId_nullPublisherId_throws() throws Exception {
    String validJson = loadTestData("valid/correct_only_publisher_claim.json").toStringUtf8();
    String invalidJson = validJson.replace("\"google.com\"", "null");
    ByteString statementBytes = ByteString.copyFromUtf8(invalidJson);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> validator.getValidPublisherId(statementBytes));
    assertThat(e).hasMessageThat().contains("publisher_id");
  }

  @Test
  public void getValidPublisherId_emptyPublisherId_succeeds() throws Exception {
    String validJson = loadTestData("valid/correct_only_publisher_claim.json").toStringUtf8();
    String invalidJson = validJson.replace("google.com", "");
    ByteString statementBytes = ByteString.copyFromUtf8(invalidJson);

    String publisherId = validator.getValidPublisherId(statementBytes);
    assertThat(publisherId).isEmpty();
  }
}
