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
import static org.mockito.Mockito.when;

import com.google.pes.domain.model.PublisherPolicy;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.PolicyProvider;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class PublisherVerifierTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private PolicyProvider mockPolicyProvider;

  private PublisherVerifier verifier;

  private static final String TEST_PUBLISHER_ID = "test-publisher";
  private static final String TEST_ISSUER = "test-issuer";
  private static final String TEST_SUBJECT = "test-subject";
  private static final CallerIdentity TEST_IDENTITY =
      new CallerIdentity(TEST_ISSUER, TEST_SUBJECT, java.util.Set.of("test-audience"));
  private static final VerificationMaterial TEST_MATERIAL =
      new VerificationMaterial(
          ByteString.copyFromUtf8("test-material"), VerificationMaterial.Format.ECDSA_P256_SHA256);
  private static final VerificationMaterial OTHER_MATERIAL =
      new VerificationMaterial(
          ByteString.copyFromUtf8("other-material"), VerificationMaterial.Format.X509_DER);
  private static final ByteString TEST_SIGNATURE_BYTES = ByteString.copyFromUtf8("test-signature");

  private static final Signature TEST_SIGNATURE =
      new Signature(TEST_SIGNATURE_BYTES, TEST_MATERIAL);

  private static final PublisherPolicy TEST_CONFIG =
      new PublisherPolicy(
          TEST_PUBLISHER_ID,
          List.of(new PublisherPolicy.OidcTokenClaims(TEST_ISSUER, TEST_SUBJECT)),
          List.of(TEST_MATERIAL));

  @Before
  public void setUp() {
    verifier = new PublisherVerifier(mockPolicyProvider);
  }

  @Test
  public void verify_success() {
    when(mockPolicyProvider.get(TEST_PUBLISHER_ID)).thenReturn(Optional.of(TEST_CONFIG));

    // No exception should be thrown
    verifier.verify(TEST_PUBLISHER_ID, TEST_IDENTITY, TEST_SIGNATURE);
  }

  @Test
  public void verify_configNotFound_throwsIllegalArgumentException() {
    when(mockPolicyProvider.get(TEST_PUBLISHER_ID)).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () -> verifier.verify(TEST_PUBLISHER_ID, TEST_IDENTITY, TEST_SIGNATURE));
  }

  @Test
  public void verify_issuerMismatch_throwsIllegalArgumentException() {
    PublisherPolicy configWithDifferentIssuer =
        new PublisherPolicy(
            TEST_PUBLISHER_ID,
            List.of(new PublisherPolicy.OidcTokenClaims("different-issuer", TEST_SUBJECT)),
            List.of(TEST_MATERIAL));
    when(mockPolicyProvider.get(TEST_PUBLISHER_ID))
        .thenReturn(Optional.of(configWithDifferentIssuer));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> verifier.verify(TEST_PUBLISHER_ID, TEST_IDENTITY, TEST_SIGNATURE));

    assertThat(exception).hasMessageThat().contains("issuer");
  }

  @Test
  public void verify_subjectMismatch_throwsIllegalArgumentException() {
    PublisherPolicy configWithDifferentSubject =
        new PublisherPolicy(
            TEST_PUBLISHER_ID,
            List.of(new PublisherPolicy.OidcTokenClaims(TEST_ISSUER, "different-subject")),
            List.of(TEST_MATERIAL));
    when(mockPolicyProvider.get(TEST_PUBLISHER_ID))
        .thenReturn(Optional.of(configWithDifferentSubject));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> verifier.verify(TEST_PUBLISHER_ID, TEST_IDENTITY, TEST_SIGNATURE));

    assertThat(exception).hasMessageThat().contains("subject");
  }

  @Test
  public void verify_publisherIdMismatch_throwsIllegalArgumentException() {
    when(mockPolicyProvider.get("wrong-publisher-id")).thenReturn(Optional.of(TEST_CONFIG));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> verifier.verify("wrong-publisher-id", TEST_IDENTITY, TEST_SIGNATURE));

    assertThat(exception).hasMessageThat().contains("Publisher ID");
  }

  @Test
  public void verify_verificationMaterialNotAuthorized_throwsIllegalArgumentException() {
    when(mockPolicyProvider.get(TEST_PUBLISHER_ID)).thenReturn(Optional.of(TEST_CONFIG));

    Signature signatureWithOtherMaterial = new Signature(TEST_SIGNATURE_BYTES, OTHER_MATERIAL);

    assertThrows(
        IllegalArgumentException.class,
        () -> verifier.verify(TEST_PUBLISHER_ID, TEST_IDENTITY, signatureWithOtherMaterial));
  }

  @Test
  public void verify_configContainsMultipleMaterials_success() {
    PublisherPolicy configWithMultipleMaterials =
        new PublisherPolicy(
            TEST_PUBLISHER_ID,
            List.of(new PublisherPolicy.OidcTokenClaims(TEST_ISSUER, TEST_SUBJECT)),
            List.of(OTHER_MATERIAL, TEST_MATERIAL));
    when(mockPolicyProvider.get(TEST_PUBLISHER_ID))
        .thenReturn(Optional.of(configWithMultipleMaterials));

    verifier.verify(TEST_PUBLISHER_ID, TEST_IDENTITY, TEST_SIGNATURE);
  }

  @Test
  public void verify_multipleClaims_success() {
    PublisherPolicy configWithMultipleClaims =
        new PublisherPolicy(
            TEST_PUBLISHER_ID,
            List.of(
                new PublisherPolicy.OidcTokenClaims("other-issuer", "other-subject"),
                new PublisherPolicy.OidcTokenClaims(TEST_ISSUER, TEST_SUBJECT)),
            List.of(TEST_MATERIAL));
    when(mockPolicyProvider.get(TEST_PUBLISHER_ID))
        .thenReturn(Optional.of(configWithMultipleClaims));

    verifier.verify(TEST_PUBLISHER_ID, TEST_IDENTITY, TEST_SIGNATURE);
  }

  @Test
  public void verify_multipleClaims_mismatch_throwsIllegalArgumentException() {
    PublisherPolicy configWithMultipleClaims =
        new PublisherPolicy(
            TEST_PUBLISHER_ID,
            List.of(
                new PublisherPolicy.OidcTokenClaims("other-issuer", "other-subject"),
                new PublisherPolicy.OidcTokenClaims(TEST_ISSUER, "different-subject")),
            List.of(TEST_MATERIAL));
    when(mockPolicyProvider.get(TEST_PUBLISHER_ID))
        .thenReturn(Optional.of(configWithMultipleClaims));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> verifier.verify(TEST_PUBLISHER_ID, TEST_IDENTITY, TEST_SIGNATURE));

    assertThat(exception).hasMessageThat().contains("mismatch");
  }
}
