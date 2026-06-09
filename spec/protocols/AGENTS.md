# Agent Sharing

> **Status: Community Proposal.** The agent sharing specification is at an early stage and the design is not final. Key open questions include the invocation model, session management across organizational boundaries, and where governance should be enforced. We are publishing this to invite community input before finalizing. Please open an issue or discussion with feedback.

## Motivation

OpenSharing supports sharing static data and AI artifacts — tables, volumes, models, and agent skills. These asset types share a common access model: the sharing server issues scoped temporary credentials, and the recipient accesses the asset directly from cloud storage.

`Agent` is a different kind of asset. An agent is a running service: the recipient sends a request, the agent executes on the provider's infrastructure, and the provider returns a result. The recipient never gets direct access to storage, model weights, or internal tooling — only the output of a call.

This is distinct from `AgentSkill`. An `AgentSkill` is a directory of files the recipient downloads and runs locally inside their own agent. An `Agent` is a service the recipient calls remotely. The protocol needs both: skills for capabilities you can ship as files, agents for capabilities that need to stay on the provider's side.

The access model follows the same pattern as the rest of the protocol. The sharing server issues a short-lived invocation token and endpoint; the recipient calls the agent directly using the declared `invocationProtocol`.

## Protocol Changes

The following new endpoints are introduced to support agent sharing.

### Agent Object

An agent object has the following fields:

