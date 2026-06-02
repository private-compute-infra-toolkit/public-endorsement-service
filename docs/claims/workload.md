# Workload ID

Link to self:
https://github.com/private-compute-infra-toolkit/public-endorsement-service/blob/main/docs/claims/workload.md

This claim asserts that the endorsed binary belongs to a specific workload.
It is an optional claim, typically used for application-layer endorsements.
PES verifies that the claim is correctly formatted if present.

## Annotations

The claim requires the following key-value pair in the `annotations` map:

- `workload_id`: A string identifying the workload (e.g., `bar`).

### Restrictions

PES imposes the following restrictions on `workload_id`:

- `workload_id` must be no longer than 255 characters.
- `workload_id` must only use characters from `[a-zA-Z0-9.-_]`.

## Example

In the Oak predicate, this claim is represented as:

```json
{
  "type": "https://github.com/private-compute-infra-toolkit/public-endorsement-service/blob/main/docs/claims/workload.md",
  "annotations": {
    "workload_id": "bar"
  }
}
```
