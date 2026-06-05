# Glossary Sharing

> **Status: Community Proposal.** This specification is at an early stage and the design is not final. Key open questions include whether to break from Delta Sharing's `share/schema/asset` URL convention (this proposal does, for product-fit reasons), how account-level glossary content maps onto a share's namespace, and the portability of workspace-object references. We are publishing this to invite community input before finalizing. Please open an issue or discussion with feedback.

## Motivation

When data is shared across organizational boundaries, recipients receive the physical schema but not the business meaning behind it. A column named `amt` could be gross revenue, net revenue, or a transaction amount in any currency. A field named `status` has no inherent meaning without knowing what each value represents. A metric called `Revenue` may be calculated differently across organizations.

This missing context creates friction for anyone consuming shared data — analysts who need to interpret results correctly, and AI agents that need accurate business definitions to generate reliable outputs.

`GlossaryPage` is a new asset type for sharing business definitions alongside data. A glossary page is a named definition of a business entity, metric, dimension, or term, expressed as a markdown document. Glossary pages within a domain can reference each other and the data assets they describe, forming a graph that gives shared data its business context.

For example, a `Revenue` glossary page can define the formula, currency, grain, and known exclusions, and reference the UC tables it is computed from. A `Customer` page can define what the provider means by a customer, list synonyms (`Account`, `Org`), and reference related pages like `Order` or `Subscription`. Providers include glossary pages in a share to give recipients the context they need to use the shared data correctly.

