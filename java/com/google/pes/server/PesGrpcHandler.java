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

package com.google.pes.server;

import com.google.common.flogger.FluentLogger;
import com.google.pes.adapters.EndorsementMapper;
import com.google.pes.domain.CallerIdentity;
import com.google.pes.domain.PublicEndorsementService;
import com.google.pes.domain.model.Endorsement;
import com.google.pes.domain.ports.InvalidSignatureException;
import com.google.pes.domain.ports.InvalidVerificationMaterialException;
import com.google.pes.domain.ports.PolicyException;
import com.google.pes.domain.ports.TLogException;
import com.google.pes.v1.CreatePublicEndorsementRequest;
import com.google.pes.v1.PublicEndorsement;
import com.google.pes.v1.PublicEndorsementServiceGrpc.PublicEndorsementServiceImplBase;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import java.util.Set;

/**
 * A gRPC handler that implements the Public Endorsement Service API.
 *
 * <p>This class is responsible for handling incoming gRPC requests and forwarding it to internal
 * PES service implementation
 */
public class PesGrpcHandler extends PublicEndorsementServiceImplBase {
  FluentLogger logger = FluentLogger.forEnclosingClass();
  private final PublicEndorsementService publicEndorsementService;

  @Inject
  PesGrpcHandler(PublicEndorsementService publicEndorsementService) {
    this.publicEndorsementService = publicEndorsementService;
  }

  /** Publishes provided Endorsement on the TLog, signs and returns a public endorsement. */
  @Override
  public void createPublicEndorsement(
      CreatePublicEndorsementRequest request, StreamObserver<PublicEndorsement> responseObserver) {
    logger.atInfo().log("Create an endorsement called");
    String issuer = JwtInterceptor.ISSUER_CONTEXT_KEY.get();
    String subject = JwtInterceptor.SUBJECT_CONTEXT_KEY.get();
    Set<String> audiences = JwtInterceptor.AUDIENCE_CONTEXT_KEY.get();

    if (issuer == null || subject == null || audiences == null) {
      responseObserver.onError(
          Status.UNAUTHENTICATED.withDescription("Missing JWT token").asRuntimeException());
      return;
    }

    CallerIdentity callerIdentity = new CallerIdentity(issuer, subject, audiences);
    try {
      Endorsement publicEndorsement =
          publicEndorsementService.createEndorsement(
              EndorsementMapper.toDomain(request.getPublicEndorsement()), callerIdentity);
      responseObserver.onNext(EndorsementMapper.toProto(publicEndorsement));
      responseObserver.onCompleted();
    } catch (TLogException e) {
      logger.atWarning().withCause(e).log("Interaction with Tlog failed");
      responseObserver.onError(
          Status.UNAVAILABLE.withDescription(e.getMessage()).withCause(e).asRuntimeException());
    } catch (IllegalArgumentException
        | InvalidSignatureException
        | InvalidVerificationMaterialException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT
              .withDescription("Invalid request: " + e.getMessage())
              .withCause(e)
              .asRuntimeException());
    } catch (PolicyException e) {
      responseObserver.onError(
          Status.FAILED_PRECONDITION
              .withDescription("Error fetching publisher's configuration: " + e.getMessage())
              .withCause(e)
              .asRuntimeException());
    }
  }
}
