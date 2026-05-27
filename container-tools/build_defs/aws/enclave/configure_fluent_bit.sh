#!/bin/bash
# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

echo "Fetching EC2 metadata and tags for Fluent Bit..."

token=$(curl -s --fail-with-body -X PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 120")
region=$(curl -s --fail-with-body http://169.254.169.254/latest/meta-data/placement/region -H "X-aws-ec2-metadata-token: ${token}")
instance_id=$(curl -s --fail-with-body http://169.254.169.254/latest/meta-data/instance-id -H "X-aws-ec2-metadata-token: ${token}")

tags=$(aws --region "$region" ec2 describe-tags --filters Name=resource-id,Values="${instance_id}")
tags_kv=$(echo "${tags}" | jq "[.Tags[] | {key:.Key, value:.Value}] | from_entries")
environment=$(echo "${tags_kv}" | jq -r ".environment")

if [ "$environment" == "null" ] || [ -z "$environment" ]; then
  echo "Error: environment tag not found."
  exit 1
fi

# Load the service name from the static file
service_name=$(cat /opt/google/enclave/service_name.txt)

{
  echo "SERVICE_NAME=${service_name}"
  echo "LOG_GROUP_NAME=/aws/enclave/${service_name}-${environment}"
  echo "AWS_REGION=${region}"
  echo "INSTANCE_ID=${instance_id}"
} > /tmp/fluent-bit-env

echo "Fluent Bit environment configured."
