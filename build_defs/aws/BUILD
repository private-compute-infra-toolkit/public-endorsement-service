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

load("@com_github_google_rpmpack//:def.bzl", "pkg_tar2rpm")
load("@rules_pkg//:pkg.bzl", "pkg_tar")

package(
    default_visibility = ["//visibility:public"],
)

pkg_tar(
    name = "cw_agent_config",
    srcs = [
        "amazon-cloudwatch-agent.json",
        "prometheus.yaml",
    ],
    mode = "0644",
    ownername = "root.root",
    package_dir = "opt/google/metrics",
)

pkg_tar2rpm(
    name = "pes_metrics_rpm",
    data = ":cw_agent_config",
    pkg_name = "pes-metrics",
    release = "0",
    version = "0.0.1",
)
