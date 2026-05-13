# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
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

# Tag all targets in this package as manual because all //cc/... targets are
# currently built in a container, conflicting with :reproducible_proxy_outputs
# which is built in a different container. These targets should only be built
# when being explicitly included as an RPM for an AMI.

pkg_tar(
    name = "proxy_bin",
    srcs = [
        "//cc/aws/proxy/src:proxify",
        "//cc/aws/proxy/src:proxy",
        "//cc/aws/proxy/src:proxy_preload",
        "//cc/aws/proxy/src:socket_vendor",
    ],
    mode = "0755",
    ownername = "root.root",
    package_dir = "opt/google/proxy",
)

pkg_tar(
    name = "proxy_service",
    srcs = [
        "vsockproxy.service",
    ],
    mode = "0644",
    ownername = "root.root",
    package_dir = "etc/systemd/system",
)

pkg_tar(
    name = "proxy_tar",
    ownername = "root.root",
    deps = [
        ":proxy_bin",
        ":proxy_service",
    ],
)

pkg_tar2rpm(
    name = "vsockproxy_rpm",
    data = ":proxy_tar",
    pkg_name = "vsockproxy",
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
    systemctl stop vsockproxy.service
    systemctl disable vsockproxy.service
    """,
    release = "0",
    use_dir_allowlist = True,
    version = "0.0.1",
)
