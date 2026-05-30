# Agent Skills Sharing

This is a proposal to support agent skill sharing in the OpenSharing Protocol.

## Motivation

We propose to add **AgentSkill** as a new asset type in the [OpenSharing protocol](https://github.com/OpenSharing-IO/OpenSharing), following the [AgentSkills specification](https://agentskills.io/specification).

An AgentSkill is a directory of files that an AI agent can load and execute to perform a specific task — for example, processing PDFs, running code reviews, or interacting with external APIs. Sharing skills across organizational boundaries enables agents to leverage capabilities developed by other providers without duplicating effort.

An AgentSkill follows the [AgentSkills directory structure](https://agentskills.io/specification):

```
skill-name/
├── SKILL.md       # Required: metadata + instructions
├── scripts/       # Optional: executable code
├── references/    # Optional: documentation
└── assets/        # Optional: templates, resources
```

An AgentSkill is a first-class asset in OpenSharing with its own `storageLocation`. Access to the skill's files is granted via a dedicated `GenerateTemporarySkillCredentials` endpoint scoped to that storage location. This design keeps each skill self-contained and isolates access per skill.

## Protocol Changes

### AgentSkill Object

An AgentSkill object has the following fields:

| Name | Type | Description | Notes |
|---|---|---|---|
| name | String | The name of the skill. Lowercase letters, numbers, and hyphens only. Max 64 chars. | [required] |
| schema | String | The schema the skill belongs to. | [required] |
| share | String | The share the skill belongs to. | [required] |
| shareId | String | A unique, immutable identifier for the share across the sharing server. Recommended format: UUID. | [optional] |
| id | String | A unique, immutable identifier for the skill within the share. Recommended format: UUID. | [optional] |
| description | String | What the skill does and when to use it. Max 1024 chars. | [required] |
| storageLocation | String | The root storage location of the skill directory (e.g., `s3://bucket/path/to/skill-name/`). | [required] |
| license | String | License name or reference to a bundled license file within the skill. | [optional] |
| compatibility | String | Environment requirements (e.g., required tools, OS, network access). Max 500 chars. | [optional] |
| allowedTools | String | Space-separated list of pre-approved tools the skill may invoke. | [optional] |
| metadata | Map<String, String> | Arbitrary key-value map for additional properties. | [optional] |

Note: the `name` field must match the directory name at the root of `storageLocation`. It must not start or end with a hyphen and must not contain consecutive hyphens.

### List All Skills in a Share

List all skills in a share, across all schemas.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/all-skills`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): The maximum number of results per page that should be returned. If the number of available results is larger than `maxResults`, the response will provide a `nextPageToken` that can be used to get the next page of results in subsequent list requests. The server may return fewer than `maxResults` items even if there are more available. The client should check `nextPageToken` in the response to determine if there are more available. Must be non-negative. 0 will return no results but `nextPageToken` may be populated.<br><br>**pageToken** (type: String, optional): Specifies a page token to use. Set `pageToken` to the `nextPageToken` returned by a previous list request to get the next page of results. `nextPageToken` will not be returned in a response if there are no more results available.

<details open>
<summary><b>200: The skills were successfully returned.</b></summary>

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
      "schema": "string",
      "share": "string",
      "shareId": "string",
      "id": "string",
      "description": "string",
      "storageLocation": "string",
      "license": "string",
      "compatibility": "string",
      "allowedTools": "string",
      "metadata": {
        "key": "value"
      }
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
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>

`Content-Type: application/json`

</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>401: The request is unauthenticated.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>

`Content-Type: application/json`

</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>403: The request is forbidden from being fulfilled.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>

`Content-Type: application/json`

</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>404: The requested resource does not exist.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>

`Content-Type: application/json`

</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

---

### List Skills in a Schema

List skills in a schema under a share.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/schemas/{schema}/skills`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{schema}**: The schema name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): See above.<br><br>**pageToken** (type: String, optional): See above.

<details open>
<summary><b>200: The skills were successfully returned.</b></summary>

Same response format as [List All Skills in a Share](#list-all-skills-in-a-share).

</details>

<details>
<summary><b>400: The request is malformed.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>401: The request is unauthenticated.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>403: The request is forbidden from being fulfilled.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>404: The requested resource does not exist.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

---

### Get Skill

Get info for a specific skill.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/schemas/{schema}/skills/{skill}`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{schema}**: The schema name to query. It's case-insensitive.<br><br>**{skill}**: The skill name to query. It's case-insensitive.

<details open>
<summary><b>200: The skill was successfully returned.</b></summary>

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
  "schema": "string",
  "share": "string",
  "shareId": "string",
  "id": "string",
  "description": "string",
  "storageLocation": "string",
  "license": "string",
  "compatibility": "string",
  "allowedTools": "string",
  "metadata": {
    "key": "value"
  }
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>400: The request is malformed.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>401: The request is unauthenticated.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>403: The request is forbidden from being fulfilled.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>404: The requested resource does not exist.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

---

### Generate Temporary Skill Credentials

Generate temporary cloud credentials for accessing a skill's storage location. The response provides short-lived credentials scoped to the skill's `storageLocation`, which the recipient uses to access the skill's files directly via the cloud storage API.

<table>
<tr>
<th>HTTP Request</th>
<th>Value</th>
</tr>
<tr>
<td>Method</td>
<td>

`POST`
</td>
</tr>
<tr>
<td>Headers</td>
<td>

`Authorization: Bearer {token}`

Optional: `Content-Type: application/json; charset=utf-8`
</td>
</tr>
<tr>
<td>URL</td>
<td>

`{prefix}/shares/{share}/schemas/{schema}/skills/{skill}/temporary-skill-credentials`
</td>
</tr>
<tr>
<td>URL Parameters</td>
<td>

**{share}**: The share name to query. It's case-insensitive.

**{schema}**: The schema name to query. It's case-insensitive.

**{skill}**: The skill name to query. It's case-insensitive.
</td>
</tr>
</table>

<details open>
<summary><b>200: The temporary credentials were successfully returned.</b></summary>

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

Only one of `awsTempCredentials`, `azureUserDelegationSas`, `gcpOauthToken`, or `r2Credentials` should be defined.

```json
{
  "awsTempCredentials": {
    "accessKeyId": "string",
    "secretAccessKey": "string",
    "sessionToken": "string"
  },
  "azureUserDelegationSas": {
    "sasToken": "string"
  },
  "gcpOauthToken": {
    "oauthToken": "string"
  },
  "r2Credentials": {
    "accessKeyId": "string",
    "secretAccessKey": "string",
    "sessionToken": "string"
  },
  "expirationTime": 123456789
}
```

</td>
</tr>
</table>
</details>

<details>
<summary><b>400: The request is malformed.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>401: The request is unauthenticated.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>403: The request is forbidden from being fulfilled.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

<details>
<summary><b>404: The requested resource does not exist.</b></summary>

<table>
<tr><th>HTTP Response</th><th>Value</th></tr>
<tr><td>Header</td><td>`Content-Type: application/json`</td></tr>
<tr><td>Body</td><td>

```json
{ "errorCode": "string", "message": "string" }
```

</td></tr>
</table>
</details>

---

## Credential Models

The credential models (`TemporaryCredentials`, `AwsCredentials`, `AzureUserDelegationSAS`, `GcpOauthToken`, `R2Credentials`) are defined in [CREDENTIALS.md](CREDENTIALS.md) and shared across all asset types.
