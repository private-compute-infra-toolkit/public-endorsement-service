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
    name = "SignatureMapperTest",
    size = "small",
    srcs = ["SignatureMapperTest.java"],
    test_class = "com.google.pes.adapters.SignatureMapperTest",
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/proto:endorsement_java_proto",
        "//java/com/google/pes/proto:public_endorsement_service_java_proto",
        "//java/com/google/pes/proto:verification_material_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "StatementSignatureMapperTest",
    size = "small",
    srcs = ["StatementSignatureMapperTest.java"],
    test_class = "com.google.pes.adapters.StatementSignatureMapperTest",
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/proto:endorsement_java_proto",
        "//java/com/google/pes/proto:public_endorsement_service_java_proto",
        "//java/com/google/pes/proto:verification_material_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "StatementMapperTest",
    size = "small",
    srcs = ["StatementMapperTest.java"],
    test_class = "com.google.pes.adapters.StatementMapperTest",
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/proto:endorsement_java_proto",
        "//java/com/google/pes/proto:public_endorsement_service_java_proto",
        "//java/com/google/pes/proto:verification_material_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "TLogReceiptMapperTest",
    size = "small",
    srcs = ["TLogReceiptMapperTest.java"],
    test_class = "com.google.pes.adapters.TLogReceiptMapperTest",
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/proto:endorsement_java_proto",
        "//java/com/google/pes/proto:public_endorsement_service_java_proto",
        "//java/com/google/pes/proto:verification_material_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "VerificationMaterialMapperTest",
    size = "small",
    srcs = ["VerificationMaterialMapperTest.java"],
    test_class = "com.google.pes.adapters.VerificationMaterialMapperTest",
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/proto:verification_material_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "EndorsementMapperTest",
    size = "small",
    srcs = ["EndorsementMapperTest.java"],
    test_class = "com.google.pes.adapters.EndorsementMapperTest",
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/proto:endorsement_java_proto",
        "//java/com/google/pes/proto:public_endorsement_service_java_proto",
        "//java/com/google/pes/proto:verification_material_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "PublisherPolicyMapperTest",
    size = "small",
    srcs = ["PublisherPolicyMapperTest.java"],
    test_class = "com.google.pes.adapters.PublisherPolicyMapperTest",
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/proto:publisher_policy_java_proto",
        "//java/com/google/pes/proto:verification_material_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "SystemMetricsTest",
    size = "small",
    srcs = ["SystemMetricsTest.java"],
    test_class = "com.google.pes.adapters.SystemMetricsTest",
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/domain/metric",
        "@maven//:com_google_truth_truth",
        "@maven//:io_micrometer_micrometer_core",
        "@maven//:io_micrometer_micrometer_registry_prometheus",
        "@maven//:junit_junit",
        "@pcit_mbs//java/com/google/mbs/domain",
    ],
)

java_test(
    name = "OidcAudienceValidatorTest",
    size = "small",
    srcs = ["oidc/OidcAudienceValidatorTest.java"],
    test_class = "com.google.pes.adapters.oidc.OidcAudienceValidatorTest",
    deps = [
        "//java/com/google/pes/adapters/oidc",
        "//java/com/google/pes/server:aws_metadata",
        "@maven//:com_google_truth_truth",
        "@maven//:io_jsonwebtoken_jjwt_api",
        "@maven//:junit_junit",
        "@maven//:org_bouncycastle_bcprov_jdk18on",
        "@maven//:org_mockito_mockito_core",
        "@pcit_mbs//java/com/google/mbs:certmanagement",
        "@pcit_mbs//java/com/google/mbs/domain",
    ],
)
