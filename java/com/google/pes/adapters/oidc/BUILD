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

package(default_visibility = ["//visibility:public"])

java_library(
    name = "oidc",
    srcs = [
        "AudienceHostname.java",
        "JwksUriProvider.java",
        "OidcAudienceValidator.java",
        "OidcDiscoveryFetcher.java",
        "OidcJwksKeyFetcher.java",
        "OidcJwksKeyLocator.java",
    ],
    runtime_deps = [
        "@maven//:com_google_flogger_flogger_system_backend",
    ],
    deps = [
        "//java/com/google/pes/domain",
        "//java/com/google/pes/domain/model",
        "//java/com/google/pes/domain/ports",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//java/util",
        "@io_grpc_grpc_java//api",
        "@io_grpc_grpc_java//stub",
        "@io_grpc_grpc_proto//:health_java_proto",
        "@maven//:com_beust_jcommander",
        "@maven//:com_fasterxml_jackson_core_jackson_databind",
        "@maven//:com_github_ben_manes_caffeine_caffeine",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:io_grpc_grpc_netty_shaded",
        "@maven//:io_grpc_grpc_services",
        "@maven//:io_jsonwebtoken_jjwt_api",
        "@maven//:io_jsonwebtoken_jjwt_impl",
        "@maven//:io_jsonwebtoken_jjwt_jackson",
        "@maven//:jakarta_inject_jakarta_inject_api",
        "@maven//:org_apache_httpcomponents_client5_httpclient5",
        "@pcit_mbs//java/com/google/tlog",
    ],
)
