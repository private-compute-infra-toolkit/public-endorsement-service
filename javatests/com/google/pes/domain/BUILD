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
    name = "PublisherVerifierTest",
    size = "small",
    srcs = ["PublisherVerifierTest.java"],
    test_class = "com.google.pes.domain.PublisherVerifierTest",
    deps = [
        "//java/com/google/pes/domain",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "PreAuthenticationEncodingTest",
    size = "small",
    srcs = ["PreAuthenticationEncodingTest.java"],
    test_class = "com.google.pes.domain.PreAuthenticationEncodingTest",
    deps = [
        "//java/com/google/pes/domain",
        "//java/com/google/pes/domain/model",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "PublicEndorsementServiceTest",
    size = "small",
    srcs = ["PublicEndorsementServiceTest.java"],
    test_class = "com.google.pes.domain.PublicEndorsementServiceTest",
    deps = [
        "//java/com/google/pes/domain",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:jakarta_inject_jakarta_inject_api",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
        "@pcit_mbs//java/com/google/mbs:certmanagement",
    ],
)
