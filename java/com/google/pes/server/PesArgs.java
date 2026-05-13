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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(separators = "=")
public class PesArgs {
  @Parameter(
      names = {"--help", "-h"},
      help = true,
      description = "Display help information")
  private boolean help;

  @Parameter(
      names = "--tledger-url",
      description =
          "The base HTTP(S) URL for the TLedger service API. This URL is used as the endpoint for"
              + " all API requests to TLedger.")
  private String tLedgerUrl;

  @Parameter(
      names = "--configuration-bucket-prefix",
      description = "Prefix for the S3 bucket where client configuration is stored")
  private String configurationBucketPrefix = "pes-configuration";

  @Parameter(names = "--mbs-kms-key-suffix", description = "Suffix (alias) of the MBS KMS key")
  private String mbsKmsKeySuffix = "alias/pes-key-encryption-key";

  @Parameter(
      names = "--cert-backup-bucket-prefix",
      description = "Prefix for the S3 bucket for PES public artifacts.")
  private String certBackupBucketPrefix = "pes-root-cert-backup";

  @Parameter(
      names = "--key-backup-bucket-prefix",
      description = "Prefix for the MBS S3 key backup bucket")
  private String keyBackupBucketPrefix = "pes-root-key-backup";

  @Parameter(
      names = "--tledger-cert-bucket-prefix",
      description = "Prefix for the S3 bucket where client tledger public cert is stored")
  private String tledegrCertBucketPrefix = "tldgr-root-cert-backup";

  public boolean isHelp() {
    return help;
  }

  public String getTLedgerUrl() {
    return tLedgerUrl;
  }

  public String getConfigurationBucketPrefix() {
    return configurationBucketPrefix;
  }

  public String getMbsKmsKeySuffix() {
    return mbsKmsKeySuffix;
  }

  public String getKeyBackupBucketPrefix() {
    return keyBackupBucketPrefix;
  }

  public String getCertBackupBucketPrefix() {
    return certBackupBucketPrefix;
  }

  public String getTLedgerCertBucketPrefix() {
    return tledegrCertBucketPrefix;
  }
}
