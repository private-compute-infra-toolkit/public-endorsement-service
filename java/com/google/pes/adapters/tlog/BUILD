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

load("@rules_java//java:defs.bzl", "java_library", "java_proto_library")

java_proto_library(
    name = "entry_java_proto",
    visibility = ["//visibility:public"],
    deps = ["@pcit_api//google/tledger/v1:entry_proto"],
)

java_library(
    name = "tlog",
    srcs = [
        "TLedger.java",
        "TLedgerCertBucketName.java",
        "TLedgerCertName.java",
        "TLedgerCertificatFetcher.java",
    ],
    visibility = ["//visibility:public"],
    runtime_deps = [
        "@maven//:com_google_flogger_flogger_system_backend",
    ],
    deps = [
        ":entry_java_proto",
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/adapters/signatures",
        "//java/com/google/pes/annotations",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "//java/com/google/pes/proto:endorsement_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:jakarta_inject_jakarta_inject_api",
        "@maven//:org_apache_httpcomponents_client5_httpclient5",
        "@maven//:org_apache_httpcomponents_core5_httpcore5",
        "@maven//:software_amazon_awssdk_s3",
        "@maven//:software_amazon_awssdk_sdk_core",
    ],
)
