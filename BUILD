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

load("@bazel_skylib//rules:common_settings.bzl", "string_flag")
load("@rules_aws//build_defs/aws/enclave:aws_eif_and_ami.bzl", "aws_eif_and_ami")

package(default_visibility = ["//visibility:public"])

string_flag(
    name = "ami_name_flag",
    build_setting_default = "pes-enclave",
)

string_flag(
    name = "aws_region_flag",
    build_setting_default = "us-east-1",
)

string_flag(
    name = "subnet_id_flag",
    build_setting_default = "",
)

aws_eif_and_ami(
    name = "pes_aws_dev",
    additional_container_tars = [
        "@pcit_mbs//java/com/google/platform/aws/nsm:nsm_jni_tar",
    ],
    ami_name = ":ami_name_flag",
    aws_region = ":aws_region_flag",
    enable_worker_debug_mode = True,
    jar_args = [
        "--tledger-url=http://api.tledger.local",
    ],
    jar_file = "/server_main_deploy.jar",
    jar_path = "//java/com/google/pes/server:server_main_deploy.jar",
    packer_ami_config = "//build_defs/aws:pes_ami.pkr.hcl",
    startup_script = "//build_defs/aws:setup_pes_enclave.sh",
    subnet_id = ":subnet_id_flag",
    uninstall_ssh_server = False,
    watcher_rpm = "//build_defs/aws:pes_watcher_rpm",
)

aws_eif_and_ami(
    name = "pes_aws_release",
    additional_container_tars = [
        "@pcit_mbs//java/com/google/platform/aws/nsm:nsm_jni_tar",
    ],
    ami_name = ":ami_name_flag",
    aws_region = ":aws_region_flag",
    enable_worker_debug_mode = False,
    jar_args = [
        "--tledger-url=http://api.tledger.local",
    ],
    jar_file = "/server_main_deploy.jar",
    jar_path = "//java/com/google/pes/server:server_main_deploy.jar",
    packer_ami_config = "//build_defs/aws:pes_ami.pkr.hcl",
    startup_script = "//build_defs/aws:setup_pes_enclave.sh",
    subnet_id = ":subnet_id_flag",
    uninstall_ssh_server = False,
    watcher_rpm = "//build_defs/aws:pes_watcher_rpm",
)

aws_eif_and_ami(
    name = "pes_aws_integration",
    additional_container_tars = [
        "@pcit_mbs//java/com/google/platform/aws/nsm:nsm_jni_tar",
    ],
    ami_name = ":ami_name_flag",
    aws_region = ":aws_region_flag",
    enable_worker_debug_mode = True,
    jar_args = [
        "--configuration-bucket-prefix=pes-integration-tests-configuration",
        "--tledger-url=http://tledger.pes.integration.test:50051",
        "--cert-backup-bucket-prefix=pes-integration-tests-key-backup",
        "--key-backup-bucket-prefix=pes-integration-tests-key-backup",
        "--mbs-kms-key-suffix=alias/pes-integration-key-encryption-key",
        "--tledger-cert-bucket-prefix=tledger-integration-tests-key-backup",
    ],
    jar_file = "/server_main_deploy.jar",
    jar_path = "//java/com/google/pes/server:server_main_deploy.jar",
    packer_ami_config = "//build_defs/aws:pes_ami.pkr.hcl",
    startup_script = "//build_defs/aws:setup_pes_enclave.sh",
    subnet_id = ":subnet_id_flag",
    uninstall_ssh_server = False,
    watcher_rpm = "//build_defs/aws:pes_watcher_rpm",
)
