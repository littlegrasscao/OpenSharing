# Volume Sharing

This is a proposal to support volume sharing in the OpenSharing Protocol.

## Motivation

We propose to add **Volume** as a new asset type in the [OpenSharing protocol](https://github.com/OpenSharing-IO/OpenSharing) to enable sharing of directory-based storage locations with recipients. Volume sharing allows providers to share unstructured data in the most general way — the protocol is agnostic to the contents of the volume, placing no constraints on the format or structure of the files within it. This allows providers to share access to arbitrary files (e.g., ML model artifacts, agent skills, raw data files, images) without requiring them to be structured as Delta tables.

The credential model follows the same pattern as [directory-based table access](https://github.com/delta-io/delta-sharing/issues/782): the server issues temporary cloud credentials (STS tokens, SAS tokens, or GCP OAuth tokens) scoped to the volume's storage location, and the recipient uses those credentials to access files directly via the cloud storage API.

## Protocol Changes

The following new endpoints are introduced to support volume sharing.

### Volume Object

A volume object has the following fields:

| Name | Type | Description | Notes |
|---|---|---|---|
| name | String | The name of the volume. | [required] |
| schema | String | The schema the volume belongs to. | [required] |
| share | String | The share the volume belongs to. | [required] |
| shareId | String | A unique, immutable identifier for the share across the sharing server. Recommended format: UUID. | [optional] |
| id | String | A unique, immutable identifier for the volume within the share. Recommended format: UUID. | [optional] |
| storageLocation | String | The root storage location of the volume (e.g., `s3://bucket/path/`). | [required] |

Note: object names (`name`, `schema`, `share`) must not exceed 255 characters and must not contain restricted characters.

### List All Volumes in a Share

List all volumes in a share, across all schemas.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/all-volumes`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): The maximum number of results per page that should be returned. If the number of available results is larger than `maxResults`, the response will provide a `nextPageToken` that can be used to get the next page of results in subsequent list requests. The server may return fewer than `maxResults` items even if there are more available. The client should check `nextPageToken` in the response to determine if there are more available. Must be non-negative. 0 will return no results but `nextPageToken` may be populated.<br><br>**pageToken** (type: String, optional): Specifies a page token to use. Set `pageToken` to the `nextPageToken` returned by a previous list request to get the next page of results. `nextPageToken` will not be returned in a response if there are no more results available.

<details open>
<summary><b>200: The volumes were successfully returned.</b></summary>

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
      "storageLocation": "string"
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
<summary><b>401: The request is unauthenticated.</b></summary>

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

---

### List Volumes in a Schema

List volumes in a schema under a share.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/schemas/{schema}/volumes`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{schema}**: The schema name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): The maximum number of results per page that should be returned. If the number of available results is larger than `maxResults`, the response will provide a `nextPageToken` that can be used to get the next page of results in subsequent list requests. The server may return fewer than `maxResults` items even if there are more available. The client should check `nextPageToken` in the response to determine if there are more available. Must be non-negative. 0 will return no results but `nextPageToken` may be populated.<br><br>**pageToken** (type: String, optional): Specifies a page token to use. Set `pageToken` to the `nextPageToken` returned by a previous list request to get the next page of results. `nextPageToken` will not be returned in a response if there are no more results available.

<details open>
<summary><b>200: The volumes were successfully returned.</b></summary>

Same response format as [List All Volumes in a Share](#list-all-volumes-in-a-share).

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
<summary><b>401: The request is unauthenticated.</b></summary>

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

---

### Get Volume

Get info for a specific volume.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/schemas/{schema}/volumes/{volume}`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{schema}**: The schema name to query. It's case-insensitive.<br><br>**{volume}**: The volume name to query. It's case-insensitive.

<details open>
<summary><b>200: The volume was successfully returned.</b></summary>

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
  "storageLocation": "string"
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
<summary><b>401: The request is unauthenticated.</b></summary>

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

---

### Generate Temporary Volume Credentials

Generate temporary cloud credentials for accessing a volume's storage location. The response provides short-lived credentials scoped to the volume's `storageLocation`, which the recipient uses to access files directly via the cloud storage API.

The credential model follows the same pattern as [GenerateTemporaryTableCredentials](https://github.com/unitycatalog/unitycatalog/blob/main/api/Apis/TemporaryCredentialsApi.md#generatetemporaryvolumecredentials) in UC OSS.

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

`{prefix}/shares/{share}/schemas/{schema}/volumes/{volume}/temporary-volume-credentials`
</td>
</tr>
<tr>
<td>URL Parameters</td>
<td>

**{share}**: The share name to query. It's case-insensitive.

**{schema}**: The schema name to query. It's case-insensitive.

**{volume}**: The volume name to query. It's case-insensitive.
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
<summary><b>401: The request is unauthenticated.</b></summary>

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

---

## Credential Models

The credential models (`TemporaryCredentials`, `AwsCredentials`, `AzureUserDelegationSAS`, `GcpOauthToken`, `R2Credentials`) are defined in [CREDENTIALS.md](CREDENTIALS.md) and shared across all asset types.
