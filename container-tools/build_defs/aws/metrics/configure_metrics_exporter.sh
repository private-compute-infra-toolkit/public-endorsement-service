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

main() {
  local metrics_dir="/opt/google/metrics"
  local cw_agent_config="${metrics_dir}/amazon-cloudwatch-agent.json"
  local prometheus_config="${metrics_dir}/prometheus.yaml"

  if [[ ! -f "${cw_agent_config}" ]]; then
    echo "Metrics exporter configuration not found at ${cw_agent_config}. Skipping metrics exporter setup."
    # Ensure the service is stopped and disabled if it was somehow running
    systemctl disable --now amazon-cloudwatch-agent.service || true
    return 0
  fi

  if [[ ! -f "${prometheus_config}" ]]; then
    echo "Prometheus configuration not found at ${prometheus_config}. Skipping metrics exporter setup."
    # Ensure the service is stopped and disabled if it was somehow running
    systemctl disable --now amazon-cloudwatch-agent.service || true
    return 0
  fi

  echo "Configuring Metrics Exporter..."

  # The CloudWatch Agent expects the Prometheus configuration to be present at this default location.
  mkdir -p /opt/aws/amazon-cloudwatch-agent/etc
  cp "${prometheus_config}" /opt/aws/amazon-cloudwatch-agent/etc/prometheus.yaml

  # amazon-cloudwatch-agent-ctl will translate the JSON config and automatically
  # enable and start the underlying amazon-cloudwatch-agent.service.
  if /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
      -a fetch-config \
      -m ec2 \
      -s \
      -c "file:${cw_agent_config}"; then
    echo "Metrics Exporter configured and started successfully."
  else
    echo "Error: Failed to configure and start Metrics Exporter."
    return 1
  fi
}

main "$@"