> **Note:** This specification is a proposal for community feedback. We are sharing it to invite input on the design, not to announce a finalized protocol. See [Open Questions](#open-questions) for the areas where we are most actively seeking input.

---

## Protocol Changes

This proposal introduces a new asset type (`GlossaryPage`) and four new endpoints. Unlike the existing Delta Sharing asset types (tables, volumes, etc.), glossary pages are addressed by **domain** rather than **schema** — see [Open Questions](#open-questions) for the rationale.

Glossary content is catalog metadata, not storage-backed data, so the sharing server returns it directly. There is no credential-vending step. (Storage-backed extensions — e.g. attached PDFs — are noted under [Future Considerations](#future-considerations).)

### GlossaryPage Object

A glossary page object has the following fields:

| Name | Type | Description | Notes |
|---|---|---|---|
| name | String | The name of the glossary page. Unique within `(share, domain)`. | [required] |
| domain | String | The business domain the page belongs to (e.g. `finance`, `sales`, `marketing`). Replaces the `schema` segment used by other Delta Sharing asset types. | [required] |
| share | String | The share the page belongs to. | [required] |
| shareId | String | A unique, immutable identifier for the share across the sharing server. Recommended format: UUID. | [optional] |
| id | String | A unique, immutable identifier for the page within the share. Recommended format: UUID. | [optional] |
| description | String | A short, human- and LLM-readable description of what this page defines. Intended for discovery — recipients use this to understand what a page is before requesting full access. Providers are encouraged to generate this server-side from the page body so it stays in sync. | [optional] |
| synonyms | Array\<String\> | Alternate names for the concept, used by recipients for keyword expansion (e.g. `["MRR × 12", "annual subscription revenue"]` for `ARR`). | [optional] |

Note: object names (`name`, `domain`, `share`) must not exceed 255 characters and must not contain restricted characters. They are case-insensitive when used in URL paths.

Note: the `id` field is optional. If populated, its value should be unique within the share and stay immutable through the page's lifecycle. Page-to-page references (see [GlossaryPageReference](#glossarypagereference)) resolve by `id` when present.

Note: the `shareId` field is optional. If populated, its value should be unique across the sharing server and immutable through the share's lifecycle.

Note: the `description` field is optional and should not exceed 65536 characters. It is intentionally short — one or two sentences — so recipients can scan a domain's worth of pages cheaply. The full page content — markdown body, references, source assets — is accessible via the [Get Glossary Page Details](#get-glossary-page-details) endpoint.

Note: the `synonyms` field is optional and each synonym should not exceed 255 characters. Recipients are expected to apply synonym matching with the same case-folding rules they use for `name`.

---

### List All Glossary Pages in a Share

List all glossary pages in a share, across all domains.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/all-glossary-pages`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): The maximum number of results per page that should be returned. If the number of available results is larger than `maxResults`, the response will provide a `nextPageToken` that can be used to get the next page of results in subsequent list requests. The server may return fewer than `maxResults` items even if there are more available. The client should check `nextPageToken` in the response to determine if there are more available. Must be non-negative. 0 will return no results but `nextPageToken` may be populated.<br><br>**pageToken** (type: String, optional): Specifies a page token to use. Set `pageToken` to the `nextPageToken` returned by a previous list request to get the next page of results. `nextPageToken` will not be returned in a response if there are no more results available.

<details open>
<summary><b>200: The glossary pages were successfully returned.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json; charset=utf-8`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "items": [
    {
      "name": "string",
      "domain": "string",
      "share": "string",
      "shareId": "string",
      "id": "string",
      "description": "string",
      "synonyms": ["string"]
    }
  ],
  "nextPageToken": "string"
}
```

Note: the `items` field may be an empty array or missing when no results are found. The client must handle both cases.

Note: the `nextPageToken` field may be an empty string or missing when there are no additional results. The client must handle both cases.

</td>
</tr>
</table>
</details>

<details>
<summary><b>400: The request is malformed.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>401: The request is unauthenticated. The bearer token is missing or incorrect.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>403: The request is forbidden from being fulfilled.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>500: The request is not handled correctly due to a server error.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

Example:

`GET {prefix}/shares/finance_share/all-glossary-pages`

```json
{
  "items": [
    {
      "name": "ARR",
      "domain": "finance",
      "share": "finance_share",
      "shareId": "edacc4a7-6600-4fbb-85f3-a62a5ce6761f",
      "id": "9f1b3c2d-4e5a-6f7b-8c9d-0e1f2a3b4c5d",
      "description": "Annual Recurring Revenue — the 12-month forward value of all active subscription contracts, excluding one-time fees and refunds.",
      "synonyms": ["MRR × 12", "annual subscription revenue"]
    },
    {
      "name": "Customer",
      "domain": "finance",
      "share": "finance_share",
      "shareId": "edacc4a7-6600-4fbb-85f3-a62a5ce6761f",
      "id": "a2b3c4d5-e6f7-8901-bcde-f01234567890",
      "description": "An enterprise account that has signed a paid contract with the provider, excluding test and internal accounts.",
      "synonyms": ["Account", "Org"]
    },
    {
      "name": "Churn",
      "domain": "retention",
      "share": "finance_share",
      "shareId": "edacc4a7-6600-4fbb-85f3-a62a5ce6761f",
      "id": "c3d4e5f6-7890-1234-defa-bcdef0123456",
      "description": "Customers who terminate or fail to renew their contract within a measurement window; computed as a rolling 90-day rate.",
      "synonyms": ["attrition", "lost customers"]
    }
  ],
  "nextPageToken": ""
}
```

---

### List Glossary Pages in a Domain

List glossary pages in a domain under a share.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/domains/{domain}/glossary-pages`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{domain}**: The domain name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): See above.<br><br>**pageToken** (type: String, optional): See above.

<details open>
<summary><b>200: The glossary pages were successfully returned.</b></summary>

Same response format as [List All Glossary Pages in a Share](#list-all-glossary-pages-in-a-share).

</details>

<details>
<summary><b>400: The request is malformed.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>401: The request is unauthenticated. The bearer token is missing or incorrect.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>403: The request is forbidden from being fulfilled.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>404: The requested resource does not exist.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

Example:

`GET {prefix}/shares/finance_share/domains/finance/glossary-pages`

Returns only the pages in the `finance` domain (`ARR`, `Customer` from the previous example, but not `Churn` which lives in `retention`).

---

### Get Glossary Page

Get the metadata for a specific glossary page, including its short description and synonyms. To access the full page content — markdown body, references, source assets — use [Get Glossary Page Details](#get-glossary-page-details).

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/domains/{domain}/glossary-pages/{glossaryPage}`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{domain}**: The domain name to query. It's case-insensitive.<br><br>**{glossaryPage}**: The glossary page name to query. It's case-insensitive.

<details open>
<summary><b>200: The glossary page was successfully returned.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json; charset=utf-8`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "name": "string",
  "domain": "string",
  "share": "string",
  "shareId": "string",
  "id": "string",
  "description": "string",
  "synonyms": ["string"]
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>400: The request is malformed.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>401: The request is unauthenticated. The bearer token is missing or incorrect.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>403: The request is forbidden from being fulfilled.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>404: The requested resource does not exist.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

Example:

`GET {prefix}/shares/finance_share/domains/finance/glossary-pages/ARR`

```json
{
  "name": "ARR",
  "domain": "finance",
  "share": "finance_share",
  "shareId": "edacc4a7-6600-4fbb-85f3-a62a5ce6761f",
  "id": "9f1b3c2d-4e5a-6f7b-8c9d-0e1f2a3b4c5d",
  "description": "Annual Recurring Revenue — the 12-month forward value of all active subscription contracts, excluding one-time fees and refunds.",
  "synonyms": ["MRR × 12", "annual subscription revenue"]
}
```

---

### Get Glossary Page Details

Get the full content of a glossary page — markdown body, outbound references, and source-asset list. Returned directly by the sharing server using the same bearer token used for discovery endpoints.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/domains/{domain}/glossary-pages/{glossaryPage}/details`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{domain}**: The domain name to query. It's case-insensitive.<br><br>**{glossaryPage}**: The glossary page name to query. It's case-insensitive.

<details open>
<summary><b>200: The glossary page content was successfully returned.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json; charset=utf-8`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "name": "string",
  "id": "string",
  "description": "string",
  "synonyms": ["string"],
  "body": "string",
  "references": [{"asset": {}, "description": "string"}],
  "sourceAssets": [{"asset": {}, "description": "string"}]
}
```

See [GlossaryPageDetails](#glossarypagedetails).

</td>
</tr>
</table>
</details>

<details>
<summary><b>400: The request is malformed.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>401: The request is unauthenticated. The bearer token is missing or incorrect.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>403: The request is forbidden from being fulfilled.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>404: The requested resource does not exist.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>500: The request is not handled correctly due to a server error.</b></summary>

<table>
<tr>
<th>HTTP Response</th>
<th>Value</th>
</tr>
<tr>
<td>Header</td>
<td>

`Content-Type: application/json`

</td>
</tr>
<tr>
<td>Body</td>
<td>

```json
{
  "errorCode": "string",
  "message": "string"
}
```

</td>
</tr>
</table>
</details>

#### Worked Example

The full lifecycle for a recipient discovering and reading the `ARR` glossary page in the `finance_share`:

**Step 1.** Recipient lists pages in the `finance` domain to find what's available.

`GET {prefix}/shares/finance_share/domains/finance/glossary-pages`

Returns the discovery metadata for each page (name, domain, description, synonyms — no body, no references). Cheap enough to scan 100+ pages without fetching content for each. An LLM can pick out which pages are relevant from descriptions alone.

**Step 2.** Recipient fetches the details for the `ARR` page using the same bearer token.

`GET {prefix}/shares/finance_share/domains/finance/glossary-pages/ARR/details`<br>
`Authorization: Bearer {token}`

```json
{
  "name": "ARR",
  "id": "9f1b3c2d-4e5a-6f7b-8c9d-0e1f2a3b4c5d",
  "description": "Annual Recurring Revenue — the 12-month forward value of all active subscription contracts, excluding one-time fees and refunds.",
  "synonyms": ["MRR × 12", "annual subscription revenue"],
  "body": "# ARR\n\nAnnual Recurring Revenue.\n\n**Formula:** `SUM(subscriptions.mrr) * 12 WHERE subscriptions.state IN ('active', 'in_grace_period')`\n\n**Currency:** USD\n\n**Grain:** One row per active subscription contract\n\n**Excludes:**\n- One-time fees and professional services\n- Refunded subscriptions\n- Test accounts (`subscriptions.is_test = true`)\n- Contracts in `trial` or `cancelled` state\n\n**Recognition window:** Forward-looking 12 months from the measurement date.",
  "references": [
    {
      "asset": {
        "unityCatalog": {
          "metastoreId": "11111111-2222-3333-4444-555555555555",
          "fullName": "sales.contracts.subscriptions",
          "kind": "TABLE"
        }
      },
      "description": "Source table for active subscription contract state and MRR."
    },
    {
      "asset": {
        "glossaryPage": {
          "id": "a2b3c4d5-e6f7-8901-bcde-f01234567890"
        }
      },
      "description": "ARR is attributed to Customers via the `account_id` foreign key."
    },
    {
      "asset": {
        "externalUrl": "https://finance.example.com/policies/arr-calculation"
      },
      "description": "Finance team's authoritative ARR calculation policy document."
    }
  ],
  "sourceAssets": [
    {
      "asset": {
        "unityCatalog": {
          "metastoreId": "11111111-2222-3333-4444-555555555555",
          "fullName": "sales.contracts.subscriptions",
          "kind": "TABLE"
        }
      },
      "description": "Primary source for the ARR formula."
    },
    {
      "asset": {
        "unityCatalog": {
          "metastoreId": "11111111-2222-3333-4444-555555555555",
          "fullName": "sales.contracts.contract_amendments",
          "kind": "TABLE"
        }
      },
      "description": "Used for mid-period MRR adjustments before computing the forward 12-month value."
    }
  ]
}
```

The recipient now has the full business definition, the formula, the source tables, and the page-to-page reference to `Customer` — enough context for an analyst to interpret an ARR query result correctly, or for an AI agent to generate a correct SQL query.

---

## Object Models

### GlossaryPageDetails

The response returned by [Get Glossary Page Details](#get-glossary-page-details):

| Name | Type | Description | Notes |
|---|---|---|---|
| name | String | The name of the glossary page. | [required] |
| id | String | The immutable identifier of the page. | [optional] |
| description | String | The short description (same value as returned by the discovery endpoints). | [optional] |
| synonyms | Array\<String\> | Alternate names (same value as returned by the discovery endpoints). | [optional] |
| body | String | The full page content in markdown. Providers should include business rules, formulas, known exclusions, grain, and any other context an AI agent or analyst needs to correctly interpret and use this definition. | [required] |
| references | Array\<GlossaryPageReference\> | Outbound references this page makes to data assets or other glossary pages. | [optional] |
| sourceAssets | Array\<GlossaryPageReference\> | Provenance — assets this page was derived from. Uses the same shape as `references`. | [optional] |

Note: the `body` field should not exceed 1048576 characters (1 MiB). Media (images, videos) should be referenced via external URLs in the body markdown rather than inlined.

### GlossaryPageReference

| Name | Type | Description | Notes |
|---|---|---|---|
| asset | Asset | The referenced asset, expressed as a typed oneof. Exactly one variant must be set. | [required] |
| description | String | A plain-language description of why this reference exists or how the asset relates to the page. | [required] |

### Asset

A typed oneof. Exactly one of the four variants must be set in a given reference.

| Variant | Type | Description |
|---|---|---|
| unityCatalog | UnityCatalogAsset | A Unity Catalog securable (table, volume, function, model). |
| workspaceObject | WorkspaceObjectAsset | A workspace-scoped object (notebook, dashboard, query, data room). Vendor-specific to Databricks workspaces; recipients without a Databricks workspace may treat these as opaque references. See [Open Questions](#open-questions). |
| glossaryPage | GlossaryPageAsset | A reference to another glossary page, resolved by `id`. |
| externalUrl | String | An arbitrary external URL — typically a policy document, dashboard, or external reference. |

#### UnityCatalogAsset

| Name | Type | Description | Notes |
|---|---|---|---|
| metastoreId | String | The metastore ID of the catalog the asset lives in. | [required] |
| fullName | String | The three-level name `catalog.schema.table`. | [required] |
| kind | String | One of `TABLE`, `VOLUME`, `FUNCTION`, `MODEL`. | [required] |

#### WorkspaceObjectAsset

| Name | Type | Description | Notes |
|---|---|---|---|
| workspaceId | String | The workspace ID. | [required] |
| objectId | String | The workspace object ID. | [required] |
| kind | String | One of `NOTEBOOK`, `DASHBOARD`, `QUERY`, `DATA_ROOM`. | [required] |

#### GlossaryPageAsset

| Name | Type | Description | Notes |
|---|---|---|---|
| id | String | The `id` of the referenced glossary page. The reference is resolved within the same share. Cross-share references are not currently supported — see [Open Questions](#open-questions). | [required] |

---

## Future Considerations

**Blob attachments (PDFs, images, etc.).** Glossary content in v1 is text-only — the `body` is markdown and any non-text assets must be referenced via `externalUrl`. Future versions may want to support first-class blob attachments owned by the glossary page (e.g. a Finance Policy PDF that explains the ARR calculation in detail). The natural extension follows the established Delta Sharing pattern for storage-backed assets:

- Add a `storageLocation` field (or attachment list) to `GlossaryPageDetails` pointing at cloud-storage objects.
- Add a `POST .../temporary-storage-credentials` endpoint that vends short-lived cloud-storage credentials (S3 presigned URL, ADLS SAS token, etc.) for the attached blob.
- The recipient uses those credentials to fetch the blob directly from cloud storage, exactly as they do for table data files today.

This is intentionally out of scope for v1, since the page body itself doesn't need credential vending and adding blob support before there's a concrete use case would over-design the protocol. The pattern is called out here so that when the use case materializes, the extension shape is predictable.

---

## Open Questions

This specification is a community proposal. The following design questions are unresolved and we are actively seeking input:

**1. URL convention divergence from Delta Sharing**

Existing Delta Sharing asset types are addressed as `/shares/{share}/schemas/{schema}/<asset-type>/{name}`. This proposal uses `/shares/{share}/domains/{domain}/glossary-pages/{name}` because glossary pages are organized by **business domain** (e.g. `finance`, `sales`), not by database schema. "Schema" carries a database connotation that fits poorly for narrative business content.

The trade-off is a break from URL uniformity: SDK authors building generic Delta Sharing clients need to special-case the path segment for this asset type. We invite community feedback on whether the product-fit gain justifies the consistency cost, or whether `schema` should be retained for path uniformity (with `domain` as an additional property on the object).

**2. WorkspaceObjectAsset portability**

`WorkspaceObjectAsset` references a workspace-scoped object (notebook, dashboard, query, data room). These are Databricks-specific concepts and may not be meaningful to recipients on other platforms. We invite community feedback on whether this variant should:
- Stay as a first-class variant in the protocol, with non-Databricks recipients treating it as opaque metadata
- Be marked as a vendor extension (e.g. under an `extensions` field), keeping the core protocol platform-neutral
- Be excluded from the v1 protocol and proposed as a separate platform-specific extension

**3. Account-level vs metastore-level sharing**

The Delta Sharing share/schema model implicitly assumes metastore- or workspace-scoped data. Glossary pages may be account-scoped — defined once and applicable across all metastores in an account. How does an account-level glossary page map onto a share's domain namespace? Likely a share groups one or more account-level domains, but the protocol does not currently constrain how providers organize this. We invite community feedback on whether explicit guidance or constraints are needed.

**4. Synonym semantics**

Recipients are expected to use `synonyms` for keyword expansion at query time. The protocol does not currently specify case-folding rules, normalization, or deduplication. We invite community feedback on whether protocol-level guidance is needed, or whether this should be left to recipient implementations (matching the same flexibility recipients have today for matching table names).

**5. Cross-share page references**

Cross-share references — where a glossary page in one share references a page in a different share — are explicitly out of scope for this proposal. The `glossaryPage` asset variant resolves by `id` within the same share. We invite community feedback on whether and how to address cross-share references in a future version.

**6. Bidirectional references**

References are one-directional — declared by the source page. If `ARR` declares a reference to `Customer`, `Customer` does not automatically declare the inverse. Recipients building knowledge graphs over shared glossaries can derive the inverse client-side. We invite feedback on whether the protocol should explicitly call out bidirectionality conventions, or leave them entirely to recipient interpretation.
