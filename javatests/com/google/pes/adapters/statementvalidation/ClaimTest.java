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

import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ClaimTest {

  @Test
  public void constructor_validPublisherClaim_succeeds() {
    Map<String, String> annotations = Map.of("publisher_id", "release@google.com");
    Claim claim = new Claim(Claim.PUBLISHER_CLAIM_TYPE, annotations);
    assertThat(claim.getType()).isEqualTo(Claim.PUBLISHER_CLAIM_TYPE);
    assertThat(claim.getAnnotations()).isEqualTo(annotations);
  }

  @Test
  public void constructor_validPublisherClaimWithLegacyType_succeeds() {
    Map<String, String> annotations = Map.of("publisher_id", "release@google.com");
    Claim claim = new Claim(Claim.LEGACY_PUBLISHER_CLAIM_TYPE, annotations);
    assertThat(claim.getType()).isEqualTo(Claim.LEGACY_PUBLISHER_CLAIM_TYPE);
  }

  @Test
  public void constructor_publisherClaimMissingAnnotations_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> new Claim(Claim.PUBLISHER_CLAIM_TYPE, null));
    assertThat(e).hasMessageThat().contains("Publisher");
    assertThat(e).hasMessageThat().contains("annotations");
  }

  @Test
  public void constructor_publisherClaimMissingPublisherId_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> new Claim(Claim.PUBLISHER_CLAIM_TYPE, Map.of()));
    assertThat(e).hasMessageThat().contains("publisher_id");
  }

  @Test
  public void constructor_publisherClaimNullPublisherId_throws() {
    Map<String, String> annotations = java.util.Collections.singletonMap("publisher_id", null);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Claim(Claim.PUBLISHER_CLAIM_TYPE, annotations));
    assertThat(e).hasMessageThat().contains("publisher_id");
  }

  @Test
  public void constructor_publisherClaimEmptyPublisherId_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Claim(Claim.PUBLISHER_CLAIM_TYPE, Map.of("publisher_id", "")));
    assertThat(e).hasMessageThat().contains("publisher_id");
    assertThat(e).hasMessageThat().contains("empty");
  }

  @Test
  public void constructor_publisherClaimInvalidFormatNoAt_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Claim(Claim.PUBLISHER_CLAIM_TYPE, Map.of("publisher_id", "google.com")));
    assertThat(e).hasMessageThat().contains("publisher_id");
    assertThat(e).hasMessageThat().contains("format");
  }

  @Test
  public void constructor_publisherClaimInvalidFormatMultipleAt_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Claim(Claim.PUBLISHER_CLAIM_TYPE, Map.of("publisher_id", "a@b@c.com")));
    assertThat(e).hasMessageThat().contains("publisher_id");
    assertThat(e).hasMessageThat().contains("format");
  }

  @Test
  public void constructor_publisherClaimRoleTooLong_throws() {
    String longRole = "a".repeat(256);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Claim(
                    Claim.PUBLISHER_CLAIM_TYPE, Map.of("publisher_id", longRole + "@google.com")));
    assertThat(e).hasMessageThat().contains("role");
    assertThat(e).hasMessageThat().contains("255");
  }

  @Test
  public void constructor_publisherClaimRoleInvalidChars_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Claim(Claim.PUBLISHER_CLAIM_TYPE, Map.of("publisher_id", "role#@google.com")));
    assertThat(e).hasMessageThat().contains("role");
    assertThat(e).hasMessageThat().contains("invalid");
  }

  @Test
  public void constructor_publisherClaimDomainTooLong_throws() {
    String longDomain = "a".repeat(256) + ".com";
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Claim(
                    Claim.PUBLISHER_CLAIM_TYPE, Map.of("publisher_id", "release@" + longDomain)));
    assertThat(e).hasMessageThat().contains("domain");
    assertThat(e).hasMessageThat().contains("255");
  }

  @Test
  public void constructor_publisherClaimDomainInvalidChars_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Claim(
                    Claim.PUBLISHER_CLAIM_TYPE, Map.of("publisher_id", "release@Google.com")));
    assertThat(e).hasMessageThat().contains("domain");
    assertThat(e).hasMessageThat().contains("invalid");
  }

  @Test
  public void constructor_publisherClaimDomainLeadingDot_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Claim(
                    Claim.PUBLISHER_CLAIM_TYPE, Map.of("publisher_id", "release@.google.com")));
    assertThat(e).hasMessageThat().contains("domain");
    assertThat(e).hasMessageThat().contains("valid");
  }

  @Test
  public void constructor_publisherClaimDomainTrailingDot_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Claim(
                    Claim.PUBLISHER_CLAIM_TYPE, Map.of("publisher_id", "release@google.com.")));
    assertThat(e).hasMessageThat().contains("domain");
    assertThat(e).hasMessageThat().contains("valid");
  }

  @Test
  public void constructor_publisherClaimDomainConsecutiveDots_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Claim(
                    Claim.PUBLISHER_CLAIM_TYPE, Map.of("publisher_id", "release@google..com")));
    assertThat(e).hasMessageThat().contains("domain");
    assertThat(e).hasMessageThat().contains("valid");
  }

  @Test
  public void constructor_validWorkloadClaim_succeeds() {
    Map<String, String> annotations = Map.of("workload_id", "workload-123");
    Claim claim = new Claim(Claim.WORKLOAD_CLAIM_TYPE, annotations);
    assertThat(claim.getType()).isEqualTo(Claim.WORKLOAD_CLAIM_TYPE);
    assertThat(claim.getAnnotations()).isEqualTo(annotations);
  }

  @Test
  public void constructor_validWorkloadClaimWithLegacyType_succeeds() {
    Map<String, String> annotations = Map.of("workload_id", "workload-123");
    Claim claim = new Claim(Claim.LEGACY_WORKLOAD_CLAIM_TYPE, annotations);
    assertThat(claim.getType()).isEqualTo(Claim.LEGACY_WORKLOAD_CLAIM_TYPE);
  }

  @Test
  public void constructor_workloadClaimMissingAnnotations_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> new Claim(Claim.WORKLOAD_CLAIM_TYPE, null));
    assertThat(e).hasMessageThat().contains("Workload");
    assertThat(e).hasMessageThat().contains("annotations");
  }

  @Test
  public void constructor_workloadClaimMissingWorkloadId_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> new Claim(Claim.WORKLOAD_CLAIM_TYPE, Map.of()));
    assertThat(e).hasMessageThat().contains("workload_id");
  }

  @Test
  public void constructor_workloadClaimEmptyWorkloadId_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Claim(Claim.WORKLOAD_CLAIM_TYPE, Map.of("workload_id", "")));
    assertThat(e).hasMessageThat().contains("workload_id");
    assertThat(e).hasMessageThat().contains("empty");
  }

  @Test
  public void constructor_workloadClaimTooLong_throws() {
    String longWorkload = "a".repeat(256);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Claim(Claim.WORKLOAD_CLAIM_TYPE, Map.of("workload_id", longWorkload)));
    assertThat(e).hasMessageThat().contains("workload_id");
    assertThat(e).hasMessageThat().contains("255");
  }

  @Test
  public void constructor_workloadClaimInvalidChars_throws() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Claim(Claim.WORKLOAD_CLAIM_TYPE, Map.of("workload_id", "workload#")));
    assertThat(e).hasMessageThat().contains("workload_id");
    assertThat(e).hasMessageThat().contains("invalid");
  }
}
