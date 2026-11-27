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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.pes.domain.model.TsaConfiguration;
import com.google.pes.domain.ports.TsaException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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

  private S3TsaConfigProvider provider;

  @Before
  public void setUp() {
    provider = new S3TsaConfigProvider(s3Client, BUCKET_NAME);
  }

  private ResponseInputStream<GetObjectResponse> createS3Stream(String content) {
    GetObjectResponse response = GetObjectResponse.builder().build();
    InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    return new ResponseInputStream<>(response, AbortableInputStream.create(inputStream));
  }

  @Test
  public void getConfig_whenEnabledWithUrls_returnsCorrectConfig() {
    String textProto =
        """
        enabled {
          tsa_urls: "https://tsa1.example.com/tsa"
          tsa_urls: "https://tsa2.example.com/tsa"
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config.enabled()).isTrue();
    assertThat(config.tsaUrls())
        .containsExactly(
            URI.create("https://tsa1.example.com/tsa"), URI.create("https://tsa2.example.com/tsa"))
        .inOrder();
    ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3Client).getObject(captor.capture());
    assertThat(captor.getValue().bucket()).isEqualTo(BUCKET_NAME);
    assertThat(captor.getValue().key()).isEqualTo("tsa_config.textproto");
    assertThat(captor.getValue().key()).isEqualTo(CONFIG_KEY);
  }

  @Test
  public void getConfig_whenDisabled_returnsDisabledConfig() {
    String textProto =
        """
        disabled {}
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenEmptyTextProto_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(""));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenBothEnabledAndDisabledSet_returnsDisabledConfig() {
    String textProto =
        """
        disabled {}
        enabled {
          tsa_urls: "https://tsa.example.com/tsa"
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenTsaUrlsContainsEmptyString_returnsDisabledConfig() {
    String textProto =
        """
        enabled {
          tsa_urls: "https://tsa.example.com/tsa"
          tsa_urls: ""
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenTextProtoContainsUnknownFields_ignoresUnknownFields() {
    String textProto =
        """
        enabled {
          tsa_urls: "https://tsa.example.com/tsa"
          unknown_inner_field: "ignored"
        }
        unknown_field: "some_value"
        unknown_block { nested: 123 }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config.enabled()).isTrue();
    assertThat(config.tsaUrls()).containsExactly(URI.create("https://tsa.example.com/tsa"));
  }

  @Test
  public void getConfig_whenTextProtoContainsComments_returnsCorrectConfig() {
    String textProto =
        """
        # Primary TSA configuration
        enabled {
          tsa_urls: "https://tsa.example.com/tsa" # inline comment
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config.enabled()).isTrue();
    assertThat(config.tsaUrls()).containsExactly(URI.create("https://tsa.example.com/tsa"));
  }

  @Test
  public void getConfig_whenNoSuchBucketException_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build());

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenNoSuchKeyException_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
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

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
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

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenS3ExceptionStatusCode404_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(
            (S3Exception) S3Exception.builder().statusCode(404).message("Not Found").build());

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
    assertThat(config.enabled()).isFalse();
    assertThat(config.tsaUrls()).isEmpty();
  }

  @Test
  public void getConfig_whenS3ExceptionOtherStatusCode_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(
            (S3Exception) S3Exception.builder().statusCode(403).message("Access denied").build());

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenMalformedTextProto_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(createS3Stream("not a valid textproto"));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenJsonInsteadOfTextProto_returnsDisabledConfig() {
    String json =
        """
        {
          "enabled": true,
          "tsa_urls": ["https://tsa.example.com/tsa"]
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(json));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenInvalidUriInConfig_returnsDisabledConfig() {
    String textProto =
        """
        enabled {
          tsa_urls: "https://invalid uri with spaces"
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenIoExceptionReadingStream_returnsDisabledConfig() {
    InputStream failingStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Simulated stream read error");
          }
        };
    ResponseInputStream<GetObjectResponse> s3Stream =
        new ResponseInputStream<>(
            GetObjectResponse.builder().build(), AbortableInputStream.create(failingStream));
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(s3Stream);

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void parseAndValidateUri_whenNull_throwsTsaException() {
    TsaException ex =
        assertThrows(TsaException.class, () -> S3TsaConfigProvider.parseAndValidateUri(null));

    assertThat(ex).hasMessageThat().contains("cannot be null");
  }

  @Test
  public void parseAndValidateUri_whenSyntaxInvalid_throwsTsaExceptionWithoutUrlOrCause() {
    TsaException ex =
        assertThrows(
            TsaException.class,
            () ->
                S3TsaConfigProvider.parseAndValidateUri(
                    "https://secret-tsa.example.com/tsa?bad query"));

    assertThat(ex).hasMessageThat().contains("Invalid TSA URI syntax in configuration");
    assertThat(ex).hasMessageThat().contains("at index 38");
    assertThat(ex).hasMessageThat().doesNotContain("secret-tsa.example.com");
    assertThat(ex).hasCauseThat().isNull();
  }

  @Test
  public void getConfig_whenRelativeUriInConfig_returnsDisabledConfig() {
    String textProto =
        """
        enabled {
          tsa_urls: "tsa.example.com"
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenNonHttpSchemeInConfig_returnsDisabledConfig() {
    String textProto =
        """
        enabled {
          tsa_urls: "ftp://tsa.example.com"
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenHttpSchemeInConfig_returnsCorrectConfig() {
    String textProto =
        """
        enabled {
          tsa_urls: "http://tsa.example.com/tsa"
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config.enabled()).isTrue();
    assertThat(config.tsaUrls()).containsExactly(URI.create("http://tsa.example.com/tsa"));
  }

  @Test
  public void getConfig_whenEnabledAndUrlsEmpty_returnsDisabledConfig() {
    String textProto =
        """
        enabled {}
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenEnabledAndUrlsEmptyList_returnsDisabledConfig() {
    String textProto =
        """
        enabled {
          tsa_urls: []
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenUriHasMissingHost_returnsDisabledConfig() {
    String textProto =
        """
        enabled {
          tsa_urls: "https:///tsa"
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_whenSdkException_returnsDisabledConfig() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(
            software.amazon.awssdk.core.exception.SdkClientException.create("Connection refused"));

    TsaConfiguration config = provider.getConfig();

    assertThat(config).isEqualTo(TsaConfiguration.DISABLED);
  }

  @Test
  public void getConfig_withinTtl_cachesResultAndAvoidsExtraS3Calls() {
    String textProto =
        """
        enabled {
          tsa_urls: "https://tsa.example.com/tsa"
        }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(createS3Stream(textProto));

    TsaConfiguration config1 = provider.getConfig();
    TsaConfiguration config2 = provider.getConfig();

    assertThat(config1).isEqualTo(config2);
    verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
  }

  @Test
  public void getConfig_whenFailureOccurs_cachesDisabledConfigWithinTtl() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(
            (S3Exception) S3Exception.builder().statusCode(500).message("Internal Error").build());

    TsaConfiguration config1 = provider.getConfig();
    TsaConfiguration config2 = provider.getConfig();

    assertThat(config1).isEqualTo(TsaConfiguration.DISABLED);
    assertThat(config2).isEqualTo(TsaConfiguration.DISABLED);
    verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
  }
}
