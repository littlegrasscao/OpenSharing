## Summary

  Build an open-source OpenSharing reference server that turns the current protocol specification into a working, persistent sharing
  service.

  The server will:

  - Remain compatible with existing Delta Sharing clients.
  - Add provider-facing Share and Recipient management APIs.
  - Persist shares, recipients, grants, tokens, and asset membership.
  - Integrate with catalogs through a pluggable interface.
  - Serve recipient-facing OpenSharing and Delta Sharing APIs.
  - Vend temporary, scoped storage credentials.
  - Keep asset data outside the sharing server's data path.

  This is a top-level tracking issue. Individual components should be delivered through focused child issues and pull requests.

  ## Motivation

  OpenSharing currently defines a recipient-facing protocol, but it does not provide a reference server with:

  - Share and Recipient management
  - Persistent entities
  - Provider authentication and authorization
  - Catalog-backed asset validation
  - Dynamic credential vending
  - Recipient token issuance and rotation

  The goal is a single server implementation that works standalone or embedded in a catalog service while preserving the OpenSharing
  guarantee that existing Delta Sharing clients continue to work unchanged.

  ## Goals

  1. Preserve Delta Sharing compatibility.
  2. Add CRUD APIs for Shares and Recipients.
  3. Replace static-only configuration with database-backed entities.
  4. Validate all shared assets against a catalog.
  5. Support pluggable catalog implementations.
  6. Issue one-time recipient activation links and per-recipient tokens.
  7. Support both URL and directory-credential table access.
  8. Establish reusable infrastructure for tables, volumes, models, and agent skills.
  9. Support standalone and embedded deployments.

  ## Non-goals for v1

  - Proxying asset data through the OpenSharing server
  - Partition-level sharing
  - Partial-history table sharing
  - Writing to shared assets
  - Translating between Delta and Iceberg protocols
  - Supporting several catalog connectors in one server process
  - OIDC recipient federation
  - Agents and glossary pages
  - Every proposed OpenSharing asset type

  ## Architecture

  OpenSharing is one server with two logical modules.

  ### Management module

  Responsible for:

  - Share CRUD
  - Recipient CRUD
  - Share-to-asset membership
  - Share-to-recipient grants
  - Provider authentication and authorization
  - Recipient activation and token rotation
  - Persistent metadata

  ### Protocol-serving module

  Responsible for:

  - Recipient authentication and authorization
  - Share, schema, and asset discovery
  - Delta Sharing-compatible table operations
  - Catalog re-resolution at serving time
  - Temporary credential vending
  - Format-specific metadata and action generation

  The server performs metadata operations only. Recipients read asset data directly from provider storage.

  ## End-to-end flow

  A typical table-sharing workflow is:

  1. A provider authenticates and creates a share.
    a. The server asks the catalog to authorize CREATE_SHARE.
    b. The server stores the catalog user ID as the share owner.
  2. The provider adds a catalog table to the share.
    a. The server authenticates the provider and resolves the table through the catalog.
    b. The catalog checks that the provider can access the table.
    c. The server records the asset identity, format, location, alias.
  3. The provider creates a recipient.
    a. The server authorizes CREATE_RECIPIENT and returns a one-time activation URL.
  4. The provider grants the recipient SELECT on the share.
  5. The recipient activates and receives a standard sharing profile.
  6. Recipient reads an asset 
    a. Server resolves the asset as the share owner and validates the owner's catalog access.
    b. The server returns protocol actions, signed URLs, or temporary storage credentials.
  7. The recipient reads the data directly from storage.

## Provider-facing APIs

Routes are relative to the configured provider prefix.

| Operation | Endpoint |
|---|---|
| Create share | `POST /shares` |
| Get share | `GET /shares/{name}` |
| List shares | `GET /shares` |
| Update share or membership | `PATCH /shares/{name}` |
| Delete share | `DELETE /shares/{name}` |
| Create recipient | `POST /recipients` |
| Get recipient | `GET /recipients/{name}` |
| List recipients | `GET /recipients` |
| Update recipient | `PATCH /recipients/{name}` |
| Delete recipient | `DELETE /recipients/{name}` |
| Rotate recipient token | `POST /recipients/{name}/rotate-token` |
| List share grants | `GET /shares/{name}/permissions` |
| Update share grants | `PATCH /shares/{name}/permissions` |
| List recipient shares | `GET /recipients/{name}/share-permissions` |

  Management operations must resolve the provider to a catalog identity. OpenSharing then enforces ownership of its own Shares and
  Recipients.

