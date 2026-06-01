# OpenSharing Roadmap

This document describes the proposed direction for the OpenSharing protocol. It is a community proposal — not a committed product roadmap. We are sharing this to invite feedback.

> **We want to hear from you.** If you have a use case that isn't covered, a protocol design concern, or a proposal to add an asset type, please open an issue or discussion.

---

## Initial Release

OpenSharing launches with four asset types covering a wide range of data and AI assets organizations share today.

### Tables

Structured data in [Delta Lake](https://delta.io/), [Apache Iceberg](https://iceberg.apache.org/), and Parquet formats.

See [spec](./spec/protocols/TABLES.md).

### Volumes

Unstructured or semi-structured file collections — documents, media, embeddings, raw data, and any file-based asset. The protocol is agnostic to file contents and format. Access via temporary cloud credentials scoped to the volume's storage location.

See [spec](./spec/protocols/VOLUME_SHARING.md).

### Agent Skills

Reusable AI capabilities following the [AgentSkills specification](https://agentskills.io/specification). Each skill is a directory of files an agent can load and execute, shared as a self-contained asset with its own storage location and scoped credentials.

See [spec](./spec/protocols/AGENT_SKILLS.md).

### ML Models

ML model artifacts with version metadata, run provenance, and credential-vended access to artifact storage. `Model` and `ModelVersion` are first-class asset types — each version has its own `storageLocation` and tracks status from registration to ready.

---

## Future Roadmap

### Agent Sharing

- **What is "Agent"** — Callable agent services: complete AI agents that take a goal and act on it autonomously, as opposed to file-based agent skills that a local agent loads and runs itself.
- **What we plan to do** — Extend OpenSharing to support sharing complete agents as callable services, with standard protocols for discovery, invocation, and cross-organizational trust.
- **Why it matters** — Organizations are building specialized agents for specific domains; sharing them across boundaries avoids rebuilding the same capability independently on every side.

### Semantic Sharing

- **What is "Semantic"** — Per-asset annotations that describe business meaning: what fields represent, how to interpret values, and metric definitions (KPIs, aggregation rules, computed measures).
- **What we plan to do** — Add an `ai_context` metadata field to every asset type and introduce a `Metric` asset type for sharing business logic alongside data.
- **Why it matters** — An AI consuming a shared table can read its structure but not its intent. Without semantic metadata, it has no way to know that `amt` is a revenue figure in USD, that `status=2` means "completed," or how to aggregate a metric correctly — and will guess wrong.

### Ontology Sharing

- **What it "Ontology"** — A graph of canonical business concepts and relationships — entities like `Customer`, `Order`, `Product` and how they connect — decoupled from any physical schema.
- **What we plan to do** — Introduce an `Ontology` asset type that providers can share alongside their data assets, declaring entities, relationships, and semantic mappings to physical fields.
- **Why it matters** — Even with well-annotated individual assets, an AI can't reason across them without a shared concept layer. It can't determine which tables to join, whether `orders.cust_id` and `users.id` are the same entity, or whether two providers use "customer" to mean the same thing.

### Eval Dataset & Benchmark Sharing

- **What is "Eval Dataset & Benchmark"** — Curated test sets, human preference annotations, and domain benchmarks used to measure model quality and track regressions.
- **What we plan to do** — Add evaluation-specific metadata (task type, scoring rubric, model family) and per-recipient access scoping so providers can share test sets with specific partners without making them public.
- **Why it matters** — Evaluation datasets are how organizations measure whether a model works on their specific tasks. Once a test set is public, models can be trained on it directly, which invalidates it as a benchmark. Teams still need to share evals with trusted partners, but today there is no standard way to do this with controlled access.

### Synthetic Dataset Sharing

- **What is "Synthetic Dataset"** — Datasets algorithmically generated to mimic the statistical properties of real data, used when the underlying data is too sensitive to share directly (PII, financial records, health data).
- **What we plan to do** — Add a provenance metadata schema to synthetic datasets: generator model, seed schema, privacy technique, and quality metrics as governed fields on the asset.
- **Why it matters** — Real data is often too sensitive or legally restricted to share directly. Synthetic datasets let organizations share the statistical properties of their data without exposing actual records. But a synthetic dataset is only trustworthy if you know how it was made: which model generated it, what real data it was based on, and what privacy technique was applied.

### Vector Index Sharing

- **What is "Vector Index"** — Pre-computed embedding representations of data — documents, tables, knowledge bases — used for semantic search and retrieval in AI applications.
- **What we plan to do** — Define a `VectorIndex` asset type with the metadata recipients need to use a shared index: embedding model and version, chunking strategy, dimensionality, and similarity metric.
- **Why it matters** — Vector indexes are widely used in AI applications for semantic search and retrieval, but they are expensive to compute and tied to the specific model that produced them. Today there is no standard way to share an index across organizational boundaries with the metadata a recipient needs to use it.

---

## Contributing to the Roadmap

This roadmap is a proposal, not a decree. The protocol should be shaped by the organizations and developers building on it. To contribute:

1. **Open an issue** with your use case, pain point, or proposal
2. **Start a discussion** for broader design questions
3. **Submit a PR** with a spec change or addition — all significant changes will go through community review

We follow a lightweight process for spec changes: PR → discussion period (minimum 7 days) → consensus vote. Breaking changes require a stronger consensus signal.

---

*Last updated: June 2026*
