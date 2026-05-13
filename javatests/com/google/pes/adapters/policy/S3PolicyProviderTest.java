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

package com.google.pes.adapters.policy;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.pes.domain.model.PublisherPolicy;
import com.google.pes.domain.model.VerificationMaterial;
import com.google.pes.domain.ports.PolicyException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RunWith(MockitoJUnitRunner.class)
public class S3PolicyProviderTest {

  private static final String BUCKET_NAME = "test-bucket";
  private static final String PUBLISHER_ID = "pub-123";

  @Mock private S3Client s3Client;

  private S3PolicyProvider provider;

  @Before
  public void setUp() {
    provider = new S3PolicyProvider(s3Client, BUCKET_NAME);
  }

  @Test
  public void get_ShouldReturnConfiguration_WhenJsonIsValid() {
    String validJson =
        "{\n"
            + "  \"publisher_id\": \"pub-123\",\n"
            + "  \"oidc_token_claims\": [\n"
            + "    {\"issuer\": \"expected-issuer\", \"subject\": \"expected-subject\"}\n"
            + "  ],\n"
            + "  \"verification_material\": [{\"ecdsa_p256_sha256\": {\"der_bytes\": \"SGkgbW9t\"}"
            + " }] \n"
            + "}";
    mockS3Response(validJson);

    Optional<PublisherPolicy> resultOptional = provider.get(PUBLISHER_ID);

    assertThat(resultOptional).isPresent();
    PublisherPolicy result = resultOptional.get();
    assertThat(result.publisherId()).isEqualTo("pub-123");
    assertThat(result.oidcTokenClaims()).hasSize(1);
    assertThat(result.oidcTokenClaims().get(0).issuer()).isEqualTo("expected-issuer");
    assertThat(result.oidcTokenClaims().get(0).subject()).isEqualTo("expected-subject");
    assertThat(result.verificationMaterialList().get(0).content().toStringUtf8())
        .isEqualTo("Hi mom");
    assertThat(result.verificationMaterialList().get(0).format())
        .isEqualTo(VerificationMaterial.Format.ECDSA_P256_SHA256);
    verify(s3Client)
        .getObject(
            argThat(
                (GetObjectRequest request) ->
                    request.bucket().equals(BUCKET_NAME) && request.key().equals(PUBLISHER_ID)));
  }

  @Test
  public void get_ShouldReturnEmpty_NoSuchKeyException() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().build());

    Optional<PublisherPolicy> resultOptional = provider.get(PUBLISHER_ID);

    assertThat(resultOptional).isEmpty();
  }

  @Test
  public void get_ShouldThrowPolicyException_WhenJsonIsInvalid() {
    mockS3Response("{ not valid json }");

    PolicyException e = assertThrows(PolicyException.class, () -> provider.get(PUBLISHER_ID));
  }

  @Test
  public void get_ShouldThrowPolicyException_WhenS3Fails() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(S3Exception.builder().message("Access Denied").build());

    PolicyException e = assertThrows(PolicyException.class, () -> provider.get(PUBLISHER_ID));

    assertThat(e).hasCauseThat().isInstanceOf(S3Exception.class);
  }

  private void mockS3Response(String jsonContent) {
    InputStream contentStream =
        new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8));

    GetObjectResponse responseMeta = GetObjectResponse.builder().build();

    ResponseInputStream<GetObjectResponse> s3Stream =
        new ResponseInputStream<>(responseMeta, AbortableInputStream.create(contentStream));

    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(s3Stream);
  }
}
