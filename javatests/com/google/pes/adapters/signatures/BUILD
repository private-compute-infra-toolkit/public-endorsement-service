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
    name = "SignatureVerifierImplTest",
    srcs = ["SignatureVerifierImplTest.java"],
    deps = [
        "//java/com/google/pes/adapters/signatures",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_bouncycastle_bcpkix_jdk15to18",
        "@maven//:org_bouncycastle_bcprov_jdk15to18",
    ],
)

java_test(
    name = "SignerImplTest",
    srcs = ["SignerImplTest.java"],
    deps = [
        "//java/com/google/pes/adapters/signatures",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_bouncycastle_bcpkix_jdk15to18",
        "@maven//:org_bouncycastle_bcprov_jdk15to18",
        "@maven//:org_mockito_mockito_core",
        "@pcit_mbs//java/com/google/mbs:certmanagement",
    ],
)

java_test(
    name = "PublicKeyParserTest",
    srcs = ["PublicKeyParserTest.java"],
    deps = [
        "//java/com/google/pes/adapters/signatures",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
        "@pcit_mbs//java/com/google/mbs:certmanagement",
    ],
)
