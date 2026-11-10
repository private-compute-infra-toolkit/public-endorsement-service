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

package com.google.pes.adapters.oidc;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.mbs.adapters.DefaultSelfSignedCertificateFactory;
import com.google.mbs.domain.AttestationToken;
import com.google.mbs.domain.SelfSignedBundle;
import com.google.mbs.domain.SelfSignedBundleWithEpoch;
import com.google.mbs.domain.SelfSignedCertificate;
import com.google.mbs.domain.SelfSignedCertificateFactory;
import com.google.mbs.domain.TrustPackage;
import com.google.mbs.domain.TrustPackageProvider;
import com.google.mbs.domain.ValidityPeriod;
import com.google.pes.server.AwsInstanceMetadata;
import io.jsonwebtoken.Locator;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OidcAudienceValidatorTest {
  private static final String TRUST_DOMAIN = "pes.pcit.local";
  private static final AwsInstanceMetadata METADATA =
      AwsInstanceMetadata.builder()
          .setRegion("us-east-1")
          .setAccountId("dummy_account")
          .setEnvironment("test")
          .setDomain("aws.pcit.local")
          .setInstanceId("i-testinstance")
          .build();

  private Locator<Key> mockKeyLocator;
  private TrustPackageProvider mockCertificateProvider;
  private OidcAudienceValidator validator;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() throws Exception {
    mockKeyLocator = (Locator<Key>) mock(Locator.class);
    mockCertificateProvider = mock(TrustPackageProvider.class);
    when(mockCertificateProvider.getActiveTrustPackage())
        .thenReturn(rootCertificateWithTrustDomain(TRUST_DOMAIN));
    validator = new OidcAudienceValidator(mockCertificateProvider, METADATA, mockKeyLocator);
  }

  /** Builds a trust package whose root certificates carry the SPIFFE URI SANs for the domains. */
  private static TrustPackage rootCertificateWithTrustDomain(String... trustDomains) {
    return new TrustPackage(
        java.util.Arrays.stream(trustDomains)
            .map(OidcAudienceValidatorTest::bundleWithTrustDomain)
            .map(bundle -> new SelfSignedBundleWithEpoch(0, bundle))
            .toList(),
        List.of());
  }

  private static SelfSignedBundle bundleWithTrustDomain(String trustDomain) {
    GeneralNames san =
        new GeneralNames(
            new GeneralName(
                GeneralName.uniformResourceIdentifier, "spiffe://" + trustDomain + "/workload"));
    DefaultSelfSignedCertificateFactory factory =
        new DefaultSelfSignedCertificateFactory(
            new DefaultSelfSignedCertificateFactory.CertSignatureSpec("RSA", 2048, "SHA256withRSA"),
            new X500Name("C=US, O=Google LLC, CN=PES"),
            Optional.of(san),
            KeyUsage.digitalSignature);
    SelfSignedCertificateFactory.X509CertificateAndPrivateKey generated =
        factory.generate(
            new SelfSignedCertificate(
                0,
                new ValidityPeriod(
                    Instant.now().minus(Duration.ofDays(1)),
                    Instant.now().plus(Duration.ofDays(1)))));
    return new SelfSignedBundle(
        generated.certificate(),
        generated.privateKey(),
        AttestationToken.fromBytes("token".getBytes()));
  }

  @Test
  public void validateAudience_trustDomainAudience_succeeds() throws Exception {
    String audience = "https://pes.pcit.local/v1/endorsements";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_globalHostnameAudience_succeeds() throws Exception {
    String audience = "https://pes.test.aws.pcit.local/v1/endorsements";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_regionalHostnameAudience_succeeds() throws Exception {
    String audience = "https://us-east-1.pes.test.aws.pcit.local/v1/endorsements";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_trustDomainAudienceWithoutPath_succeeds() throws Exception {
    String audience = "https://pes.pcit.local";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_globalHostnameAudienceWithoutPath_succeeds() throws Exception {
    String audience = "https://pes.test.aws.pcit.local";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_regionalHostnameAudienceWithoutPath_succeeds() throws Exception {
    String audience = "https://us-east-1.pes.test.aws.pcit.local";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_mismatchedRegionalAudience_fails() {
    String audience = "https://us-west-1.pes.test.aws.pcit.local/v1/endorsements";
    assertThrows(
        AudienceValidationException.class, () -> validator.validateAudience(Set.of(audience)));
  }

  @Test
  public void validateAudience_globalHostnameAudienceWrongDomain_fails() {
    String audience = "https://pes.test.aws.pcit.local1/v1/endorsements";
    assertThrows(
        AudienceValidationException.class, () -> validator.validateAudience(Set.of(audience)));
  }

  @Test
  public void validateAudience_globalHostnameAudienceWrongEnv_fails() {
    String audience = "https://pes.test1.aws.pcit.local/v1/endorsements";
    assertThrows(
        AudienceValidationException.class, () -> validator.validateAudience(Set.of(audience)));
  }

  @Test
  public void validateAudience_malformedAudienceFormat_fails() {
    String audience = "https://pes.pcit.local/v1/endorsements/invalid";
    assertThrows(
        AudienceValidationException.class, () -> validator.validateAudience(Set.of(audience)));
  }

  @Test
  public void validateAudience_trailingSlash_fails() {
    String audience = "https://pes.test.aws.pcit.local/";
    assertThrows(
        AudienceValidationException.class, () -> validator.validateAudience(Set.of(audience)));
  }

  @Test
  public void validateAudience_wrongPath_fails() {
    String audience = "https://pes.test.aws.pcit.local/something_else";
    assertThrows(
        AudienceValidationException.class, () -> validator.validateAudience(Set.of(audience)));
  }

  @Test
  public void validateAudience_certificateNotLoaded_propagatesIllegalStateException() {
    when(mockCertificateProvider.getActiveTrustPackage())
        .thenThrow(new IllegalStateException("Certificate has not been initialized yet"));

    String audience = "https://pes.pcit.local/v1/endorsements";
    assertThrows(IllegalStateException.class, () -> validator.validateAudience(Set.of(audience)));
  }

  @Test
  public void validateAudience_certificateNotLoaded_failsBeforeAudienceInspection() {
    when(mockCertificateProvider.getActiveTrustPackage())
        .thenThrow(new IllegalStateException("Certificate has not been initialized yet"));

    // Even with empty, invalid, or matching hostnames, it must fail closed before inspecting
    // audiences
    assertThrows(IllegalStateException.class, () -> validator.validateAudience(Set.of()));
    assertThrows(
        IllegalStateException.class,
        () -> validator.validateAudience(Set.of("https://invalid-format/path")));
    assertThrows(
        IllegalStateException.class,
        () ->
            validator.validateAudience(Set.of("https://pes.test.aws.pcit.local/v1/endorsements")));
  }

  @Test
  public void validateAudience_certificateWithMalformedSan_throwsIllegalStateException()
      throws Exception {
    when(mockCertificateProvider.getActiveTrustPackage())
        .thenReturn(rootCertificateWithTrustDomain("invalid-no-pes-prefix.local"));

    String audience = "https://pes.pcit.local/v1/endorsements";
    assertThrows(IllegalStateException.class, () -> validator.validateAudience(Set.of(audience)));
  }

  @Test
  public void validateAudience_mismatchedCertificateTrustDomains_throwsIllegalStateException() {
    when(mockCertificateProvider.getActiveTrustPackage())
        .thenReturn(rootCertificateWithTrustDomain("pes.pcit.local", "pes.other.local"));

    assertThrows(
        IllegalStateException.class,
        () -> validator.validateAudience(Set.of("https://pes.pcit.local/v1/endorsements")));
  }

  @Test
  public void validateAudience_multipleMatchingCertificateTrustDomains_succeeds() {
    when(mockCertificateProvider.getActiveTrustPackage())
        .thenReturn(rootCertificateWithTrustDomain("pes.pcit.local", "pes.pcit.local"));

    validator.validateAudience(Set.of("https://pes.pcit.local/v1/endorsements"));
  }

  @Test
  public void validateAudience_certificateReloadedWithNewTrustDomain_usesNewTrustDomain()
      throws Exception {
    when(mockCertificateProvider.getActiveTrustPackage())
        .thenReturn(rootCertificateWithTrustDomain("pes.rotated.local"));

    validator.validateAudience(Set.of("https://pes.rotated.local/v1/endorsements"));

    assertThrows(
        AudienceValidationException.class,
        () -> validator.validateAudience(Set.of("https://pes.pcit.local/v1/endorsements")));
  }
}
