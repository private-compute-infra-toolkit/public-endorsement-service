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

load("@rules_java//java:defs.bzl", "java_test")

java_test(
    name = "S3PolicyProviderTest",
    size = "small",
    srcs = ["S3PolicyProviderTest.java"],
    test_class = "com.google.pes.adapters.policy.S3PolicyProviderTest",
    deps = [
        "//java/com/google/pes/adapters/policy",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "//java/com/google/pes/proto:publisher_policy_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
        "@maven//:software_amazon_awssdk_auth",
        "@maven//:software_amazon_awssdk_http_client_spi",
        "@maven//:software_amazon_awssdk_s3",
        "@maven//:software_amazon_awssdk_sdk_core",
    ],
)