## Persistent data model

  OpenSharing persists five primary entity tables.

  ### os_shares

  Represents a provider-managed Share.

  Fields include:

  - Immutable Share UUID
  - Unique normalized name
  - Display name
  - Comment
  - Properties
  - Catalog owner_id
  - created_by
  - updated_by
  - Creation and update timestamps

  Deleting a Share removes its SharedDataObjects and SharePermissions.

  ### os_shared_data_objects

  Represents membership between a Share and a catalog asset.

  Fields include:

  - Immutable shared-object UUID
  - Parent Share ID
  - Durable catalog asset ID, when available
  - Canonical source name
  - Asset type
  - Data-source format
  - Source subtype
  - Recipient-visible shared_as alias, split into schema and object name
  - Resolved storage location
  - Added and updated timestamps

  Supported asset types initially include:

  - TABLE
  - SCHEMA
  - VOLUME
  - MODEL
  - SKILL

  Supported table formats initially include:

  - DELTA
  - ICEBERG
  - PARQUET

  Shared objects expose full table history and do not represent individual partitions.

  ### os_recipients

  Represents an external data Recipient.

  Fields include:

  - Immutable Recipient UUID
  - Unique normalized name
  - Owner ID
  - Authentication type
  - Properties
  - Creation and update timestamps

  Supported authentication types are:

  - TOKEN in v1
  - OIDC_FEDERATION in a later phase

  Deleting a Recipient removes its tokens and SharePermissions.

  ### os_recipient_tokens

  Contains one row for every issued or rotated Recipient token. Token history is retained rather than overwritten.

  Fields include:

  - Immutable token UUID
  - Recipient ID
  - SHA-256 bearer-token hash
  - activation-nonce
  - Activation state
  - Expiration
  - Supersession timestamp
  - Creation timestamp

  Token hashes and activation-nonce are unique.

  ### os_share_permissions

  Represents a grant between a Share and Recipient.

  Fields include:

  - Share ID
  - Recipient ID
  - Privilege
  - granted_by
  - Grant timestamp, represented by the row's creation timestamp

  The initial privilege is SELECT.

  The unique constraint is:

  (share_id, recipient_id, privilege)

  This supports additional privileges later without changing the key model.

  ## Metadata store

  Use a database-backed persistence layer with:

  1. JPA entities and Hibernate
  2. Spring Data repositories
  3. Store classes that own transactions and invariants

  Controllers must use stores instead of accessing repositories directly.

  Required constraints include:

  - Case-normalized unique Share and Recipient names
  - Unique source names and recipient aliases within a Share
  - Unique (share, recipient, privilege) grants
  - Unique token and activation-nonce hashes
  - Cascading deletion of Share objects and permissions
  - Cascading deletion of Recipient tokens and permissions
  - Indexed strings sized within supported database limits

  Database support:

  - H2 file database for local development
  - In-memory H2 for tests
  - PostgreSQL for production
  - MySQL for production
  - Hibernate schema updates only for development
  - Versioned migrations and schema validation for production

  ## Catalog integration

  Introduce a CatalogConnector SPI:
  ```java
  public interface CatalogConnector {
    String name();

    // Resolves the provider credential to a catalog user. When a privilege is provided, it also verifies that privilege.
    UserContext authorize(
        AuthContext auth,
        String privilege);

    ResolvedAsset resolveAsset(
        AssetLookup lookup,
        AuthContext auth);

    // Optional: A connector without schema enumeration can still share individual assets.
    default List<ResolvedAsset> listChildren(
        AssetLookup parent,
        AuthContext auth);

    List<StorageCredential> getTemporaryStorageCredentials(
        CredentialRequest request,
        AuthContext auth);
  }

  record AuthContext(
      String serverId,
      UserContext user) {}

  record UserContext(
      String userId,
      String bearerToken,
      String userName) {}
  // Fields may be absent when they are not applicable.
  ```

  ServerId is required for on-behalf-of calls from a standalone OpenSharing deployment. 
  In embedded mode, OpenSharing and Catalog are in the same process, and the stored owner identity is sufficient.

  ### Connector responsibilities

  The connector owns:

  - Provider authentication
  - Catalog authorization
  - Asset resolution
  - Schema enumeration when supported
  - Format and storage discovery
  - Temporary credential vending

  ### Resolved assets

  A resolved asset should include:
-   type
-   identifier
-   catalogAssetId
-   storageLocation
-   metadataLocation
-   dataSourceFormat
-   sourceSubtype
-   schema
-   partitionColumns
-   accessModes
-   auxiliaryLocations

  OpenSharing stores a snapshot of this result, but re-resolves the asset during recipient operations.

  A successful re-resolution refreshes the stored identity, format, locations, and access modes. A missing source becomes
  SOURCE_NOT_FOUND; a catalog denial becomes PERMISSION_DENIED.

  ### Credential vending

  Credential requests identify an asset rather than accepting an arbitrary path:
-   assetType
-   identifier
-   catalogAssetId
-   storageLocation
-   operation
-   ttl

  A connector returns one or more scoped credentials containing:
-   locationPrefix
-   cloudProvider
-   credentials
-   expirationTime

  The requested location must be the primary location or a catalog-approved auxiliary location.
  The server may hold credentials long enough to read metadata or build a response, but must never persist them.

  Supported credential families include:
  - AWS
  - Azure SAS
  - GCP OAuth
  - Cloudflare R2

## Catalog caller semantics

