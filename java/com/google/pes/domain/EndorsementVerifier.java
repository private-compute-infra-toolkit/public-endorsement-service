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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.pes.adapters.statementvalidation.InTotoStatement;
import com.google.pes.adapters.statementvalidation.OakPredicate;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.model.StatementSignature;
import com.google.pes.domain.model.VerifiedEndorsement;
import com.google.pes.domain.ports.PublisherIdProvider;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Verifies endorsement statement validity dates against root certificate, uses {@link
 * PublisherIdProvider} to extract publisher ID, and delegates policy verification to {@link
 * PublisherVerifier}.
 */
public class EndorsementVerifier {

  private final Map<Statement.Format, Provider<PublisherIdProvider>> publisherIdProviders;
  private final PublisherVerifier publisherVerifier;
  private final ObjectMapper objectMapper;

  @Inject
  EndorsementVerifier(
      Map<Statement.Format, Provider<PublisherIdProvider>> publisherIdProviders,
      PublisherVerifier publisherVerifier,
      ObjectMapper objectMapper) {
    this.publisherIdProviders = publisherIdProviders;
    this.publisherVerifier = publisherVerifier;
    this.objectMapper = objectMapper;
  }

  /**
   * Verifies the statement validity, expiration date against root certificate, extracts publisher
   * ID via {@link PublisherIdProvider}, and verifies publisher policy via {@link
   * PublisherVerifier}.
   *
   * @param statement The endorsement statement to verify.
   * @param identity The OIDC identity of the client.
   * @param signature The signature object containing verification material.
   * @return The verified endorsement containing extracted valid publisher ID.
   * @throws IllegalArgumentException if statement verification or policy verification fails.
   */
  public VerifiedEndorsement parseAndVerify(
      Statement statement,
      CallerIdentity identity,
      StatementSignature signature,
      X509Certificate rootCertificate) {
    if (statement.format() == Statement.Format.FORMAT_UNSPECIFIED) {
      throw new IllegalArgumentException("The statement format has to be specified");
    }

    InTotoStatement inTotoStatement;
    try {
      inTotoStatement =
          objectMapper.readValue(statement.serialized().toByteArray(), InTotoStatement.class);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format("Failed to parse statement as JSON: %s", e.getMessage()), e);
    }

    validatePredicateType(inTotoStatement);
    validateNotAfter(inTotoStatement.getPredicate(), rootCertificate);
    String publisherId = validatePublisherAndGetPublisherId(statement, identity, signature);
    return new VerifiedEndorsement(publisherId);
  }

  private void validatePredicateType(InTotoStatement inTotoStatement) {
    if (inTotoStatement == null || inTotoStatement.getPredicate() == null) {
      throw new IllegalArgumentException("Statement or predicate cannot be null");
    }
  }

  private void validateNotAfter(OakPredicate predicate, X509Certificate rootCertificate) {
    if (predicate.getValidity() == null) {
      throw new IllegalArgumentException("Failed to resolve predicate validity");
    }

    String notAfterStr = predicate.getValidity().getNotAfter();
    if (notAfterStr == null) {
      throw new IllegalArgumentException("Failed to resolve predicate validity expiration date");
    }

    if (rootCertificate == null || rootCertificate.getNotAfter() == null) {
      throw new IllegalArgumentException("Failed to resolve root certificate expiration date");
    }

    Instant endorsementNotAfter = parseInstant(notAfterStr);
    Instant rootNotAfter = rootCertificate.getNotAfter().toInstant();

    if (endorsementNotAfter.isAfter(rootNotAfter)) {
      throw new IllegalArgumentException(
          String.format(
              "Endorsement expiration date (%s) cannot be past root certificate expiration date"
                  + " (%s)",
              notAfterStr, rootNotAfter));
    }
  }

  private String validatePublisherAndGetPublisherId(
      Statement statement, CallerIdentity identity, StatementSignature signature) {
    Provider<PublisherIdProvider> provider = publisherIdProviders.get(statement.format());
    if (provider == null || provider.get() == null) {
      throw new IllegalArgumentException(
          "Provider resolution failed for statement format: " + statement.format());
    }
    String publisherId = provider.get().getValidPublisherId(statement.serialized());
    publisherVerifier.verify(publisherId, identity, signature);
    return publisherId;
  }

  private static Instant parseInstant(String timestamp) {
    try {
      return Instant.parse(timestamp);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Invalid RFC 3339 timestamp in statement notAfter: " + timestamp, e);
    }
  }
}
