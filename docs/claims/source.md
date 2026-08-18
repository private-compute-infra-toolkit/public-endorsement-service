# Source

Link to self:
https://github.com/private-compute-infra-toolkit/public-endorsement-service/blob/main/docs/claims/source.md

This claim asserts that the endorsed binary is associated with a specific source code repository and ref.
It is an optional claim, typically used to specify the source code repository in endorsements.
PES verifies that the claim is correctly formatted if present.

## Annotations

The claim requires the following key-value pairs in the `annotations` map:

- `github_url`: A string identifying the GitHub repository URL (e.g., `https://github.com/private-compute-infra-toolkit/encrypted-zone-ratified-isolates`).
- `ref`: A string identifying the Git ref, such as a branch, tag, or commit hash (e.g., `main` or `refs/tags/v1.0.0`).

### Restrictions

PES imposes the following restrictions on the annotations:

- `github_url`:
  - Must be no longer than 1024 characters.
  - Must be a valid URL starting with `https://github.com/`.
- `ref`:
  - Must be no longer than 255 characters.
  - Must only use characters from `[a-zA-Z0-9._/-]`.

## Example

In the Oak predicate, this claim is represented as:

```json
{
  "type": "https://github.com/private-compute-infra-toolkit/public-endorsement-service/blob/main/docs/claims/source.md",
  "annotations": {
    "github_url": "https://github.com/private-compute-infra-toolkit/encrypted-zone-ratified-isolates",
    "ref": "main"
  }
}
```
