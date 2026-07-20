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

import com.google.pes.server.AwsInstanceMetadata;
import io.jsonwebtoken.Locator;
import java.security.Key;
import java.util.Set;
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
          .build();

  private Locator<Key> mockKeyLocator;
  private OidcAudienceValidator validator;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() throws Exception {
    mockKeyLocator = (Locator<Key>) mock(Locator.class);
    validator = new OidcAudienceValidator(TRUST_DOMAIN, METADATA, mockKeyLocator);
  }

  @Test
  public void validateAudience_trustDomainAudience_succeeds() {
    String audience = "https://pes.pcit.local/v1/endorsements";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_globalHostnameAudience_succeeds() {
    String audience = "https://pes.test.aws.pcit.local/v1/endorsements";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_regionalHostnameAudience_succeeds() {
    String audience = "https://us-east-1.pes.test.aws.pcit.local/v1/endorsements";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_trustDomainAudienceWithoutPath_succeeds() {
    String audience = "https://pes.pcit.local";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_globalHostnameAudienceWithoutPath_succeeds() {
    String audience = "https://pes.test.aws.pcit.local";
    validator.validateAudience(Set.of(audience));
  }

  @Test
  public void validateAudience_regionalHostnameAudienceWithoutPath_succeeds() {
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
}
