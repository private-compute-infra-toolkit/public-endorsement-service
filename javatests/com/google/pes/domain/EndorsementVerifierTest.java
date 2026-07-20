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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.pes.domain.model.Endorsement;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.model.VerifiedEndorsement;
import com.google.pes.domain.ports.PublisherIdProvider;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class EndorsementVerifierTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private PublisherIdProvider mockPublisherIdProvider;
  @Mock private PublisherVerifier mockPublisherVerifier;
  @Mock private X509Certificate mockRootCertificate;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private EndorsementVerifier verifier;

  private static final String TEST_PUBLISHER_ID = "role@example.com";
  private static final String TEST_ISSUER = "test-issuer";
  private static final String TEST_SUBJECT = "test-subject";
  private static final CallerIdentity TEST_IDENTITY =
      new CallerIdentity(TEST_ISSUER, TEST_SUBJECT, java.util.Set.of("test-audience"));

  private static ByteString loadTestData(String filePath) {
    String resourcePath = "/com/google/pes/testdata/intoto_v1_examples/" + filePath;
    try (InputStream inputStream =
        EndorsementVerifierTest.class.getResourceAsStream(resourcePath)) {
      if (inputStream == null) {
        throw new IllegalArgumentException("Cannot find test data file: " + resourcePath);
      }
      return ByteString.readFrom(inputStream);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final Statement TEST_STATEMENT =
      new Statement(Statement.Format.JSON_INTOTO, loadTestData("valid/statement.json"));

  private static final VerificationMaterial TEST_MATERIAL =
      new VerificationMaterial(
          ByteString.copyFromUtf8("test-material"), VerificationMaterial.Format.ECDSA_P256_SHA256);
  private static final ByteString TEST_SIGNATURE_BYTES = ByteString.copyFromUtf8("test-signature");

  private static final Signature TEST_SIGNATURE =
      new Signature(TEST_SIGNATURE_BYTES, TEST_MATERIAL);

  private static final Endorsement TEST_ENDORSEMENT =
      new Endorsement("name", TEST_STATEMENT, TEST_SIGNATURE, List.of(), null);

  @Before
  public void setUp() {
    when(mockPublisherIdProvider.getValidPublisherId(any())).thenReturn(TEST_PUBLISHER_ID);
    when(mockRootCertificate.getNotAfter())
        .thenReturn(Date.from(Instant.parse("2026-12-31T23:59:59Z")));
    verifier =
        new EndorsementVerifier(
            Map.of(Statement.Format.JSON_INTOTO, () -> mockPublisherIdProvider),
            mockPublisherVerifier,
            mockRootCertificate,
            objectMapper);
  }

  @Test
  public void parseAndVerify_success() {
    VerifiedEndorsement verifiedEndorsement =
        verifier.parseAndVerify(
            TEST_ENDORSEMENT.statement(), TEST_IDENTITY, TEST_ENDORSEMENT.statementSignature());

    assertThat(verifiedEndorsement.publisherId()).isEqualTo(TEST_PUBLISHER_ID);
    verify(mockPublisherVerifier)
        .verify(TEST_PUBLISHER_ID, TEST_IDENTITY, TEST_ENDORSEMENT.statementSignature());
  }

  @Test
  public void parseAndVerify_unspecifiedFormat_throwsIllegalArgumentException() {
    Statement statement =
        new Statement(Statement.Format.FORMAT_UNSPECIFIED, ByteString.copyFromUtf8("statement"));

    assertThrows(
        IllegalArgumentException.class,
        () -> verifier.parseAndVerify(statement, TEST_IDENTITY, TEST_SIGNATURE));
    verifyNoInteractions(mockPublisherVerifier);
  }

  @Test
  public void parseAndVerify_invalidPredicateType_throwsIllegalArgumentException() {
    Statement statement =
        new Statement(
            Statement.Format.JSON_INTOTO, loadTestData("invalid/invalid_predicate_type.json"));
    Endorsement inputEndorsement =
        new Endorsement("name", statement, TEST_SIGNATURE, List.of(), null);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            verifier.parseAndVerify(
                inputEndorsement.statement(),
                TEST_IDENTITY,
                inputEndorsement.statementSignature()));
    verifyNoInteractions(mockPublisherVerifier);
  }

  @Test
  public void parseAndVerify_notAfterExceedsRootCert_throwsIllegalArgumentException() {
    when(mockRootCertificate.getNotAfter())
        .thenReturn(Date.from(Instant.parse("2026-08-31T23:59:59Z")));

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                verifier.parseAndVerify(
                    TEST_ENDORSEMENT.statement(),
                    TEST_IDENTITY,
                    TEST_ENDORSEMENT.statementSignature()));

    assertThat(thrown).hasMessageThat().contains("cannot be past root certificate expiration date");
    verifyNoInteractions(mockPublisherVerifier);
  }

  @Test
  public void parseAndVerify_notAfterBeforeRootCert_success() {
    when(mockRootCertificate.getNotAfter())
        .thenReturn(Date.from(Instant.parse("2026-10-31T23:59:59Z")));

    VerifiedEndorsement verifiedEndorsement =
        verifier.parseAndVerify(
            TEST_ENDORSEMENT.statement(), TEST_IDENTITY, TEST_ENDORSEMENT.statementSignature());

    assertThat(verifiedEndorsement.publisherId()).isEqualTo(TEST_PUBLISHER_ID);
    verify(mockPublisherVerifier)
        .verify(TEST_PUBLISHER_ID, TEST_IDENTITY, TEST_ENDORSEMENT.statementSignature());
  }

  @Test
  public void parseAndVerify_missingPredicateValidity_throwsIllegalArgumentException() {
    Statement statement = createStatementWithValidity(null);

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> verifier.parseAndVerify(statement, TEST_IDENTITY, TEST_SIGNATURE));

    assertThat(thrown).hasMessageThat().contains("Missing required creator property 'validity'");
    verifyNoInteractions(mockPublisherVerifier);
  }

  @Test
  public void parseAndVerify_missingNotAfter_throwsIllegalArgumentException() {
    Statement statement = createStatementWithValidity("{\"notBefore\": \"2026-06-01T00:00:00Z\"}");

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> verifier.parseAndVerify(statement, TEST_IDENTITY, TEST_SIGNATURE));

    assertThat(thrown).hasMessageThat().contains("Missing required creator property 'notAfter'");
    verifyNoInteractions(mockPublisherVerifier);
  }

  @Test
  public void parseAndVerify_nullRootCertificate_throwsIllegalArgumentException() {
    EndorsementVerifier verifierWithNullRoot =
        new EndorsementVerifier(
            Map.of(Statement.Format.JSON_INTOTO, () -> mockPublisherIdProvider),
            mockPublisherVerifier,
            /* rootCertificate= */ null,
            objectMapper);

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                verifierWithNullRoot.parseAndVerify(
                    TEST_ENDORSEMENT.statement(), TEST_IDENTITY, TEST_SIGNATURE));

    assertThat(thrown)
        .hasMessageThat()
        .contains("Failed to resolve root certificate expiration date");
    verifyNoInteractions(mockPublisherVerifier);
  }

  @Test
  public void parseAndVerify_nullRootCertificateNotAfter_throwsIllegalArgumentException() {
    when(mockRootCertificate.getNotAfter()).thenReturn(null);

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                verifier.parseAndVerify(
                    TEST_ENDORSEMENT.statement(), TEST_IDENTITY, TEST_SIGNATURE));

    assertThat(thrown)
        .hasMessageThat()
        .contains("Failed to resolve root certificate expiration date");
    verifyNoInteractions(mockPublisherVerifier);
  }

  @Test
  public void parseAndVerify_publisherIdProviderThrowsException_propagatesException() {
    when(mockPublisherIdProvider.getValidPublisherId(any()))
        .thenThrow(new IllegalArgumentException("Publisher ID error"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            verifier.parseAndVerify(
                TEST_ENDORSEMENT.statement(),
                TEST_IDENTITY,
                TEST_ENDORSEMENT.statementSignature()));
    verifyNoInteractions(mockPublisherVerifier);
  }

  @Test
  public void parseAndVerify_publisherVerifierThrowsException_propagatesException() {
    doThrow(new IllegalArgumentException("Publisher policy mismatch"))
        .when(mockPublisherVerifier)
        .verify(TEST_PUBLISHER_ID, TEST_IDENTITY, TEST_ENDORSEMENT.statementSignature());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            verifier.parseAndVerify(
                TEST_ENDORSEMENT.statement(),
                TEST_IDENTITY,
                TEST_ENDORSEMENT.statementSignature()));
  }

  private static Statement createStatementWithValidity(String validityJson) {
    String json =
        "{\n"
            + "  \"_type\": \"https://in-toto.io/Statement/v1\",\n"
            + "  \"subject\": [{\"name\": \"sub\", \"digest\": {\"sha256\": \"1234\"}}],\n"
            + "  \"predicateType\": \"https://project-oak.github.io/oak/tr/endorsement/v1\",\n"
            + "  \"predicate\": "
            + (validityJson != null
                ? "{\"issuedOn\": \"2026-06-01T00:00:00Z\", \"validity\": " + validityJson + "}"
                : "{\"issuedOn\": \"2026-06-01T00:00:00Z\"}")
            + "\n}";
    return new Statement(Statement.Format.JSON_INTOTO, ByteString.copyFromUtf8(json));
  }
}
