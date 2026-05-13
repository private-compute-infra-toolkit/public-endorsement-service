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

package com.google.pes.adapters.tlog;

import com.google.pes.domain.ports.TLogException;
import com.google.protobuf.ByteString;
import jakarta.inject.Inject;
import java.io.IOException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class TLedgerCertificatFetcher {
  private final S3Client s3Client;
  private final String certificateBucketName;
  private final String certificateFileName;

  @Inject
  TLedgerCertificatFetcher(
      S3Client s3Client,
      @TLedgerCertBucketName String certificateBucketName,
      @TLedgerCertName String certificateFileName) {
    this.s3Client = s3Client;
    this.certificateBucketName = certificateBucketName;
    this.certificateFileName = certificateFileName;
  }

  public ByteString fetch() {
    try (ResponseInputStream<GetObjectResponse> inputStream = fetchCertFromS3()) {
      return ByteString.readFrom(inputStream);
    } catch (IOException e) {
      throw new TLogException("Failed to read TLedger certificate from S3 response", e);
    }
  }

  private ResponseInputStream<GetObjectResponse> fetchCertFromS3() {
    try {
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().bucket(certificateBucketName).key(certificateFileName).build();

      return s3Client.getObject(getObjectRequest);
    } catch (S3Exception e) {
      throw new TLogException(
          "Failed to fetch TLedger certificate from S3 named: "
              + certificateBucketName
              + " for key: "
              + certificateFileName,
          e);
    }
  }
}
