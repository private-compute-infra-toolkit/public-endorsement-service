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
    name = "TLedgerTest",
    size = "small",
    srcs = ["TLedgerTest.java"],
    test_class = "com.google.pes.adapters.tlog.TLedgerTest",
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/adapters/tlog",
        "//java/com/google/pes/adapters/tlog:entry_java_proto",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "//java/com/google/pes/proto:endorsement_java_proto",
        "//java/com/google/pes/proto:public_endorsement_service_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_apache_httpcomponents_client5_httpclient5",
        "@maven//:org_apache_httpcomponents_core5_httpcore5",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "TLedgerCertificatFetcherTest",
    size = "small",
    srcs = ["TLedgerCertificatFetcherTest.java"],
    test_class = "com.google.pes.adapters.tlog.TLedgerCertificatFetcherTest",
    deps = [
        "//java/com/google/pes/adapters/tlog",
        "//java/com/google/pes/domain/ports",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
        "@maven//:software_amazon_awssdk_s3",
        "@maven//:software_amazon_awssdk_sdk_core",
    ],
)
