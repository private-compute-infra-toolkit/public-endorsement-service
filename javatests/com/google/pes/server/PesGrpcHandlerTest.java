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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.pes.domain.PublicEndorsementService;
import com.google.pes.domain.model.Endorsement;
import com.google.pes.domain.model.Signature;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.model.TLogReceipt;
import com.google.pes.domain.ports.InvalidSignatureException;
import com.google.pes.domain.ports.InvalidVerificationMaterialException;
import com.google.pes.domain.ports.PolicyException;
import com.google.pes.domain.ports.TLogException;
import com.google.pes.v1.CreatePublicEndorsementRequest;
import com.google.pes.v1.PublicEndorsement;
import com.google.pes.v1.PublicEndorsementServiceGrpc;
import com.google.pes.v1.VerificationMaterial;
import com.google.pes.v1.X509Der;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class PesGrpcHandlerTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
  @Captor private ArgumentCaptor<Endorsement> endorsementCaptor;

  @Mock private PublicEndorsementService mockDomainService;
  private PublicEndorsementServiceGrpc.PublicEndorsementServiceBlockingStub blockingStub;

  @Before
  public void setUp() throws Exception {
    mockDomainService = mock(PublicEndorsementService.class);
    String serverName = InProcessServerBuilder.generateName();

    io.grpc.ServerInterceptor dummyAuthInterceptor =
        new io.grpc.ServerInterceptor() {
          @Override
          public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(
              io.grpc.ServerCall<ReqT, RespT> call,
              io.grpc.Metadata headers,
              io.grpc.ServerCallHandler<ReqT, RespT> next) {
            io.grpc.Context context =
                io.grpc.Context.current()
                    .withValue(JwtInterceptor.ISSUER_CONTEXT_KEY, "issuer")
                    .withValue(JwtInterceptor.SUBJECT_CONTEXT_KEY, "subject")
                    .withValue(JwtInterceptor.AUDIENCE_CONTEXT_KEY, java.util.Set.of("audience"));
            return io.grpc.Contexts.interceptCall(context, call, headers, next);
          }
        };

    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(
                io.grpc.ServerInterceptors.intercept(
                    new PesGrpcHandler(mockDomainService), dummyAuthInterceptor))
            .build()
            .start());

    blockingStub =
        PublicEndorsementServiceGrpc.newBlockingStub(
            grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build()));
  }

  @Test
  public void createPublicEndorsement_success() {
    Endorsement domainResponse =
        new Endorsement(
            "endorsements/123",
            new Statement(Statement.Format.JSON_INTOTO, ByteString.copyFromUtf8("test statement")),
            new Signature(
                ByteString.copyFromUtf8("sig"),
                new com.google.pes.domain.model.VerificationMaterial(
                    ByteString.copyFromUtf8("verification"),
                    com.google.pes.domain.model.VerificationMaterial.Format.X509_DER)),
            Collections.emptyList(),
            new TLogReceipt("logId"));
    when(mockDomainService.createEndorsement(any(), any())).thenReturn(domainResponse);
    CreatePublicEndorsementRequest request = generatePesRequest();
    PublicEndorsement expectedProtoResponse = generatePublicEndorsement();

    PublicEndorsement actualProtoResponse = blockingStub.createPublicEndorsement(request);

    verify(mockDomainService).createEndorsement(endorsementCaptor.capture(), any());
    Endorsement capturedEndorsement = endorsementCaptor.getValue();

    assertThat(capturedEndorsement.statement().format()).isEqualTo(Statement.Format.JSON_INTOTO);
    assertThat(capturedEndorsement.statement().serialized())
        .isEqualTo(ByteString.copyFromUtf8("test statement"));
    assertThat(capturedEndorsement.statementSignature().signature())
        .isEqualTo(ByteString.copyFromUtf8("sig"));
    assertThat(capturedEndorsement.statementSignature().verificationMaterial().content())
        .isEqualTo(ByteString.copyFromUtf8("verification"));
    assertThat(capturedEndorsement.statementSignature().verificationMaterial().format())
        .isEqualTo(com.google.pes.domain.model.VerificationMaterial.Format.X509_DER);

    assertThat(actualProtoResponse).isEqualTo(expectedProtoResponse);
  }

  @Test
  public void createPublicEndorsement_tLogException_unavailable() {
    when(mockDomainService.createEndorsement(any(), any()))
        .thenThrow(new TLogException("TLog timeout"));

    StatusRuntimeException thrown =
        assertThrows(
            StatusRuntimeException.class,
            () ->
                blockingStub.createPublicEndorsement(
                    CreatePublicEndorsementRequest.getDefaultInstance()));

    assertThat(thrown.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
  }

  @Test
  public void createPublicEndorsement_invalidSignatureException_invalidArgument() {
    when(mockDomainService.createEndorsement(any(), any()))
        .thenThrow(new InvalidSignatureException("Signature check failed"));

    StatusRuntimeException thrown =
        assertThrows(
            StatusRuntimeException.class,
            () ->
                blockingStub.createPublicEndorsement(
                    CreatePublicEndorsementRequest.getDefaultInstance()));

    assertThat(thrown.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  public void createPublicEndorsement_illegalArgumentException_invalidArgument() {
    when(mockDomainService.createEndorsement(any(), any()))
        .thenThrow(new IllegalArgumentException("Malformed proto fields"));

    StatusRuntimeException thrown =
        assertThrows(
            StatusRuntimeException.class,
            () ->
                blockingStub.createPublicEndorsement(
                    CreatePublicEndorsementRequest.getDefaultInstance()));

    assertThat(thrown.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  public void createPublicEndorsement_invalidVerificationMaterial_invalidArgument() {
    when(mockDomainService.createEndorsement(any(), any()))
        .thenThrow(new InvalidVerificationMaterialException("Broken verification material"));

    StatusRuntimeException thrown =
        assertThrows(
            StatusRuntimeException.class,
            () ->
                blockingStub.createPublicEndorsement(
                    CreatePublicEndorsementRequest.getDefaultInstance()));

    assertThat(thrown.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  public void createPublicEndorsement_configurationException_failedPrecondition() {
    when(mockDomainService.createEndorsement(any(), any()))
        .thenThrow(new PolicyException("KMS credentials expired"));

    StatusRuntimeException thrown =
        assertThrows(
            StatusRuntimeException.class,
            () ->
                blockingStub.createPublicEndorsement(
                    CreatePublicEndorsementRequest.getDefaultInstance()));

    assertThat(thrown.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
  }

  private CreatePublicEndorsementRequest generatePesRequest() {
    return CreatePublicEndorsementRequest.newBuilder()
        .setPublicEndorsement(
            PublicEndorsement.newBuilder()
                .setStatement(
                    com.google.pes.v1.Statement.newBuilder()
                        .setFormat(com.google.pes.v1.Statement.Format.JSON_INTOTO)
                        .setSerialized(ByteString.copyFromUtf8("test statement")))
                .setStatementSignature(
                    com.google.pes.v1.Signature.newBuilder()
                        .setSignature(ByteString.copyFromUtf8("sig"))
                        .setVerificationMaterial(
                            VerificationMaterial.newBuilder()
                                .setX509Certificate(
                                    X509Der.newBuilder()
                                        .setDerBytes(ByteString.copyFromUtf8("verification"))))))
        .build();
  }

  private PublicEndorsement generatePublicEndorsement() {
    return PublicEndorsement.newBuilder()
        .setName("endorsements/123")
        .setStatement(
            com.google.pes.v1.Statement.newBuilder()
                .setFormat(com.google.pes.v1.Statement.Format.JSON_INTOTO)
                .setSerialized(ByteString.copyFromUtf8("test statement")))
        .setStatementSignature(
            com.google.pes.v1.Signature.newBuilder()
                .setSignature(ByteString.copyFromUtf8("sig"))
                .setVerificationMaterial(
                    com.google.pes.v1.VerificationMaterial.newBuilder()
                        .setX509Certificate(
                            com.google.pes.v1.X509Der.newBuilder()
                                .setDerBytes(ByteString.copyFromUtf8("verification")))))
        .setTlogReceipt(com.google.pes.v1.TLogReceipt.newBuilder().setEntryId("logId"))
        .build();
  }
}
