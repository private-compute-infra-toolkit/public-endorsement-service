# Copyright 2026 Google LLC
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

def enclave_watcher_rpm(name, service_name):
    """Generates a service-specific RPM containing static enclave config scripts and systemd configurations.

    Args:
        name: The name of the target RPM.
        service_name: The name of the service used for template expansion.
    """

    # Generate a static text file containing the service name
    native.genrule(
        name = name + "_gen_service_name_txt",
        outs = [name + "/service_name.txt"],
        cmd = "echo -n '" + service_name + "' > $@",
    )

    pkg_tar(
        name = name + "_service_name",
        srcs = [":" + name + "/service_name.txt"],
        mode = "0644",
        ownername = "root.root",
        package_dir = "opt/google/enclave",
        strip_prefix = name,
    )

    pkg_tar(
        name = name + "_watcher_script",
        srcs = [
            "@rules_aws//build_defs/aws/enclave:configure_fluent_bit.sh",
            "@rules_aws//build_defs/aws/enclave:enclave_watcher.sh",
        ],
        mode = "0755",
        ownername = "root.root",
        package_dir = "opt/google/enclave",
    )

    pkg_tar(
        name = name + "_watcher_service",
        srcs = [
            "@rules_aws//build_defs/aws/enclave:enclave.service",
            "@rules_aws//build_defs/aws/enclave:configure-fluent-bit.service",
        ],
        mode = "0644",
        ownername = "root.root",
        package_dir = "etc/systemd/system",
    )

    pkg_tar(
        name = name + "_fluent_bit_config",
        srcs = ["@rules_aws//build_defs/aws/enclave:fluent-bit.conf"],
        mode = "0644",
        ownername = "root.root",
        package_dir = "opt/google/enclave",
    )

    pkg_tar(
        name = name + "_fluent_bit_repo",
        srcs = ["@rules_aws//build_defs/aws/enclave:fluent-bit.repo"],
        mode = "0644",
        ownername = "root.root",
        package_dir = "etc/yum.repos.d",
    )

    pkg_tar(
        name = name + "_fluent_bit_override",
        srcs = ["@rules_aws//build_defs/aws/enclave:override.conf"],
        mode = "0644",
        ownername = "root.root",
        package_dir = "etc/systemd/system/fluent-bit.service.d",
    )

    pkg_tar(
        name = name + "_enclave_worker_tar",
        ownername = "root.root",
        deps = [
            ":" + name + "_watcher_script",
            ":" + name + "_watcher_service",
            ":" + name + "_fluent_bit_config",
            ":" + name + "_fluent_bit_repo",
            ":" + name + "_fluent_bit_override",
            ":" + name + "_service_name",
        ],
    )

    pkg_tar2rpm(
        name = name,
        data = ":" + name + "_enclave_worker_tar",
        pkg_name = service_name + "_watcher",
        postin = """
        systemctl daemon-reload
        """,
        postun = """
        systemctl daemon-reload
        """,
        preun = """
        systemctl stop enclave.service
        systemctl disable enclave.service
        """,
        release = "0",
        requires = [
            "python3-pyyaml",
        ],
        use_dir_allowlist = True,
        version = "0.0.1",
    )
