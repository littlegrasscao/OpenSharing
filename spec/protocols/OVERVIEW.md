<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
# Open Sharing Protocol

- [Overview](#overview)
- [Open Sharing Specification](#open-sharing-specification)
  - [Concepts](#concepts)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Overview

[Open Sharing](https://github.com/OpenSharing-IO/OpenSharing) is an open protocol for secure real-time exchange of large datasets and AI assets, which enables secure data and ai sharing across products for the first time. It is a simple REST protocol that securely shares access to part of a cloud dataset. It leverages modern cloud storage systems, such as S3, ADLS, or GCS, to reliably transfer large datasets.

With Open Sharing, the user accessing shared data can directly connect to it through Spark, Pandas, Tableau, or dozens of other systems that implement the open protocol, without having to deploy a specific platform first. This reduces their access time from months to minutes, and makes life dramatically simpler for data providers who want to reach as many users as possible.

This document is an overview for the Open Sharing Protocol, which defines the REST APIs and the formats of messages used by any clients and servers to exchange data.

# Delta Sharing Specification

## Concepts

- Share: A share is a logical grouping to share with recipients. A share can be shared with one or multiple recipients. A recipient can access all resources in a share. A share may contain multiple schemas.
- Schema: A schema is a logical grouping of shared assets, including tables, volumes, models, skills, etc.
- Table: A table is a [Delta Lake](https://delta.io/) table or a view on top of a Delta Lake table.
- Volume: A volume is a directory-based storage locations with related metadata.
- Recipient: A principal that has a bearer token to access shared assets.
- Sharing Server: A server that implements this protocol on the server side.
- Sharing Client: A client that implements this protocol on the client side.