| Operation | Catalog context |
|---|---|
| Create Share or Recipient | Provider credential; authorize the requested management privilege |
| Other provider management operation | Provider credential; resolve identity and enforce stored ownership |
| Add an asset | Provider credential; resolve and authorize the asset |
| List a shared schema | Stored Share owner identity, plus server identity in standalone mode |
| Read an asset | Stored Share owner identity, plus server identity in standalone mode |
| Vend credentials | Stored Share owner identity, plus server identity in standalone mode |
  
Recipient credentials must never be forwarded to the catalog.

  ## Initial connectors

  ### Local connector

  - File-based asset lookup
  - Schema enumeration
  - Static or test credentials
  - Development and protocol tests only

  ### Unity Catalog connector

  - Table and schema resolution
  - Paginated table enumeration
  - Temporary table credential vending
  - Provider authentication and authorization
  - Mapping of catalog IDs, formats, locations, schemas, and partition columns

  The server constructs one connector at startup.

  ## Recipient authentication

  The initial release uses per-recipient bearer tokens.

  1. Creating a Recipient generates a one-time activation nonce.
  2. The provider sends the activation URL out of band.
  3. Opening it generates the actual bearer token.
  4. Only the bearer-token hash is persisted.
  5. Reusing the activation URL is rejected.
  6. Recipient requests validates the bearer token.
  7. Expired, superseded, or revoked tokens are rejected.

  Token rotation supports a grace period. A zero grace period revokes the previous token immediately.

  OIDC federation is deferred unless community feedback establishes it as a v1 requirement.

## Recipient-facing APIs

Follows the pattern in Delta Sharing Protocol: https://github.com/delta-io/delta-sharing/blob/main/PROTOCOL.md

Routes use a configurable protocol prefix, defaulting to `/opensharing`.

| Method | Route |
|---|---|
| `GET` | `/shares` |
| `GET` | `/shares/{share}` |
| `GET` | `/shares/{share}/schemas` |
| `GET` | `/shares/{share}/all-tables` |
| `GET` | `/shares/{share}/schemas/{schema}/tables` |
| `POST` | `/shares/{share}/schemas/{schema}/tables/{table}/temporary-table-credentials` |
| `GET` | `/shares/{share}/schemas/{schema}/tables/{table}/version` |
| `GET` | `/shares/{share}/schemas/{schema}/tables/{table}/metadata` |
| `POST` | `/shares/{share}/schemas/{schema}/tables/{table}/query` |
| `GET` | `/shares/{share}/schemas/{schema}/tables/{table}/changes` |

List responses use:

```json
{
  "items": [],
  "nextPageToken": "..."
}
```
  The default page size is 500 and the maximum is 1,000.

  ## Table-format boundary

  interface TableOperations {
    TableFormat format();
    long version(...);
    ActionStream metadata(...);
    ActionStream query(...);
    ActionStream changes(...);
  }

  A registry holds one implementation per supported format.

  Dispatch uses the format returned by the catalog at serving time. A table converted after registration must not be sent to a reader
  selected from stale metadata.

  If no reader exists, the server returns NOT_IMPLEMENTED and clients may use directory access instead.

  ## Directory access

  The temporary-credential endpoint:

  1. Authorizes the Recipient.
  2. Fetches the visible shared object.
  3. Resolves the source as the Share owner.
  4. Verifies that the requested location belongs to the resolved asset.
  5. Requests credentials from the catalog.
  6. Converts them to the OpenSharing credential response.

  Directory access supports clients that read table formats themselves, formats without server-side readers, and deployments where URL
  access is disabled.

  The server advertises dir only when the catalog can vend credentials for the location.

  ## Delta URL access

  Use Delta Kernel for Delta-log replay and action generation.

  OpenSharing owns:

  - Catalog resolution
  - Authorization
  - Credential acquisition
  - Delta Log replay
  - URL signing
  - Delta Sharing response mapping

  Responses use newline-delimited JSON and include Delta-Table-Version.

  Support both Delta Sharing response formats:

  - parquet: legacy protocol, metadata, and flattened file actions
  - delta: deltaProtocol, deltaMetadata, and deltaSingleAction

  Deletion-vector files are signed and referenced by their data-file actions. The recipient reader applies the vectors.

  Predicate, JSON-predicate, and limit hints remain best effort.

  The server reads the Delta log but never reads table data files.

  ## Other asset types

  After delta table sharing is stable, add:

  1. Volumes
  2. Iceberg Tables
  3. Models
  4. Agent skills
  These reuse the same catalog resolution, authorization, recipient access, and credential-vending infrastructure.

  ## Deployment modes

  The same server artifact and protocol implementation support both modes.

  ### Standalone

  - Independent process
  - Public listener
  - H2 by default
  - Configured local or remote catalog connector
  - Server identity included in on-behalf-of catalog requests

  ### Embedded

  - Host-managed lifecycle
  - Host-provided catalog connector
  - Host-provided or shared database
  - Internal-only OpenSharing listener
  - Public traffic routed through the host
