# Metrics Cardinality Safety Rules

All metrics configurations in this directory (`prometheus.yaml` and `amazon-cloudwatch-agent.json`)
must strictly adhere to defensive cardinality limits.

## Core Mandates

1.  **Strict Whitelisting (Defensive Filtering via EMF)**:

    - `amazon-cloudwatch-agent.json` **must** provide native cardinality protection using explicit `dimensions` and strict `metric_selectors`.
    - `prometheus.yaml` must remain clean and free of `metric_relabel_configs` to prevent upstream Telegraf metadata loss.

2.  **Dimension Alignment**:

    - Only low-cardinality labels are allowed in CloudWatch EMF `dimensions` (`method`, `status`).
    - **Strictly Forbidden Dimensions**: `instance` (IPs/hostnames), `pod_name`, or any other dynamic identifiers.

3.  **No Metric Bloat**:

    - Only keep essential metrics required for SLAs/SLIs.
    - Currently allowed metrics:
      - `pes_server_total_duration_seconds_(count|sum)` (Request duration)
      - `pes_server_requests_total` (Request count)
      - `pes_authenticationStatus_total` (Authentication status count)
      - `pes_endorsementGeneration_total` (Endorsement generation count per publisher, max cardinality: 10)
    - Do not add new metrics without verifying their cardinality impact.

4.  **Verification**:
    - If any new metric or label is proposed, you **must** calculate and document the maximum
      theoretical cardinality (total unique time series) to prove it is safe before applying
      changes.

## Cardinality Calculations for Proposed Metrics

- `pes_endorsementGeneration_total`:
  - Dimensions: `publisher_id`
  - Max values for `publisher_id`: 10 (enforced by `SystemMetrics.MAX_APPROVED_PUBLISHERS` in code).
  - Max theoretical cardinality: 10 unique time series.
  - Status: Safe.
