# Artifact

Link to self:
https://github.com/private-compute-infra-toolkit/public-endorsement-service/blob/main/docs/claims/artifact.md

This claim asserts that the endorsed binary is associated with a specific artifact filename.
It is an optional claim.

## Annotations

The claim requires the following key-value pair in the `annotations` map:

- `filename`: A string identifying the path to a file (e.g., `crypto_oracle/isolate_rootfs.tar`).

### Restrictions

PES imposes the following restrictions on the annotations:

- `filename`:
  - Must be no longer than 1024 characters.
  - Must only use characters from `[a-zA-Z0-9._/-]`.

## Example

In the Oak predicate, this claim is represented as:

```json
{
  "type": "https://github.com/private-compute-infra-toolkit/public-endorsement-service/blob/main/docs/claims/artifact.md",
  "annotations": {
    "filename": "crypto_oracle/isolate_rootfs.tar"
  }
}
```
