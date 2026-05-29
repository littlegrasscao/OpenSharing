# Shares
A share is a logical grouping to share with recipients. A share can be shared with one or multiple recipients. A recipient can access all resources in a share. A share may contain multiple schemas.

## REST APIs

Here are the list of APIs to access shares in OpenSharing Protocol. All of the REST APIs use [bearer tokens for authorization](https://tools.ietf.org/html/rfc6750). The `{prefix}` of each API is configurable, and servers hosted by different providers may pick up different prefixes.

### List Shares

This is the API to list shares accessible to a recipient.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares`
Query Parameters | **maxResults** (type: Int32, optional): The maximum number of results per page that should be returned. If the number of available results is larger than `maxResults`, the response will provide a `nextPageToken` that can be used to get the next page of results in subsequent list requests. The server may return fewer than `maxResults` items even if there are more available. The client should check `nextPageToken` in the response to determine if there are more available. Must be non-negative. 0 will return no results but `nextPageToken` may be populated.<br><br>**pageToken** (type: String, optional): Specifies a page token to use. Set `pageToken` to the `nextPageToken` returned by a previous list request to get the next page of results. `nextPageToken` will not be returned in a response if there are no more results available.

<details open>
<summary><b>200: The shares were successfully returned.</b></summary>

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
      "id": "string",
      "displayName": "string",
      "comment": "string",
      "properties": {
        "key": "value"
      }
    }
  ],
  "nextPageToken": "string"
}
```

Note: the `items` field may be an empty array or missing when no results are found. The client must handle both cases.

Note: check the format of the `name` field in the sharing service. Object names must not exceed 255 characters and must not contain restricted characters. 

Note: the `id` field is optional. If `id` is populated for a share, its value should be unique across the sharing server and stay immutable through the share's lifecycle. The format recommendation of `id` is UUID.

Note: the `displayName` is optional. If `displayName` is populated for a share, this is the share name that should be displayed to the user. `displayName` must not exceed 255 characters, otherwise it could be truncated by the client.

Note: the `comment` is optional and should not exceed 65536 characters, otherwise it could be truncated by the client.

Note: the `properties` field is optional. If `properties` is populated for a share, it should be of type `Map<String, String>`. `key` should not exceed 255 characters and `value` should not exceed 1000 characters. The number of key-value pairs should not exceed 50 and could be truncated by the client. Exceeding the above lengths could result in truncation by the client.

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

`GET {prefix}/shares?maxResults=10&pageToken=...`

```json
{
   "items": [
      {
         "name": "vaccine_share",
         "id": "edacc4a7-6600-4fbb-85f3-a62a5ce6761f",
         "displayName": "Vaccine Share",
         "comment": "A sample share containing vaccine-related datasets",
         "properties": {
           "owner": "vaccine-team",
           "region": "us-west-2",
           "created_date": "2024-01-15"
         }   
      },
      {
         "name": "sales_share",
         "id": "3e979c79-6399-4dac-bcf8-54e268f48515",
         "displayName": "Sales Share",
         "comment": "A sample share containing sales and revenue data",
         "properties": {
           "owner": "sales-team",
           "region": "us-east-1",
           "created_date": "2024-02-20"
         }
      }
   ],
   "nextPageToken": "..."
}
```

### Get Share

This is the API to get the metadata of a share.

HTTP Request | Value
-- | --
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.

<details open>
<summary><b>200: The share's metadata was successfully returned.</b></summary>

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
  "share": {
    "name": "string",
    "id": "string",
    "displayName": "string",
    "comment": "string",
    "properties": {
      "key": "value"
    }
  }
}
```

Note: the `id` field is optional. If `id` is populated for a share, its value should be unique across the sharing server and stay immutable through the share's lifecycle. The format recommendation of `id` is UUID.

Note: check the format of the `name` field in the sharing service. Object names must not exceed 255 characters and must not contain restricted characters. 

Note: the `displayName` is optional. If `displayName` is populated for a share, this is the share name that should be displayed to the user. `displayName` must not exceed 255 characters, otherwise it could be truncated by the client.

Note: the `comment` is optional and should not exceed 65536 characters, otherwise it could be truncated by the client.

Note: the `properties` field is optional. If `properties` is populated for a share, it should be of type `Map<String, String>`. `key` should not exceed 255 characters and `value` should not exceed 1000 characters. The number of key-value paris should not exceed 50, otherwise it could be truncated by the client. Exceeding the above lengths could result in truncation by the client.
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

Example:

`GET {prefix}/shares/vaccine_share`

```json
{
  "share": {
    "name": "vaccine_share",
    "id": "edacc4a7-6600-4fbb-85f3-a62a5ce6761f",
    "displayName": "Vaccine Share",
    "comment": "A sample share containing vaccine-related datasets",
    "properties": {
      "owner": "vaccine-team",
      "region": "us-west-2",
      "created_date": "2024-01-15"
    }
  }
}
```
 
