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
    name = "statementvalidation",
    srcs = [
        "Claim.java",
        "InTotoStatement.java",
        "JsonPublisherIdProvider.java",
        "OakPredicate.java",
        "ResourceDescriptor.java",
    ],
    visibility = ["//visibility:public"],
    runtime_deps = [
        "@maven//:com_google_flogger_flogger_system_backend",
    ],
    deps = [
        "//java/com/google/pes/domain/ports",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_fasterxml_jackson_core_jackson_annotations",
        "@maven//:com_fasterxml_jackson_core_jackson_core",
        "@maven//:com_fasterxml_jackson_core_jackson_databind",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_flogger_flogger",
        "@maven//:jakarta_inject_jakarta_inject_api",
    ],
)
