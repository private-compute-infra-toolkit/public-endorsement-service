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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.flogger.FluentLogger;
import com.google.pes.domain.model.TsaConfig;
import com.google.pes.domain.ports.TsaConfigProvider;
import com.google.pes.domain.ports.TsaException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Provides TSA configuration from a dedicated AWS S3 bucket with TTL-based caching. */
@Singleton
public class S3TsaConfigProvider implements TsaConfigProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);
  public static final String TSA_CONFIG_FILE_NAME = "tsa-config.json";

  private final S3Client s3Client;
  private final String bucketName;
  private final ObjectMapper objectMapper;
  private final Supplier<TsaConfig> configSupplier;

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TsaConfigDto(
      @JsonProperty("enabled") boolean enabled, @JsonProperty("tsa_urls") List<String> tsaUrls) {}

  @Inject
  public S3TsaConfigProvider(
      S3Client s3Client, @TsaConfigBucketName String bucketName, ObjectMapper objectMapper) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
    this.objectMapper = objectMapper;
    this.configSupplier =
        Suppliers.memoizeWithExpiration(
            this::fetchConfig, CACHE_TTL.toNanos(), TimeUnit.NANOSECONDS);
  }

  @Override
  public TsaConfig getConfig() {
    return configSupplier.get();
  }

  private TsaConfig fetchConfig() {
    GetObjectRequest getObjectRequest =
        GetObjectRequest.builder().bucket(bucketName).key(TSA_CONFIG_FILE_NAME).build();
    try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getObjectRequest)) {
      TsaConfigDto dto = objectMapper.readValue(s3Stream, TsaConfigDto.class);
      if (dto == null) {
        return TsaConfig.DISABLED;
      }
      List<URI> uris =
          dto.tsaUrls() != null
              ? dto.tsaUrls().stream().map(S3TsaConfigProvider::parseAndValidateUri).toList()
              : List.of();
      if (dto.enabled() && uris.isEmpty()) {
        throw new TsaException(
            "TSA is enabled but no TSA URLs were configured in "
                + bucketName
                + "/"
                + TSA_CONFIG_FILE_NAME);
      }

      return new TsaConfig(dto.enabled(), uris);
    } catch (SdkException e) {
      if (e instanceof S3Exception s3e && isNotFound(s3e)) {
        logger.atWarning().log(
            "TSA configuration '%s/%s' not found (S3 error: %s). Defaulting to disabled.",
            bucketName,
            TSA_CONFIG_FILE_NAME,
            s3e.awsErrorDetails() != null ? s3e.awsErrorDetails().errorCode() : s3e.getMessage());
      } else {
        logger.atWarning().withCause(e).log(
            "Failed to fetch TSA configuration from S3: %s/%s. Defaulting to disabled.",
            bucketName, TSA_CONFIG_FILE_NAME);
      }
      return TsaConfig.DISABLED;
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to parse TSA configuration from S3: %s/%s. Defaulting to disabled.",
          bucketName, TSA_CONFIG_FILE_NAME);
      return TsaConfig.DISABLED;
    } catch (IllegalArgumentException | TsaException e) {
      logger.atWarning().withCause(e).log(
          "Invalid TSA configuration in %s/%s. Defaulting to disabled.",
          bucketName, TSA_CONFIG_FILE_NAME);
      return TsaConfig.DISABLED;
    }
  }

  private static URI parseAndValidateUri(String urlStr) {
    if (urlStr == null) {
      throw new TsaException("TSA URI in configuration cannot be null");
    }
    URI uri = URI.create(urlStr);
    if (!uri.isAbsolute()
        || uri.getHost() == null
        || uri.getHost().isBlank()
        || (!"https".equalsIgnoreCase(uri.getScheme())
            && !"http".equalsIgnoreCase(uri.getScheme()))) {
      throw new TsaException("TSA URI must be an absolute HTTP or HTTPS URL: " + uri);
    }
    return uri;
  }

  private static boolean isNotFound(S3Exception e) {
    if (e instanceof NoSuchBucketException || e instanceof NoSuchKeyException) {
      return true;
    }
    if (e.statusCode() == 404) {
      return true;
    }
    if (e.awsErrorDetails() != null) {
      String errorCode = e.awsErrorDetails().errorCode();
      if ("NoSuchBucket".equalsIgnoreCase(errorCode) || "NoSuchKey".equalsIgnoreCase(errorCode)) {
        return true;
      }
    }
    return false;
  }
}
