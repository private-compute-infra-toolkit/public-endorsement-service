# Publisher ID

Link to self:
https://github.com/private-compute-infra-toolkit/public-endorsement-service/blob/main/docs/claims/publisher.md

This claim asserts that the endorsed statement is associated with a specific publisher.
It is a required claim for all PES endorsements. PES uses this claim to verify that the
publisher has onboarded their public key to the system and that the signature matches the public key.

## Annotations

The claim requires the following key-value pair in the `annotations` map:

- `publisher_id`: A string identifying the publisher (e.g., `release@google.com`).

### Restrictions

PES imposes the following restrictions on `publisher_id`:

- `publisher_id` must be in the format `<publisher_role>@<publisher_domain>`.
- `<publisher_role>` and `<publisher_domain>` each must be no longer than 255 characters.
- `<publisher_domain>` must be a valid domain and must only use characters from `[a-z0-9.-_]`.
- `<publisher_role>` must only use characters from `[a-zA-Z0-9.-_]`.

## Example

In the Oak predicate, this claim is represented as:

```json
{
  "type": "https://github.com/private-compute-infra-toolkit/public-endorsement-service/blob/main/docs/claims/publisher.md",
  "annotations": {
    "publisher_id": "release@google.com"
  }
}
```
