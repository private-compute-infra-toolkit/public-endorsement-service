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

package com.google.pes.adapters.tsa;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.pes.domain.model.TsaConfig;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RunWith(MockitoJUnitRunner.class)
public class S3TsaConfigProviderTest {

  private static final String BUCKET_NAME = "test-tsa-bucket";
  private static final String CONFIG_KEY = S3TsaConfigProvider.TSA_CONFIG_FILE_NAME;

  @Mock private S3Client s3Client;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private S3TsaConfigProvider provider;

  @Before
  public void setUp() {
    provider = new S3TsaConfigProvider(s3Client, BUCKET_NAME, objectMapper);
  }

  private ResponseInputStream<GetObjectResponse> createS3Stream(String content) {
    GetObjectResponse response = GetObjectResponse.builder().build();
    InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    return new ResponseInputStream<>(response, AbortableInputStream.create(inputStream));
  }

  @Test
  public void getConfig_whenEnabledWithUrls_returnsCorrectConfig() {
    String json =
        """
        {
          "enabled": true,
          "tsa_urls": [
            "https://tsa1.example.com/tsa",
            "https://tsa2.example.com/tsa"
          ]
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config.enabled()).isTrue();
    assertThat(config.tsaUrls())
        .containsExactly(
            URI.create("https://tsa1.example.com/tsa"), URI.create("https://tsa2.example.com/tsa"))
        .inOrder();
    ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3Client).getObject(captor.capture());
    assertThat(captor.getValue().bucket()).isEqualTo(BUCKET_NAME);
    assertThat(captor.getValue().key()).isEqualTo(CONFIG_KEY);
  }

  @Test
  public void getConfig_whenDisabled_returnsDisabledConfig() {
    String json =
        """
        {
          "enabled": false,
          "tsa_urls": []
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenTsaUrlsOmitted_returnsEmptyUrlList() {
    String json =
        """
        {
          "enabled": false
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenTsaUrlsNull_returnsEmptyUrlList() {
    String json =
        """
        {
          "enabled": false,
          "tsa_urls": null
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenTsaUrlsContainsNullElement_returnsDisabledConfig() {
    String json =
        """
        {
          "enabled": true,
          "tsa_urls": ["https://tsa.example.com/tsa", null]
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
  }

  @Test
  public void getConfig_whenJsonContainsUnknownProperties_ignoresUnknownProperties() {
    String json =
        """
        {
          "enabled": true,
          "tsa_urls": ["https://tsa.example.com/tsa"],
          "unknown_field": "some_value"
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config.enabled()).isTrue();
    assertThat(config.tsaUrls()).containsExactly(URI.create("https://tsa.example.com/tsa"));
  }

  @Test
  public void getConfig_whenNoSuchBucketException_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build());

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenNoSuchKeyException_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenS3ExceptionNoSuchBucketErrorCode_returnsDisabledConfig() {
    software.amazon.awssdk.awscore.exception.AwsErrorDetails errorDetails =
        software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
            .errorCode("NoSuchBucket")
            .errorMessage("The specified bucket does not exist")
            .build();
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(
            (S3Exception)
                S3Exception.builder()
                    .statusCode(404)
                    .awsErrorDetails(errorDetails)
                    .message("The specified bucket does not exist")
                    .build());

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenS3ExceptionNoSuchKeyErrorCode_returnsDisabledConfig() {
    software.amazon.awssdk.awscore.exception.AwsErrorDetails errorDetails =
        software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
            .errorCode("NoSuchKey")
            .errorMessage("The specified key does not exist")
            .build();
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(
            (S3Exception)
                S3Exception.builder()
                    .statusCode(404)
                    .awsErrorDetails(errorDetails)
                    .message("The specified key does not exist")
                    .build());

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenS3ExceptionStatusCode404_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(
            (S3Exception) S3Exception.builder().statusCode(404).message("Not Found").build());

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenS3ExceptionOtherStatusCode_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(
            (S3Exception) S3Exception.builder().statusCode(403).message("Access denied").build());

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
  }

  @Test
  public void getConfig_whenMalformedJson_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(createS3Stream("not a valid json"));

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
  }

  @Test
  public void getConfig_whenInvalidUriInConfig_returnsDisabledConfig() {
    String json =
        """
        {
          "enabled": true,
          "tsa_urls": ["https://invalid uri with spaces"]
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
  }

  @Test
  public void getConfig_whenRelativeUriInConfig_returnsDisabledConfig() {
    String json =
        """
        {
          "enabled": true,
          "tsa_urls": ["tsa.example.com"]
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
  }

  @Test
  public void getConfig_whenNonHttpSchemeInConfig_returnsDisabledConfig() {
    String json =
        """
        {
          "enabled": true,
          "tsa_urls": ["ftp://tsa.example.com"]
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
  }

  @Test
  public void getConfig_whenEnabledAndUrlsEmpty_returnsDisabledConfig() {
    String json =
        """
        {
          "enabled": true,
          "tsa_urls": []
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
  }

  @Test
  public void getConfig_whenUriHasMissingHost_returnsDisabledConfig() {
    String json =
        """
        {
          "enabled": true,
          "tsa_urls": ["https:///tsa"]
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
  }

  @Test
  public void getConfig_whenSdkException_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(
            software.amazon.awssdk.core.exception.SdkClientException.create("Connection refused"));

    TsaConfig config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfig.DISABLED);
  }

  @Test
  public void getConfig_withinTtl_cachesResultAndAvoidsExtraS3Calls() {
    String json =
        """
        {
          "enabled": true,
          "tsa_urls": ["https://tsa.example.com/tsa"]
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfig config1 = provider.getConfig();
    TsaConfig config2 = provider.getConfig();

    assertThat(config1).isEqualTo(config2);
    verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
  }

  @Test
  public void getConfig_whenFailureOccurs_cachesDisabledConfigWithinTtl() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(
            (S3Exception) S3Exception.builder().statusCode(500).message("Internal Error").build());

    TsaConfig config1 = provider.getConfig();
    TsaConfig config2 = provider.getConfig();

    assertThat(config1).isEqualTo(TsaConfig.DISABLED);
    assertThat(config2).isEqualTo(TsaConfig.DISABLED);
    verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
  }
}
