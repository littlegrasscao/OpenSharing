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

# OpenSharing Specification

## Concepts

- Share: A share is a logical grouping to share with recipients. A share can be shared with one or multiple recipients. A recipient can access all resources in a share. A share may contain multiple schemas.
- Schema: A schema is a logical grouping of shared assets, including tables, volumes, models, skills, etc.
- Table: A table is a [Delta Lake](https://delta.io/) table or a view on top of a Delta Lake table.
- Volume: A volume is a directory-based storage locations with related metadata.
- Recipient: A principal that has a bearer token to access shared assets.
- Sharing Server: A server that implements this protocol on the server side.
- Sharing Client: A client that implements this protocol on the client side.

## Profile File Format

A profile file is a JSON file that contains the information for a recipient to access shared data on a OpenSharing server. There are a few fields in this file as listed below.

Field Name | Descrption
-|-
shareCredentialsVersion | The file format version of the profile file. This version will be increased whenever non-forward-compatible changes are made to the profile format. When a client is running an unsupported profile file format version, it should show an error message instructing the user to upgrade to a newer version of their client.
endpoint | The url of the sharing server.
bearerToken | The [bearer token](https://tools.ietf.org/html/rfc6750) to access the server.
expirationTime | The expiration time of the bearer token in [ISO 8601 format](https://www.w3.org/TR/NOTE-datetime). This field is optional and if it is not provided, the bearer token can be seen as never expire.

Example:

```json
{
  "shareCredentialsVersion": 1,
  "endpoint": "https://sharing.opensharing.io/open-sharing/",
  "bearerToken": "<token>",
  "expirationTime": "2021-11-12T00:12:29.0Z"
}
```

## Names

Share, Schema, and shared objects are identifiable by names. To ensure compatibility and avoid issues across different sharing servers, the following limitations apply for object names:

- Object names cannot exceed 255 characters.
- The following special characters are not allowed for all object names:
  - Space (` `)
  - Forward slash (`/`)
  - All ASCII control characters (`00-1F` hex)
  - The DELETE character (`7f` hex)
- Schema and shared object names additionally do not allow special character Period (`.`)
- Object names are case-insensitive
