# OpenSharing Roadmap

This document describes the proposed direction for the OpenSharing protocol. It is a community proposal — not a committed product roadmap. We are sharing this to invite feedback.

> **We want to hear from you.** If you have a use case that isn't covered, a protocol design concern, or a proposal to add an asset type, please open an issue or discussion.

---

## Initial Release

OpenSharing launches with four specified asset types and two community proposals.

### Tables

Structured data in [Delta Lake](https://delta.io/), [Apache Iceberg](https://iceberg.apache.org/), and Parquet formats.

See [spec](./spec/protocols/TABLES.md).

### Volumes

Unstructured or semi-structured file collections — documents, media, embeddings, raw data, and any file-based asset. The protocol is agnostic to file contents and format. Access via temporary cloud credentials scoped to the volume's storage location.

See [spec](./spec/protocols/VOLUMES.md).

### Agent Skills

Reusable AI capabilities following the [AgentSkills specification](https://agentskills.io/specification). Each skill is a directory of files an agent can load and execute, shared as a self-contained asset with its own storage location and scoped credentials.

See [spec](./spec/protocols/AGENT_SKILLS.md).

### ML Models

ML model artifacts with version metadata, run provenance, and credential-vended access to artifact storage. `Model` and `ModelVersion` are first-class asset types — each version has its own `storageLocation` and tracks status from registration to ready.

See [spec](./spec/protocols/ML_MODELS.md).

### Agent (Community Proposal)

Live, callable agent services. Unlike `AgentSkill` assets, a shared agent is a service the provider operates — the recipient invokes it and receives results without accessing the underlying storage or model. This specification is at an early stage; see the spec for open design questions.

See [spec](./spec/protocols/AGENTS.md).

### Page / Glossary (Community Proposal)

A named business entity, metric, dimension, or term — with a markdown definition and relationships to other pages in the same schema. Pages give recipients the business context needed to correctly interpret shared data assets. This specification is at an early stage; see the spec for open design questions.

See [spec](./spec/protocols/GLOSSARY.md).

---

## Future Roadmap

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
