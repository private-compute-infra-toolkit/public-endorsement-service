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

/** Composes AWS resource names from configuration and IMDS data. */
public class AwsResourceNamesProvider {

  private final PesArgs args;
  private final AwsInstanceMetadata awsInstanceMetadata;

  public AwsResourceNamesProvider(PesArgs args, AwsInstanceMetadata awsInstanceMetadata) {
    this.args = args;
    this.awsInstanceMetadata = awsInstanceMetadata;
  }

  public AwsResourceNames getRecord() {
    String certBackupBucketName =
        String.format(
            "%s-%s-%s",
            args.getCertBackupBucketPrefix(),
            awsInstanceMetadata.accountId(),
            awsInstanceMetadata.region());
    String keyBackupBucketName =
        String.format(
            "%s-%s-%s",
            args.getKeyBackupBucketPrefix(),
            awsInstanceMetadata.accountId(),
            awsInstanceMetadata.region());
    String configBucketName =
        String.format(
            "%s-%s-%s",
            args.getConfigurationBucketPrefix(),
            awsInstanceMetadata.accountId(),
            awsInstanceMetadata.region());
    String kmsKeyArn =
        String.format(
            "arn:aws:kms:%s:%s:%s",
            awsInstanceMetadata.region(),
            awsInstanceMetadata.accountId(),
            args.getMbsKmsKeySuffix());
    String tledgerCertBucketName =
        String.format(
            "%s-%s-%s",
            args.getTLedgerCertBucketPrefix(),
            awsInstanceMetadata.accountId(),
            awsInstanceMetadata.region());

    return new AwsResourceNames(
        certBackupBucketName,
        keyBackupBucketName,
        configBucketName,
        kmsKeyArn,
        tledgerCertBucketName);
  }
}
