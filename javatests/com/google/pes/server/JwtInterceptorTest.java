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
import static com.google.pes.domain.metric.AuthenticationStatus.FAILURE;
import static com.google.pes.domain.metric.AuthenticationStatus.SUCCESS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.pes.adapters.oidc.AudienceValidationException;
import com.google.pes.adapters.oidc.OidcAudienceValidator;
import com.google.pes.domain.CallerIdentity;
import com.google.pes.domain.metric.Metrics;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.jsonwebtoken.JwtException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
public class JwtInterceptorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private OidcAudienceValidator mockValidator;
  @Mock private Metrics mockMetrics;
  @Mock private ServerCall<String, String> mockCall;

  @Captor private ArgumentCaptor<Status> statusCaptor;
  @Captor private ArgumentCaptor<Metadata> metadataCaptor;

  private JwtInterceptor interceptor;

  private static final Metadata.Key<String> AUTHORIZATION_KEY =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

  @Before
  public void setUp() {
    interceptor = new JwtInterceptor(mockValidator, mockMetrics);
  }

  @Test
  public void interceptCall_validToken_successContextSetAndMetricIncremented() {
    Metadata headers = new Metadata();
    headers.put(AUTHORIZATION_KEY, "Bearer valid-token");

    CallerIdentity identity =
        new CallerIdentity("test-issuer", "test-subject", Set.of("test-audience"));
    when(mockValidator.parseAndValidate("valid-token")).thenReturn(identity);

    AtomicBoolean nextCalled = new AtomicBoolean(false);
    AtomicReference<String> capturedIssuer = new AtomicReference<>();
    AtomicReference<String> capturedSubject = new AtomicReference<>();
    AtomicReference<Set<String>> capturedAudience = new AtomicReference<>();

    ServerCallHandler<String, String> next =
        (call, interceptedHeaders) -> {
          nextCalled.set(true);
          capturedIssuer.set(JwtInterceptor.ISSUER_CONTEXT_KEY.get());
          capturedSubject.set(JwtInterceptor.SUBJECT_CONTEXT_KEY.get());
          capturedAudience.set(JwtInterceptor.AUDIENCE_CONTEXT_KEY.get());
          return new ServerCall.Listener<>() {};
        };

    interceptor.interceptCall(mockCall, headers, next);

    assertThat(nextCalled.get()).isTrue();
    assertThat(capturedIssuer.get()).isEqualTo("test-issuer");
    assertThat(capturedSubject.get()).isEqualTo("test-subject");
    assertThat(capturedAudience.get()).containsExactly("test-audience");
    verify(mockMetrics).incrementAuthenticationCounter(SUCCESS);
  }

  @Test
  public void interceptCall_missingAuthorizationHeader_unauthenticated() {
    Metadata headers = new Metadata();

    ServerCallHandler<String, String> next =
        (call, interceptedHeaders) -> {
          throw new AssertionError("Next handler should not be called");
        };

    interceptor.interceptCall(mockCall, headers, next);

    verify(mockCall).close(statusCaptor.capture(), metadataCaptor.capture());
    assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    assertThat(statusCaptor.getValue().getDescription())
        .contains("Missing or invalid Authorization header");
    verify(mockMetrics).incrementAuthenticationCounter(FAILURE);
  }

  @Test
  public void interceptCall_invalidHeaderPrefix_unauthenticated() {
    Metadata headers = new Metadata();
    headers.put(AUTHORIZATION_KEY, "Basic some-credentials");

    ServerCallHandler<String, String> next =
        (call, interceptedHeaders) -> {
          throw new AssertionError("Next handler should not be called");
        };

    interceptor.interceptCall(mockCall, headers, next);

    verify(mockCall).close(statusCaptor.capture(), metadataCaptor.capture());
    assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    verify(mockMetrics).incrementAuthenticationCounter(FAILURE);
  }

  @Test
  public void interceptCall_jwtException_unauthenticated() {
    Metadata headers = new Metadata();
    headers.put(AUTHORIZATION_KEY, "Bearer invalid-jwt");

    when(mockValidator.parseAndValidate("invalid-jwt"))
        .thenThrow(new JwtException("Invalid signature"));

    ServerCallHandler<String, String> next =
        (call, interceptedHeaders) -> {
          throw new AssertionError("Next handler should not be called");
        };

    interceptor.interceptCall(mockCall, headers, next);

    verify(mockCall).close(statusCaptor.capture(), metadataCaptor.capture());
    assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    assertThat(statusCaptor.getValue().getDescription()).contains("Failed to parse JWT token");
    verify(mockMetrics).incrementAuthenticationCounter(FAILURE);
  }

  @Test
  public void interceptCall_audienceValidationException_permissionDenied() {
    Metadata headers = new Metadata();
    headers.put(AUTHORIZATION_KEY, "Bearer bad-audience-token");

    when(mockValidator.parseAndValidate("bad-audience-token"))
        .thenThrow(new AudienceValidationException("Audience mismatch"));

    ServerCallHandler<String, String> next =
        (call, interceptedHeaders) -> {
          throw new AssertionError("Next handler should not be called");
        };

    interceptor.interceptCall(mockCall, headers, next);

    verify(mockCall).close(statusCaptor.capture(), metadataCaptor.capture());
    assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    assertThat(statusCaptor.getValue().getDescription()).isEqualTo("Audience mismatch");
    verify(mockMetrics).incrementAuthenticationCounter(FAILURE);
  }

  @Test
  public void interceptCall_trustDomainOrCertificateUnavailable_serviceNotReadyUnavailable() {
    Metadata headers = new Metadata();
    headers.put(AUTHORIZATION_KEY, "Bearer token");

    when(mockValidator.parseAndValidate("token"))
        .thenThrow(
            new IllegalStateException(
                "Measurement-bound certificate has not been initialized yet"));

    ServerCallHandler<String, String> next =
        (call, interceptedHeaders) -> {
          throw new AssertionError("Next handler should not be called");
        };

    interceptor.interceptCall(mockCall, headers, next);

    verify(mockCall).close(statusCaptor.capture(), metadataCaptor.capture());
    assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
    assertThat(statusCaptor.getValue().getDescription())
        .isEqualTo("Service not ready: Measurement-bound certificate has not been initialized yet");
    org.mockito.Mockito.verifyNoInteractions(mockMetrics);
  }

  @Test
  public void interceptCall_illegalArgumentException_invalidArgument() {
    Metadata headers = new Metadata();
    headers.put(AUTHORIZATION_KEY, "Bearer token");

    when(mockValidator.parseAndValidate("token"))
        .thenThrow(new IllegalArgumentException("Illegal argument"));

    ServerCallHandler<String, String> next =
        (call, interceptedHeaders) -> {
          throw new AssertionError("Next handler should not be called");
        };

    interceptor.interceptCall(mockCall, headers, next);

    verify(mockCall).close(statusCaptor.capture(), metadataCaptor.capture());
    assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    assertThat(statusCaptor.getValue().getDescription()).isEqualTo("Illegal argument");
    verify(mockMetrics).incrementAuthenticationCounter(FAILURE);
  }
}
