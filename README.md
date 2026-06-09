<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./assets/logo-horizontal-opensharing-1-color-white.png">
  <source media="(prefers-color-scheme: light)" srcset="./assets/logo-horizontal-opensharing-full-color-navy900.png">
  <img alt="OpenSharing" src="./assets/logo-horizontal-opensharing-full-color-navy900.png" height="60">
</picture>

**The open sharing protocol for the agentic era.**

---

## Why OpenSharing

Sharing data and AI assets across organizations has no single standard today. Tables, files, models, and agent skills each require different mechanisms — often a custom integration per partner, per asset type.

OpenSharing defines one protocol for all of them: the same discovery API, credential vending model, and access controls across tables, volumes, ML models, and agent skills. Any compliant client can consume any asset type without format-specific or platform-specific code.

Four properties across all asset types that are important for the open agentic era:

- **Open standard** — Apache 2.0, governed by the Linux Foundation. Any compliant server or client is valid — no required SDK or platform.
- **AI-native** — covers the full range of assets organizations share today, from structured tables to models and agent skills, with more on the roadmap.
- **Zero-copy** — assets stay in the provider's storage. The sharing server vends temporary, scoped credentials; recipients read directly from the source.
- **Works where data lives** — works with cloud storage like S3, ADLS, GCS, R2, and on-premises environments. Organizations that can't move data can still share it.

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
       ├── Agent
       └── Page
```

**Share** — A named, access-controlled collection of assets granted to one or more recipients. A single credential grants access to everything within a share.

**Schema** — A logical namespace grouping related assets within a share.

**Asset** — The data or AI artifact being shared. OpenSharing defines a set of standard asset types, each with its own metadata model and access API.

### Asset Types

| Asset Type | Status | Description |
|---|---|---|
| **Table** | Specified | Structured data in [Delta Lake](https://delta.io/), [Apache Iceberg](https://iceberg.apache.org/), and Parquet formats. |
| **Volume** | Specified | A directory of files of any format — documents, media, embeddings, raw data. Access via scoped temporary cloud credentials. |
| **AgentSkill** | Specified | Reusable AI agent capabilities following the [AgentSkills specification](https://agentskills.io/specification). Each skill is a self-contained asset with its own storage location and scoped credentials. |
| **Model** | Specified | ML model artifacts with version metadata, run provenance, and credential-vended access to artifact storage. |
| **Agent** | Community proposal | Live, callable agent services. Unlike AgentSkills, a shared agent is a service the provider operates — the recipient invokes it and receives results without accessing the underlying storage or model. |
| **Page** | Community proposal | A named business entity, metric, dimension, or term — with a markdown definition and relationships to other pages in the same schema. |

---

## How It Works

### For Providers

A provider creates a **share**, adds assets (tables, volumes, models, or skills), and issues credentials to recipients. Assets are never copied — recipients access them directly from the provider's cloud storage via short-lived, scoped credentials.

```
POST /shares/{share}/schemas/{schema}/tables/{table}/temporary-table-credentials
→ returns AWS STS / Azure SAS / GCP OAuth / R2 token scoped to that table's storage location
```

### For Recipients

A recipient authenticates with a bearer token and uses standard list/get/read APIs to discover and consume assets. The same client can consume tables as DataFrames, download volume files, load model artifacts, or enumerate available agent skills — all through a unified protocol. Besides using bearer tokens, clients and servers can support other auth mechanisms such as OAuth.

```
GET /shares
GET /shares/{share}/schemas
GET /shares/{share}/schemas/{schema}/tables
GET /shares/{share}/all-tables
GET /shares/{share}/schemas/{schema}/volumes
GET /shares/{share}/schemas/{schema}/skills
GET /shares/{share}/all-skills
GET /shares/{share}/schemas/{schema}/models
```

### Zero-Copy Credential Vending

OpenSharing uses **credential vending** for secure, zero-copy access. The sharing server issues either **pre-signed URLs** or **temporary cloud credentials** (e.g. AWS STS, Azure SAS, GCP OAuth, Cloudflare R2), depending on the asset type and access mode. Recipients access assets directly from the provider's storage — the sharing server is never in the data path.

Each asset type has its own credential endpoint:

- `POST .../tables/{table}/temporary-table-credentials`
- `POST .../volumes/{volume}/temporary-volume-credentials`
- `POST .../models/{model}/versions/{version}/temporary-model-credentials`
- `POST .../skills/{skill}/temporary-skill-credentials`

---

## Specifications

The protocol is defined as a set of markdown specifications in the [`spec/`](./spec/) directory:

| Spec | Description |
|---|---|
| [`spec/protocols/OVERVIEW.md`](./spec/protocols/OVERVIEW.md) | Protocol overview, authentication, and common patterns |
| [`spec/protocols/SHARES.md`](./spec/protocols/SHARES.md) | Share object and list/get APIs |
| [`spec/protocols/SCHEMAS.md`](./spec/protocols/SCHEMAS.md) | Schema object and list API |
| [`spec/protocols/TABLES.md`](./spec/protocols/TABLES.md) | Table asset type specification |
| [`spec/protocols/VOLUMES.md`](./spec/protocols/VOLUMES.md) | Volume asset type specification |
| [`spec/protocols/AGENT_SKILLS.md`](./spec/protocols/AGENT_SKILLS.md) | AgentSkill asset type specification |
| [`spec/protocols/ML_MODELS.md`](./spec/protocols/ML_MODELS.md) | Model asset type specification |
| [`spec/protocols/AGENTS.md`](./spec/protocols/AGENTS.md) | Agent asset type specification (community proposal) |
| [`spec/protocols/GLOSSARY.md`](./spec/protocols/GLOSSARY.md) | Page asset type specification (community proposal) |
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
