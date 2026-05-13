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
    name = "PesModuleTest",
    size = "small",
    srcs = ["PesModuleTest.java"],
    test_class = "com.google.pes.server.PesModuleTest",
    deps = [
        "//java/com/google/pes/adapters/oidc",
        "//java/com/google/pes/adapters/policy",
        "//java/com/google/pes/adapters/signatures",
        "//java/com/google/pes/adapters/statementvalidation",
        "//java/com/google/pes/adapters/tlog",
        "//java/com/google/pes/annotations",
        "//java/com/google/pes/domain",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "//java/com/google/pes/server:pes_cmd_args",
        "//java/com/google/pes/server:server_main",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@maven//:com_beust_jcommander",
        "@maven//:com_fasterxml_jackson_core_jackson_databind",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_apache_httpcomponents_client5_httpclient5",
        "@maven//:org_mockito_mockito_core",
        "@maven//:software_amazon_awssdk_s3",
        "@pcit_mbs//java/com/google/mbs:certmanagement",
        "@pcit_mbs//java/com/google/mbs:dummy_cert_provider",
        "@pcit_mbs//java/com/google/mbs/attestationcollection",
        "@pcit_mbs//java/com/google/mbs/attestationcollection/aws",
    ],
)

java_test(
    name = "AwsResourceNamesProviderTest",
    size = "small",
    srcs = ["AwsResourceNamesProviderTest.java"],
    test_class = "com.google.pes.server.AwsResourceNamesProviderTest",
    deps = [
        "//java/com/google/pes/server:pes_cmd_args",
        "//java/com/google/pes/server:server_main",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "PesGrpcHandlerTest",
    size = "small",
    srcs = ["PesGrpcHandlerTest.java"],
    test_class = "com.google.pes.server.PesGrpcHandlerTest",
    deps = [
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/domain",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "//java/com/google/pes/proto:endorsement_java_proto",
        "//java/com/google/pes/proto:public_endorsement_service_java_grpc",
        "//java/com/google/pes/proto:public_endorsement_service_java_proto",
        "//java/com/google/pes/proto:verification_material_java_proto",
        "//java/com/google/pes/server:server_main",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@io_grpc_grpc_java//api",
        "@io_grpc_grpc_java//inprocess",
        "@io_grpc_grpc_java//stub",
        "@io_grpc_grpc_java//testing",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:io_jsonwebtoken_jjwt_api",
        "@maven//:io_jsonwebtoken_jjwt_impl",
        "@maven//:io_jsonwebtoken_jjwt_jackson",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
        "@pcit_api//google/pes/v1:endorsement_proto",
        "@pcit_api//google/pes/v1:public_endorsement_service_proto",
    ],
)
