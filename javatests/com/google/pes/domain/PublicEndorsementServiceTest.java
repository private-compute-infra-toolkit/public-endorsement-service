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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import com.google.pes.domain.model.Endorsement;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.model.TLogReceipt;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.InvalidSignatureException;
import com.google.pes.domain.ports.PesSignatureException;
import com.google.pes.domain.ports.PolicyException;
import com.google.pes.domain.ports.PublisherIdProvider;
import com.google.pes.domain.ports.SignatureGenerator;
import com.google.pes.domain.ports.SignatureVerifier;
import com.google.pes.domain.ports.TLog;
import com.google.pes.domain.ports.TLogException;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PublicEndorsementServiceTest {

  @Mock private TLog mockTLog;
  @Mock private PublisherIdProvider mockPublisherIdProvider;
  @Mock private PublisherVerifier mockPublisherVerifier;
  @Mock private SignatureGenerator mockSignatureGenerator;
  @Mock private SignatureVerifier mockSignatureVerifier;

  private PublicEndorsementService publicEndorsementService;
  Statement STATEMENT =
      new Statement(Statement.Format.JSON_INTOTO, ByteString.copyFromUtf8("statement"));
  Signature STATEMENT_SIGNATURE =
      new Signature(
          ByteString.copyFromUtf8("sig"),
          new VerificationMaterial(
              ByteString.copyFromUtf8("material"), VerificationMaterial.Format.ECDSA_P256_SHA256));
  Endorsement INPUT_ENDORSEMENT =
      new Endorsement("name", STATEMENT, STATEMENT_SIGNATURE, List.of(), null);

  private static final CallerIdentity TEST_IDENTITY =
      new CallerIdentity("issuer", "subject", java.util.Set.of("audience"));

  @Before
  public void setUp() {
    publicEndorsementService =
        new PublicEndorsementService(
            mockTLog,
            Map.of(
                Statement.Format.JSON_INTOTO,
                (Provider<PublisherIdProvider>) () -> mockPublisherIdProvider),
            mockPublisherVerifier,
            mockSignatureGenerator,
            mockSignatureVerifier);
  }

  @Test
  public void createEndorsement_success() {
    when(mockPublisherIdProvider.getValidPublisherId(any())).thenReturn("publisherId");
    TLogReceipt tLogReceipt = new TLogReceipt("logId");
    when(mockTLog.post(any())).thenReturn(tLogReceipt);

    Signature endorsementSignature =
        new Signature(
            ByteString.copyFromUtf8("pes-sig"),
            new VerificationMaterial(
                ByteString.copyFromUtf8("pes-material"),
                VerificationMaterial.Format.ECDSA_P256_SHA256));
    when(mockSignatureGenerator.generate(
            eq(PreAuthenticationEncoding.calculate(STATEMENT, STATEMENT_SIGNATURE, tLogReceipt))))
        .thenReturn(endorsementSignature);

    Endorsement result =
        publicEndorsementService.createEndorsement(INPUT_ENDORSEMENT, TEST_IDENTITY);

    assertThat(result.statement()).isEqualTo(STATEMENT);
    assertThat(result.statementSignature()).isEqualTo(STATEMENT_SIGNATURE);
    assertThat(result.endorsementSignatures()).containsExactly(endorsementSignature);
    assertThat(result.tLogReceipt()).isEqualTo(tLogReceipt);
    assertThat(result.name()).startsWith("endorsements/");

    verify(mockPublisherVerifier)
        .verify(eq("publisherId"), eq(TEST_IDENTITY), eq(STATEMENT_SIGNATURE));
    verify(mockSignatureVerifier).verify(eq(STATEMENT_SIGNATURE), eq(STATEMENT.serialized()));
  }

  @Test
  public void createEndorsement_unspecifiedFormat_throwsException() {
    Statement statement =
        new Statement(Statement.Format.FORMAT_UNSPECIFIED, ByteString.copyFromUtf8("statement"));
    Endorsement inputEndorsement =
        new Endorsement("name", statement, STATEMENT_SIGNATURE, List.of(), null);
    assertThrows(
        IllegalArgumentException.class,
        () -> publicEndorsementService.createEndorsement(inputEndorsement, TEST_IDENTITY));
  }

  @Test
  public void createEndorsement_tLogThrowsException_propagatesException() {
    when(mockPublisherIdProvider.getValidPublisherId(any())).thenReturn("publisherId");
    when(mockTLog.post(any())).thenThrow(new TLogException("TLog error"));

    assertThrows(
        TLogException.class,
        () -> publicEndorsementService.createEndorsement(INPUT_ENDORSEMENT, TEST_IDENTITY));
  }

  @Test
  public void createEndorsement_signatureVerifierThrowsException_propagatesException() {
    when(mockPublisherIdProvider.getValidPublisherId(any())).thenReturn("publisherId");
    doThrow(new InvalidSignatureException("Invalid sig"))
        .when(mockSignatureVerifier)
        .verify(any(), any());

    assertThrows(
        InvalidSignatureException.class,
        () -> publicEndorsementService.createEndorsement(INPUT_ENDORSEMENT, TEST_IDENTITY));
  }

  @Test
  public void createEndorsement_signerThrowsException_propagatesException() {
    when(mockPublisherIdProvider.getValidPublisherId(any())).thenReturn("publisherId");
    when(mockTLog.post(any())).thenReturn(new TLogReceipt("logId"));
    when(mockSignatureGenerator.generate(any()))
        .thenThrow(new PesSignatureException("Signer error"));

    assertThrows(
        PesSignatureException.class,
        () -> publicEndorsementService.createEndorsement(INPUT_ENDORSEMENT, TEST_IDENTITY));
  }

  @Test
  public void createEndorsement_publisherIdProviderThrowsException_propagatesException() {
    when(mockPublisherIdProvider.getValidPublisherId(any()))
        .thenThrow(new PolicyException("Config error"));

    assertThrows(
        PolicyException.class,
        () -> publicEndorsementService.createEndorsement(INPUT_ENDORSEMENT, TEST_IDENTITY));
  }
}
