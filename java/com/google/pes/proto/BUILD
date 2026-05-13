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

load("@com_google_protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@io_grpc_grpc_java//:java_grpc_library.bzl", "java_grpc_library")

package(default_visibility = ["//visibility:public"])

java_proto_library(
    name = "verification_material_java_proto",
    deps = ["@pcit_api//google/pes/v1:verification_material_proto"],
)

java_proto_library(
    name = "endorsement_java_proto",
    deps = ["@pcit_api//google/pes/v1:endorsement_proto"],
)

java_proto_library(
    name = "public_endorsement_service_java_proto",
    deps = ["@pcit_api//google/pes/v1:public_endorsement_service_proto"],
)

java_grpc_library(
    name = "public_endorsement_service_java_grpc",
    srcs = [
        "@pcit_api//google/pes/v1:public_endorsement_service_proto",
    ],
    deps = [
        ":public_endorsement_service_java_proto",
    ],
)

java_proto_library(
    name = "publisher_policy_java_proto",
    deps = ["@pcit_api//google/pes/policy/v1:publisher_policy_proto"],
)
