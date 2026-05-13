# Copyright 2025 Google LLC
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

load("@rules_java//java:defs.bzl", "java_binary", "java_library")

java_library(
    name = "pes_cmd_args",
    srcs = [
        "PesArgs.java",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com_beust_jcommander",
    ],
)

java_binary(
    name = "server_main",
    srcs = [
        "AwsInstanceMetadata.java",
        "AwsResourceNames.java",
        "AwsResourceNamesProvider.java",
        "ImdsClient.java",
        "JwtInterceptor.java",
        "PesGrpcHandler.java",
        "PesModule.java",
        "ServerMain.java",
    ],
    main_class = "com.google.pes.server.ServerMain",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "@maven//:com_google_flogger_flogger_system_backend",
    ],
    deps = [
        ":pes_cmd_args",
        "//java/com/google/pes/adapters",
        "//java/com/google/pes/adapters/oidc",
        "//java/com/google/pes/adapters/policy",
        "//java/com/google/pes/adapters/signatures",
        "//java/com/google/pes/adapters/statementvalidation",
        "//java/com/google/pes/adapters/tlog",
        "//java/com/google/pes/annotations",
        "//java/com/google/pes/domain",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "//java/com/google/pes/proto:endorsement_java_proto",
        "//java/com/google/pes/proto:public_endorsement_service_java_grpc",
        "//java/com/google/pes/proto:public_endorsement_service_java_proto",
        "@io_grpc_grpc_java//api",
        "@io_grpc_grpc_java//stub",
        "@io_grpc_grpc_proto//:health_java_proto",
        "@maven//:com_beust_jcommander",
        "@maven//:com_fasterxml_jackson_core_jackson_databind",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_inject_guice",
        "@maven//:com_linecorp_armeria_armeria",
        "@maven//:com_linecorp_armeria_armeria_grpc",
        "@maven//:io_grpc_grpc_netty_shaded",
        "@maven//:io_grpc_grpc_services",
        "@maven//:io_jsonwebtoken_jjwt_api",
        "@maven//:io_jsonwebtoken_jjwt_impl",
        "@maven//:io_jsonwebtoken_jjwt_jackson",
        "@maven//:jakarta_inject_jakarta_inject_api",
        "@maven//:org_apache_httpcomponents_client5_httpclient5",
        "@maven//:org_bouncycastle_bcpkix_jdk15to18",
        "@maven//:org_bouncycastle_bcprov_jdk15to18",
        "@maven//:software_amazon_awssdk_auth",
        "@maven//:software_amazon_awssdk_imds",
        "@maven//:software_amazon_awssdk_regions",
        "@maven//:software_amazon_awssdk_s3",
        "@maven//:software_amazon_awssdk_sdk_core",
        "@pcit_api//google/pes/v1:endorsement_proto",
        "@pcit_api//google/pes/v1:public_endorsement_service_proto",
        "@pcit_mbs//java/com/google/kmsclient",
        "@pcit_mbs//java/com/google/kmsclient/aws",
        "@pcit_mbs//java/com/google/mbs:certmanagement",
        "@pcit_mbs//java/com/google/mbs:dummy_cert_provider",
        "@pcit_mbs//java/com/google/mbs/attestationcollection",
        "@pcit_mbs//java/com/google/mbs/attestationcollection/aws",
        "@pcit_mbs//java/com/google/platform/aws/nsm",
        "@pcit_mbs//java/com/google/tlog",
    ],
)
