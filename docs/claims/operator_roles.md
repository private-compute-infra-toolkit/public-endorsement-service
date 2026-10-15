# Operator Roles

Link to self:
https://github.com/private-compute-infra-toolkit/public-endorsement-service/blob/main/docs/claims/operator_roles.md

This claim asserts that the endorsed artifact (such as an isolate manifest) is authorized to be
deployed and operated by one or more specific operator roles. It is an optional claim.

## Annotations

The claim requires the following key-value pair in the `annotations` map:

-   `allowed_operator_roles`: A space-separated list of the operator roles permitted to operate the
    endorsed artifact (e.g., `pa-frontend` or `pa-frontend pa-frontend-staging`). An operator role
    is the `<operator role>` segment of the SPIFFE ID in the certificate issued to the node that
    operates the artifact. A node matches if its operator role is exactly equal to one of the listed
    roles (case-sensitive; no wildcards or partial matches). If the same artifact is operated under
    several roles, for example when it is promoted across stages, the list must include all of them.

### Restrictions

PES imposes the following restrictions on the annotations:

-   `allowed_operator_roles`:
    -   Must contain at least one operator role.
    -   Must be no longer than 1024 characters.
    -   Operator roles must be separated by a single space, with no leading or trailing spaces.
    -   Each operator role must be no longer than 255 characters.
    -   Each operator role must only use characters from `[a-zA-Z0-9._-]`.

## Example

The following example shows how this claim is represented in an Oak predicate:

```json
{
    "type": "https://github.com/private-compute-infra-toolkit/public-endorsement-service/blob/main/docs/claims/operator_roles.md",
    "annotations": {
        "allowed_operator_roles": "pa-frontend pa-frontend-staging"
    }
}
```
