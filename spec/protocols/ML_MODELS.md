# ML Model Sharing

This is a proposal to support ML model sharing in the OpenSharing Protocol.

## Motivation

We propose to add **RegisteredModel** and **ModelVersion** as new asset types in the [OpenSharing protocol](https://github.com/OpenSharing-IO/OpenSharing). A `RegisteredModel` is a named, versioned ML artifact. Each `ModelVersion` corresponds to a specific version of that model and has its own `storageLocation` containing the model artifacts. Recipients can list models and their versions, and generate temporary credentials to access a specific version's files directly via the cloud storage API.

## Protocol Changes

### RegisteredModel Object

A RegisteredModel object has the following fields:

| Name | Type | Description | Notes |
|---|---|---|---|
| name | String | The name of the model. | [required] |
| schema | String | The schema the model belongs to. | [required] |
| share | String | The share the model belongs to. | [required] |
| shareId | String | A unique, immutable identifier for the share across the sharing server. Recommended format: UUID. | [optional] |
| id | String | A unique, immutable identifier for the model within the share. Recommended format: UUID. | [optional] |
| comment | String | Description of the model. | [optional] |

### ModelVersion Object

A ModelVersion object has the following fields:

| Name | Type | Description | Notes |
|---|---|---|---|
| modelName | String | The name of the parent model. | [required] |
| schema | String | The schema the model belongs to. | [required] |
| share | String | The share the model belongs to. | [required] |
| version | Long | Integer version number. | [required] |
| status | String | One of: `PENDING_REGISTRATION`, `FAILED_REGISTRATION`, `READY`. Only `READY` versions support credential generation. | [required] |
| storageLocation | String | Cloud path for this version's artifact files (e.g., `s3://bucket/models/my-model/1/artifacts/`). | [required] |
| id | String | A unique, immutable identifier for this version. Recommended format: UUID. | [optional] |
| source | String | URI to source artifacts. Informational only. | [optional] |
| runId | String | The run ID that produced this version. Informational only. | [optional] |
| comment | String | Description of this version. | [optional] |

---

### List All Models in a Share

List all models in a share, across all schemas.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/all-models`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): The maximum number of results per page that should be returned. If the number of available results is larger than `maxResults`, the response will provide a `nextPageToken` that can be used to get the next page of results in subsequent list requests. The server may return fewer than `maxResults` items even if there are more available. The client should check `nextPageToken` in the response to determine if there are more available. Must be non-negative. 0 will return no results but `nextPageToken` may be populated.<br><br>**pageToken** (type: String, optional): Specifies a page token to use. Set `pageToken` to the `nextPageToken` returned by a previous list request to get the next page of results. `nextPageToken` will not be returned in a response if there are no more results available.

<details open>
<summary><b>200: The models were successfully returned.</b></summary>

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
      "comment": "string"
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

### List Models in a Schema

List models in a schema under a share.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/schemas/{schema}/models`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{schema}**: The schema name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): See above.<br><br>**pageToken** (type: String, optional): See above.

<details open>
<summary><b>200: The models were successfully returned.</b></summary>

Same response format as [List All Models in a Share](#list-all-models-in-a-share).

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

### Get Model

Get info for a specific model.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/schemas/{schema}/models/{model}`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{schema}**: The schema name to query. It's case-insensitive.<br><br>**{model}**: The model name to query. It's case-insensitive.

<details open>
<summary><b>200: The model was successfully returned.</b></summary>

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
  "comment": "string"
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

### List Model Versions

List versions of a model. Only versions with `status = READY` are returned.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/schemas/{schema}/models/{model}/versions`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{schema}**: The schema name to query. It's case-insensitive.<br><br>**{model}**: The model name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): See above.<br><br>**pageToken** (type: String, optional): See above.

<details open>
<summary><b>200: The model versions were successfully returned.</b></summary>

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
      "modelName": "string",
      "schema": "string",
      "share": "string",
      "version": 1,
      "status": "READY",
      "storageLocation": "string",
      "id": "string",
      "source": "string",
      "runId": "string",
      "comment": "string"
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

### Get Model Version

Get info for a specific model version.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/schemas/{schema}/models/{model}/versions/{version}`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{schema}**: The schema name to query. It's case-insensitive.<br><br>**{model}**: The model name to query. It's case-insensitive.<br><br>**{version}**: The version number to query.

<details open>
<summary><b>200: The model version was successfully returned.</b></summary>

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
  "modelName": "string",
  "schema": "string",
  "share": "string",
  "version": 1,
  "status": "READY",
  "storageLocation": "string",
  "id": "string",
  "source": "string",
  "runId": "string",
  "comment": "string"
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

### Generate Temporary Model Version Credentials

Generate temporary cloud credentials for accessing a model version's artifact files. The response provides short-lived credentials scoped to the version's `storageLocation`. Only versions with `status = READY` are eligible. Access is read-only.

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

`{prefix}/shares/{share}/schemas/{schema}/models/{model}/versions/{version}/temporary-model-version-credentials`
</td>
</tr>
<tr>
<td>URL Parameters</td>
<td>

**{share}**: The share name to query. It's case-insensitive.

**{schema}**: The schema name to query. It's case-insensitive.

**{model}**: The model name to query. It's case-insensitive.

**{version}**: The version number to query.
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