| Name | Type | Description | Notes |
|---|---|---|---|
| name | String | The name of the agent. | [required] |
| schema | String | The schema the agent belongs to. | [required] |
| share | String | The share the agent belongs to. | [required] |
| shareId | String | A unique, immutable identifier for the share across the sharing server. Recommended format: UUID. | [optional] |
| id | String | A unique, immutable identifier for the agent within the share. Recommended format: UUID. | [optional] |
| description | String | A human- and LLM-readable description of what this agent does, what problems it solves, and what inputs it expects. Providers should write this as if describing the agent to another AI system deciding whether to invoke it. | [required] |
| invocationProtocol | String | The protocol used to invoke this agent once credentials have been obtained. One of: `mcp`, `a2a`, `openai`, `anthropic`, `rest`. See [Invocation Protocols](#invocation-protocols). | [required] |
| capabilities | Array\<String\> | Capability identifiers declaring what this agent can do (e.g., `sql_query`, `document_search`, `code_execution`). Intended for programmatic discovery and routing. | [optional] |
| inputSchema | Object | JSON Schema describing the expected input payload. | [optional] |

Note: object names (`name`, `schema`, `share`) must not exceed 255 characters and must not contain restricted characters.

Note: `description` must not exceed 65536 characters.

Note: the `id` field is optional. If populated, its value should be unique within the share and stay immutable through the agent's lifecycle.

Note: the `shareId` field is optional. If populated, its value should be unique across the sharing server and immutable through the agent's lifecycle.

---

### List All Agents in a Share

List all agents in a share, across all schemas.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/all-agents`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): The maximum number of results per page that should be returned. If the number of available results is larger than `maxResults`, the response will provide a `nextPageToken` that can be used to get the next page of results in subsequent list requests. The server may return fewer than `maxResults` items even if there are more available. The client should check `nextPageToken` in the response to determine if there are more available. Must be non-negative. 0 will return no results but `nextPageToken` may be populated.<br><br>**pageToken** (type: String, optional): Specifies a page token to use. Set `pageToken` to the `nextPageToken` returned by a previous list request to get the next page of results. `nextPageToken` will not be returned in a response if there are no more results available.

<details open>
<summary><b>200: The agents were successfully returned.</b></summary>

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
      "invocationProtocol": "string",
      "capabilities": ["string"]
    }
  ],
  "nextPageToken": "string"
}
```

Note: the `items` field may be an empty array or missing when no results are found. The client must handle both cases.

Note: the `nextPageToken` field may be an empty string or missing when there are no additional results. The client must handle both cases.

Note: `inputSchema` is omitted from list responses. Retrieve it via the Get Agent endpoint.

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

---

### List Agents in a Schema

List agents in a schema under a share.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/schemas/{schema}/agents`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{schema}**: The schema name to query. It's case-insensitive.
Query Parameters | **maxResults** (type: Int32, optional): The maximum number of results per page that should be returned. If the number of available results is larger than `maxResults`, the response will provide a `nextPageToken` that can be used to get the next page of results in subsequent list requests. The server may return fewer than `maxResults` items even if there are more available. The client should check `nextPageToken` in the response to determine if there are more available. Must be non-negative. 0 will return no results but `nextPageToken` may be populated.<br><br>**pageToken** (type: String, optional): Specifies a page token to use. Set `pageToken` to the `nextPageToken` returned by a previous list request to get the next page of results. `nextPageToken` will not be returned in a response if there are no more results available.

<details open>
<summary><b>200: The agents were successfully returned.</b></summary>

Same response format as [List All Agents in a Share](#list-all-agents-in-a-share).

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

---

### Get Agent

Get metadata for a specific agent, including its full `description`, `invocationProtocol`, `capabilities`, and `inputSchema`.

HTTP Request | Value
-|-
Method | `GET`
Header | `Authorization: Bearer {token}`
URL | `{prefix}/shares/{share}/schemas/{schema}/agents/{agent}`
URL Parameters | **{share}**: The share name to query. It's case-insensitive.<br><br>**{schema}**: The schema name to query. It's case-insensitive.<br><br>**{agent}**: The agent name to query. It's case-insensitive.

<details open>
<summary><b>200: The agent was successfully returned.</b></summary>

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
  "invocationProtocol": "string",
  "capabilities": ["string"],
  "inputSchema": {}
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

`GET {prefix}/shares/ai_share/schemas/support/agents/ticket_resolver`

```json
{
  "name": "ticket_resolver",
  "schema": "support",
  "share": "ai_share",
  "shareId": "edacc4a7-6600-4fbb-85f3-a62a5ce6761f",
  "id": "9f1b3c2d-4e5a-6f7b-8c9d-0e1f2a3b4c5d",
  "description": "Resolves customer support tickets by querying the provider's internal knowledge base and ticket history. Given a ticket description and optional customer context, returns a recommended resolution, relevant past cases, and a confidence score. Does not escalate or take action — returns analysis only.",
  "invocationProtocol": "rest",
  "capabilities": ["knowledge_base_search", "ticket_history_lookup"],
  "inputSchema": {
    "type": "object",
    "properties": {
      "ticket_description": { "type": "string" },
      "customer_id": { "type": "string" },
      "priority": { "type": "string", "enum": ["low", "medium", "high"] }
    },
    "required": ["ticket_description"]
  }
}
```

---

### Generate Temporary Agent Credentials

Generate a short-lived invocation token and endpoint for calling a shared agent. The recipient uses these credentials to call the agent directly using the declared `invocationProtocol`.

Note: implementations should ensure the returned `endpoint` routes through a governed proxy or gateway rather than directly to the raw agent backend. This preserves governance enforcement, cost tracking, and attribution on the provider side.

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

`{prefix}/shares/{share}/schemas/{schema}/agents/{agent}/temporary-agent-credentials`
</td>
</tr>
<tr>
<td>URL Parameters</td>
<td>

**{share}**: The share name to query. It's case-insensitive.

**{schema}**: The schema name to query. It's case-insensitive.

**{agent}**: The agent name to query. It's case-insensitive.
</td>
</tr>
<tr>
<td>Body</td>
<td>

Optional. If provided, must be `Content-Type: application/json`.

```json
{
  "sessionId": "string"
}
```

`sessionId` (optional): Resume an existing session for multi-turn agents. Omit to start a new stateless invocation.

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

```json
{
  "endpoint": "string",
  "bearerToken": "string",
  "sessionId": "string",
  "expirationTime": 0,
  "invocationProtocol": "string"
}
```

See [AgentInvocationCredentials](#agentinvocationcredentials).

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

`POST {prefix}/shares/ai_share/schemas/support/agents/ticket_resolver/temporary-agent-credentials`

```json
{
  "endpoint": "https://agents.acme.com/opensharing/ticket_resolver",
  "bearerToken": "ost_7f3a2b1c...",
  "sessionId": null,
  "expirationTime": 1780400000000,
  "invocationProtocol": "rest"
}
```

---

## Invocation Protocols

The `invocationProtocol` field declares how the recipient should call the agent endpoint once credentials have been obtained.

**`mcp`** — The agent exposes a [Model Context Protocol](https://modelcontextprotocol.io/specification) server. Initialize an MCP session with the returned `endpoint` and `bearerToken`.

**`a2a`** — The agent implements the [Agent-to-Agent protocol](https://google.github.io/A2A/). Send tasks to the `endpoint` using the A2A task schema with `bearerToken` in the `Authorization` header.

**`openai`** — The agent exposes an OpenAI-compatible chat completions endpoint. Call `POST {endpoint}/v1/chat/completions` with `bearerToken` as the bearer token.

**`anthropic`** — The agent exposes an Anthropic-compatible messages endpoint. Call `POST {endpoint}/v1/messages` with `bearerToken` as the bearer token. Adopted by Anthropic, Amazon Bedrock, DeepSeek, and LiteLLM.

**`rest`** — A minimal REST baseline intended as a fallback when no other protocol applies. Call `POST {endpoint}/invoke` with `Authorization: Bearer {bearerToken}` and a JSON body containing `{ "input": <string or object>, "sessionId": <string|null> }`. The response is `{ "output": <string or object>, "sessionId": <string|null> }`. Providers requiring streaming, tool use, or richer session semantics should declare `mcp`, `a2a`, `openai`, or `anthropic` instead.

---

## Credential Models

### AgentInvocationCredentials

| Name | Type | Description | Notes |
|---|---|---|---|
| endpoint | String | The URL of the agent's invocation endpoint. | [required] |
| bearerToken | String | A short-lived token the recipient includes as `Authorization: Bearer {bearerToken}` when calling the agent. | [required] |
| sessionId | String | Session identifier for multi-turn agents. Null for stateless invocations. Include in subsequent `temporary-agent-credentials` requests to continue the session. | [optional] |
| expirationTime | Long | Server time when the `bearerToken` expires, in epoch milliseconds. | [required] |
| invocationProtocol | String | Echoes the agent's declared `invocationProtocol`. | [required] |
