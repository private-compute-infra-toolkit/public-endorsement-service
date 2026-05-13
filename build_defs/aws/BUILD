# Copyright 2025 Google LLC
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

package(default_visibility = ["//visibility:public"])

exports_files([
    "pes_ami.pkr.hcl",
    "setup_pes_enclave.sh",
])

pkg_tar(
    name = "watcher_script",
    srcs = [
        "pes_watcher.sh",
    ],
    mode = "0755",
    ownername = "root.root",
    package_dir = "opt/google/pes",
)

pkg_tar(
    name = "watcher_service",
    srcs = [
        "pes.service",
    ],
    mode = "0644",
    ownername = "root.root",
    package_dir = "etc/systemd/system",
)

pkg_tar(
    name = "enclave_worker_tar",
    ownername = "root.root",
    deps = [
        ":watcher_script",
        ":watcher_service",
    ],
)

pkg_tar2rpm(
    name = "pes_watcher_rpm",
    data = ":enclave_worker_tar",
    pkg_name = "pes_watcher",
    # Signal to systemd that the service files changed on install.
    postin = """
    systemctl daemon-reload
    """,
    # Signal to systemd that the service files changed on uninstall.
    postun = """
    systemctl daemon-reload
    """,
    # Stop service before uninstall starts.
    preun = """
    systemctl stop pes.service
    systemctl disable pes.service
    """,
    release = "0",
    requires = [
        "python3-pyyaml",
    ],
    use_dir_allowlist = True,
    version = "0.0.1",
)
