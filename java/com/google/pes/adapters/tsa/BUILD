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

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = [
    "//java/com/google/pes/adapters/tsa:__subpackages__",
    "//java/com/google/pes/server:__subpackages__",
    "//javatests/com/google/pes/adapters/tsa:__subpackages__",
    "//javatests/com/google/pes/server:__subpackages__",
])

java_library(
    name = "tsa",
    srcs = [
        "RandomTsaUrlSelector.java",
        "S3TsaConfigProvider.java",
        "SimpleTsaClient.java",
        "TsaConfigBucketName.java",
        "TsaTrustAnchors.java",
        "TsaUrlSelector.java",
    ],
    resources = [
        "//java/com/google/pes/resources:tsa_root_certs.pem",
    ],
    runtime_deps = [
        "@maven//:com_google_flogger_flogger_system_backend",
    ],
    deps = [
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_fasterxml_jackson_core_jackson_annotations",
        "@maven//:com_fasterxml_jackson_core_jackson_databind",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:jakarta_inject_jakarta_inject_api",
        "@maven//:org_bouncycastle_bcpkix_jdk18on",
        "@maven//:org_bouncycastle_bcprov_jdk18on",
        "@maven//:org_bouncycastle_bcutil_jdk18on",
        "@maven//:software_amazon_awssdk_s3",
        "@maven//:software_amazon_awssdk_sdk_core",
    ],
)
