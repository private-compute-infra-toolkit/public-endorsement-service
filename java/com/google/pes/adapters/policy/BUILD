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

java_library(
    name = "policy",
    srcs = [
        "S3PolicyProvider.java",
    ],
    visibility = ["//visibility:public"],
    runtime_deps = [
        "@maven//:com_google_flogger_flogger_system_backend",
    ],
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/annotations",
        "//java/com/google/pes/domain",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "//java/com/google/pes/proto:publisher_policy_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_flogger_flogger",
        "@maven//:jakarta_inject_jakarta_inject_api",
        "@maven//:software_amazon_awssdk_auth",
        "@maven//:software_amazon_awssdk_s3",
        "@maven//:software_amazon_awssdk_sdk_core",
    ],
)
