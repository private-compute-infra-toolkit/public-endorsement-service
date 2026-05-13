# PCIT Container Tools

## Overview

**PCIT Container Tools** (Private Compute Infrastructure Toolkit) is a shared repository providing tooling, libraries, and build definitions for containerized workloads. It is primarily designed to support Trusted Execution Environments (TEEs), specifically focusing on building Amazon Machine Images (AMIs) and **AWS Nitro Enclaves**.

This repository serves as a foundational, reusable library consumed by other services.

## Core Capabilities

- **AMI and EIF Generation:** Provides the necessary infrastructure, Bazel rules, and templates for building Amazon Machine Images (AMIs) and Enclave Image Files (EIFs) for AWS.
- **Shared Dependencies:** Acts as a common dependency for other services, offering standardized Bazel build rules and core utilities used across the toolkit.

## Repository Structure

At a high level, the repository is organized into the following areas:

- `build_defs/`: Shared Bazel build definitions, macros, and rules.
- `cc/`: Common utilities that are included in the deployed AMIs.
- `operator/`: Build rules and HashiCorp Packer configurations for baking worker AMIs.

## Building and Testing

This project uses [Bazel](https://bazel.build/) as its primary build system.

To build the entire repository:

```bash
bazel build //...
```

To run all tests:

```bash
bazel test //...
```
