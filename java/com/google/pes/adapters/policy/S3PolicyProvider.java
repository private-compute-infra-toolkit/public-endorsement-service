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

import com.google.common.flogger.FluentLogger;
import com.google.pes.adapters.PublisherPolicyMapper;
import com.google.pes.annotations.PolicyBucket;
import com.google.pes.domain.model.PublisherPolicy;
import com.google.pes.domain.ports.PolicyException;
import com.google.pes.domain.ports.PolicyProvider;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3PolicyProvider implements PolicyProvider {
  FluentLogger logger = FluentLogger.forEnclosingClass();
  private final String bucketName;
  private final S3Client s3Client;

  @Inject
  public S3PolicyProvider(S3Client s3Client, @PolicyBucket String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  @Override
  public Optional<PublisherPolicy> get(String publisherId) {
    Optional<ResponseInputStream<GetObjectResponse>> s3ObjectOpt = fetchObjectFromS3(publisherId);
    if (s3ObjectOpt.isEmpty()) {
      logger.atWarning().log("Configuration for %s Publisher ID does not exist!", publisherId);
      return Optional.empty();
    }

    try (ResponseInputStream<GetObjectResponse> s3Object = s3ObjectOpt.get()) {
      String jsonContent = readContent(s3Object);
      com.google.pes.policy.v1.PublisherPolicy protoConfig = parseProtoFromJson(jsonContent);

      return Optional.of(PublisherPolicyMapper.toDomain(protoConfig));
    } catch (IOException e) {
      throw new PolicyException("Error processing S3 stream for key: " + publisherId, e);
    }
  }

  private Optional<ResponseInputStream<GetObjectResponse>> fetchObjectFromS3(String key) {
    try {
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().bucket(bucketName).key(key).build();

      return Optional.of(s3Client.getObject(getObjectRequest));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    } catch (S3Exception e) {
      throw new PolicyException(
          "Failed to fetch configuration from S3 named: " + bucketName + " for key: " + key, e);
    }
  }

  private String readContent(ResponseInputStream<GetObjectResponse> inputStream)
      throws IOException {
    try (inputStream) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private com.google.pes.policy.v1.PublisherPolicy parseProtoFromJson(String json) {
    com.google.pes.policy.v1.PublisherPolicy.Builder builder =
        com.google.pes.policy.v1.PublisherPolicy.newBuilder();
    try {
      JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
      return builder.build();
    } catch (InvalidProtocolBufferException e) {
      throw new PolicyException("Failed to parse JSON configuration into Protobuf", e);
    }
  }
}
