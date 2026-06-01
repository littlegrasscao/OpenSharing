# OpenSharing

**The open standard for sharing data and AI assets across any platform, cloud, or organizational boundary.**

OpenSharing is the open-source protocol for sharing structured data, unstructured data, AI models, agent skills, and agents — across any cloud, any vendor, and any format.

> **Note:** This repository contains the OpenSharing specification and roadmap. We are publishing this as a community proposal and actively seeking feedback on the protocol design, asset type coverage, and roadmap priorities.

---

## Why OpenSharing

OpenSharing is a simple REST protocol for secure, real-time sharing of data and AI assets stored in the cloud. Using zero-copy sharing, assets stay in place — a provider shares access to a table, file collection, model, agent skill, or agent service, and a recipient connects directly through Spark, Pandas, Tableau, or any compatible system, without deploying a specific platform first.

OpenSharing covers a wide range of data and AI assets organizations share today:

- **Structured data** — Delta Lake, Apache Iceberg, and Parquet tables
- **Unstructured data** — documents, media, embeddings, raw data, and any file-based asset
- **ML models** — trained artifacts, weights, and evaluation metadata
- **Agent skills** — reusable AI capabilities that other agents can download and run locally
- **Agents** — live agent services invoked remotely. The sharing server issues a short-lived invocation token and endpoint; the recipient calls the agent directly.

---

## The Protocol

### Asset Hierarchy

OpenSharing uses a three-level hierarchy:

```
Share
 └── Schema
       ├── Table
       ├── Volume
       ├── AgentSkill
       ├── Model
       └── Agent
```

**Share** — A named, access-controlled collection of assets granted to one or more recipients. A single credential grants access to everything within a share.

**Schema** — A logical namespace grouping related assets within a share.

**Asset** — The data or AI artifact being shared. OpenSharing defines a set of standard asset types, each with its own metadata model and access API.

### Asset Types

| Asset Type | Status | Description |
|---|---|---|
| **Table** | Specified | Structured data in [Delta Lake](https://delta.io/), [Apache Iceberg](https://iceberg.apache.org/), and Parquet formats. |
| **Volume** | Specified | A directory of files of any format — documents, media, embeddings, raw data. Access via scoped temporary cloud credentials. |
| **AgentSkill** | Specified | Reusable AI capabilities a recipient downloads and runs locally inside their own agent. Each skill is a self-contained asset with its own storage location and scoped credentials. |
| **Model** | Specified | ML model artifacts with version metadata, run provenance, and credential-vended access to artifact storage. |
| **Agent** | Proposed | A live, callable agent service. The sharing server issues a short-lived invocation token and endpoint; the recipient calls the agent directly using the declared invocation protocol. |

---

## How It Works

### For Providers

A provider creates a **share**, adds assets, and grants access to recipients. Assets are never copied — recipients access them directly using short-lived credentials issued by the sharing server.

```
POST /shares/{share}/schemas/{schema}/tables/{table}/temporary-table-credentials
→ returns AWS STS / Azure SAS / GCP OAuth / R2 token scoped to that table's storage location

POST /shares/{share}/schemas/{schema}/agents/{agent}/temporary-agent-credentials
→ returns a short-lived bearer token and endpoint for calling the agent directly
```

### For Recipients

A recipient authenticates with a bearer token and uses standard list/get/read APIs to discover and consume assets — all through a unified protocol.

```
GET /shares
GET /shares/{share}/schemas
GET /shares/{share}/schemas/{schema}/tables
GET /shares/{share}/all-tables
GET /shares/{share}/schemas/{schema}/volumes
GET /shares/{share}/schemas/{schema}/skills
GET /shares/{share}/all-skills
GET /shares/{share}/schemas/{schema}/models
GET /shares/{share}/schemas/{schema}/agents
GET /shares/{share}/all-agents
POST /shares/{share}/schemas/{schema}/agents/{agent}/temporary-agent-credentials
```

### Zero-Copy Credential Vending

OpenSharing uses **credential vending** for secure, zero-copy access: the sharing server issues temporary, scoped credentials that expire automatically. Recipients access assets directly — the sharing server is never in the data path, and data is never duplicated.

Each asset type has its own credential endpoint:

- `POST .../tables/{table}/temporary-table-credentials` → scoped cloud storage credentials (AWS STS, Azure SAS, GCP OAuth, Cloudflare R2)
- `POST .../volumes/{volume}/temporary-volume-credentials` → scoped cloud storage credentials
- `POST .../models/{model}/versions/{version}/temporary-model-credentials` → scoped cloud storage credentials
- `POST .../skills/{skill}/temporary-skill-credentials` → scoped cloud storage credentials
- `POST .../agents/{agent}/temporary-agent-credentials` → short-lived bearer token and endpoint for calling the agent directly

---

## Specifications

The protocol is defined as a set of markdown specifications in the [`spec/`](./spec/) directory:

| Spec | Description |
|---|---|
| [`spec/protocols/OVERVIEW.md`](./spec/protocols/OVERVIEW.md) | Protocol overview, authentication, and common patterns |
| [`spec/protocols/SHARES.md`](./spec/protocols/SHARES.md) | Share object and list/get APIs |
| [`spec/protocols/SCHEMAS.md`](./spec/protocols/SCHEMAS.md) | Schema object and list API |
| [`spec/protocols/TABLES.md`](./spec/protocols/TABLES.md) | Table asset type specification |
| [`spec/protocols/VOLUME_SHARING.md`](./spec/protocols/VOLUME_SHARING.md) | Volume asset type specification |
| [`spec/protocols/AGENT_SKILLS.md`](./spec/protocols/AGENT_SKILLS.md) | AgentSkill asset type specification (in review) |
| [`spec/protocols/MODEL_SHARING.md`](./spec/protocols/MODEL_SHARING.md) | Model asset type specification (proposed) |
| [`spec/protocols/AGENT_SHARING.md`](./spec/protocols/AGENT_SHARING.md) | Agent asset type specification (proposed) |
| [`spec/protocols/CREDENTIALS.md`](./spec/protocols/CREDENTIALS.md) | Shared credential model definitions |

---

## Community and Governance

OpenSharing is being submitted as a sandbox project under the [Linux Foundation AI & Data](https://lfaidata.foundation/) foundation. The protocol is developed in the open, and we welcome contributions, feedback, and implementations from across the data and AI ecosystem.

**How to participate:**

- **Feedback on protocol design** — Open an issue or discussion in this repository
- **Implement a server or client** — Any compliant implementation is welcome
- **Propose new asset types** — Open an issue describing the use case and asset model
- **Join the community** — [Community channels TBD]

This specification is a community proposal. Many of the AI asset types described in this document are early proposals, and we are actively soliciting input on the design choices before finalizing the spec. See [`ROADMAP.md`](./ROADMAP.md) for the current direction and open questions.

---

## License

The OpenSharing specification is licensed under [Apache 2.0](./LICENSE).
