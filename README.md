# OpenSharing

**The open sharing protocol for the agentic era.**

OpenSharing is the open-source sharing protocol for sharing structured data, unstructured data, AI models, AI agents, agent skills, and more — across any platform, any vendor, and any format.


---

## Why OpenSharing

OpenSharing is a simple REST protocol for secure, real-time sharing of data and AI assets. Using zero-copy sharing, assets stay in place — a provider shares access to an asset such as a table, file collection, model, agent, agent skill, and more – and a recipient connects directly through Spark, Pandas, Tableau, or any compatible system, without deploying a specific platform first.

OpenSharing covers a wide range of data and AI assets organizations share today:

- **Structured data** — open table formats including Delta Lake and Apache Iceberg, accessible to any client via Delta Sharing or Iceberg REST Catalog APIs
- **Unstructured data** — documents, media, embeddings, raw data, and any file-based asset
- **ML models** — trained artifacts, weights, and evaluation metadata
- **Agent skills** — reusable AI capabilities that other agents can discover and invoke
- **Agents** (community proposal) — live, callable agent services that recipients can invoke across organizational boundaries
- **Glossary (Page)** (community proposal) — named definitions of entities, metrics, and terms shared alongside data assets to provide context to AI and humans

OpenSharing also works with on-premises and private-cloud storage. Organizations that must keep data on-premises — for data sovereignty, regulatory compliance, or data gravity — can participate in the sharing ecosystem without moving data. The following storage providers have built native OpenSharing support: **Everpure**, **MinIO**, and **Qumulo**, with **Cohesity**, **Commvault**, **Hewlett Packard Enterprise**, **NetApp**, **Nutanix**, **Rubrik**, and **VAST Data** coming soon.

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

A recipient authenticates with a bearer token and uses standard list/get/read APIs to discover and consume assets. The same client can consume tables as DataFrames, download volume files, load model artifacts, or enumerate available agent skills — all through a unified protocol.

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

OpenSharing uses **credential vending** for secure, zero-copy access: the sharing server issues temporary, scoped cloud credentials (AWS STS, Azure SAS, GCP OAuth, Cloudflare R2) that expire automatically. Recipients read data directly from cloud storage — the sharing server is never in the data path, and data is never duplicated.

Each asset type has its own credential endpoint scoped precisely to that asset's storage location:

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
| [`spec/protocols/VOLUME_SHARING.md`](./spec/protocols/VOLUME_SHARING.md) | Volume asset type specification |
| [`spec/protocols/AGENT_SKILLS.md`](./spec/protocols/AGENT_SKILLS.md) | AgentSkill asset type specification |
| [`spec/protocols/ML_MODELS.md`](./spec/protocols/ML_MODELS.md) | Model asset type specification |
| [`spec/protocols/AGENT_SHARING.md`](./spec/protocols/AGENT_SHARING.md) | Agent asset type specification (community proposal) |
| [`spec/protocols/glossary-sharing-spec.md`](./spec/protocols/glossary-sharing-spec.md) | Page asset type specification (community proposal) |
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
