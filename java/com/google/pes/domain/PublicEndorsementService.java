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

import com.google.common.flogger.FluentLogger;
import com.google.mbs.domain.MeasurementBoundCertificate;
import com.google.mbs.domain.MeasurementBoundCertificateProvider;
import com.google.pes.domain.metric.Metrics;
import com.google.pes.domain.model.Endorsement;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.model.TLogReceipt;
import com.google.pes.domain.model.VerifiedEndorsement;
import com.google.pes.domain.ports.SignatureGenerator;
import com.google.pes.domain.ports.SignatureVerifier;
import com.google.pes.domain.ports.TLog;
import com.google.protobuf.ByteString;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service for creating public endorsements.
 *
 * <p>This service handles the creation and publication of endorsements in the TLogs.
 */
public class PublicEndorsementService {
  FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String NAME_PREFIX = "endorsements/";

  private final TLog tLog;
  private final EndorsementVerifier endorsementVerifier;
  private final SignatureGenerator signatureGenerator;
  private final SignatureVerifier signatureVerifier;
  private final MeasurementBoundCertificateProvider certificateProvider;
  private final Metrics metrics;

  @Inject
  PublicEndorsementService(
      TLog tLog,
      EndorsementVerifier endorsementVerifier,
      SignatureGenerator signatureGenerator,
      SignatureVerifier signatureVerifier,
      MeasurementBoundCertificateProvider certificateProvider,
      Metrics metrics) {
    this.tLog = tLog;
    this.endorsementVerifier = endorsementVerifier;
    this.signatureGenerator = signatureGenerator;
    this.signatureVerifier = signatureVerifier;
    this.certificateProvider = certificateProvider;
    this.metrics = metrics;
  }

  /**
   * Creates a new public endorsement.
   *
   * @param publicEndorsement Statement and Signature of the endorsement to create.
   * @return The fully created endorsement including its unique name and log receipt and PES
   *     signatures.
   */
  public Endorsement createEndorsement(Endorsement publicEndorsement, CallerIdentity identity) {
    if (publicEndorsement.statement().format() == Statement.Format.FORMAT_UNSPECIFIED) {
      throw new IllegalArgumentException("The statement format has to be specified");
    }

    List<MeasurementBoundCertificate> mbcs = certificateProvider.getActiveTrustPackage().bundles();

    VerifiedEndorsement verifiedEndorsement =
        endorsementVerifier.parseAndVerify(
            publicEndorsement.statement(),
            identity,
            publicEndorsement.statementSignature(),
            getEarliestCertificateExpiry(mbcs));

    signatureVerifier.verify(
        publicEndorsement.statementSignature(), publicEndorsement.statement().serialized());

    String uid = UUID.randomUUID().toString();
    String endorsementName = NAME_PREFIX + uid;

    logger.atInfo().log("Publishing endorsement with name %s to the TLedger", endorsementName);
    TLogReceipt tLogReceipt =
        tLog.post(
            new Endorsement(
                endorsementName,
                publicEndorsement.statement(),
                publicEndorsement.statementSignature(),
                publicEndorsement.endorsementSignatures(),
                publicEndorsement.tLogReceipt()));

    ByteString dataToSign =
        PreAuthenticationEncoding.calculate(
            publicEndorsement.statement(), publicEndorsement.statementSignature(), tLogReceipt);
    List<Signature> endorsementSignatures =
        mbcs.stream()
            .map(
                mbc ->
                    signatureGenerator.generate(
                        dataToSign, mbc.getCertificate(), mbc.getPrivateKey()))
            .toList();

    metrics.incrementEndorsementCounter(verifiedEndorsement.publisherId());

    return new Endorsement(
        endorsementName,
        publicEndorsement.statement(),
        publicEndorsement.statementSignature(),
        endorsementSignatures,
        tLogReceipt);
  }

  private Instant getEarliestCertificateExpiry(List<MeasurementBoundCertificate> mbcs) {
    return mbcs.stream()
        .map(mbc -> mbc.getCertificate().getNotAfter().toInstant())
        .min(Comparator.naturalOrder())
        .orElseThrow(() -> new IllegalStateException("No active certificates in trust package"));
  }
}
