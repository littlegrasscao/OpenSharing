# OpenSharing Reference Server

A prototype implementation of the [OpenSharing protocol](../spec/protocols/) — one server that both
manages shares and serves them to recipients, with the catalog and the database as replaceable
components.

**Scope: tables only, and one trivial catalog.** The protocol also defines volumes, agent skills and
models; this prototype starts with tables, shared either one at a time or by the schema that holds
them. The catalog is a trait (`CatalogConnector`) with a single implementation that reads everything
from a local config file — no real catalog is contacted, so the sharing mechanics can be exercised on
their own.

The management side follows Databricks' `DataSharingService` (`data_sharing.proto`): principals own
and author what they create, a share holds `SharedDataObject`s that carry the catalog's canonical name
plus the alias recipients see, and access is a `SharePermission` carrying a privilege. The
provider-admin API speaks snake_case to match; the recipient-facing protocol stays camelCase, as
Delta Sharing clients expect.

Tables are never copied. The server resolves a table in the provider's catalog, asks the catalog to
mint short-lived credentials scoped to that table's storage location, and hands them to the
recipient, who reads the bytes directly from cloud storage.

## Architecture

```
PROVIDER ADMIN (a principal)            RECIPIENT
      |                                    |
      | Bearer <own token>                 | activation URL -> config.share
      | manage shares, recipients, grants  | protocol calls (Bearer token)
      v                                    v
+-------------------------------------------------------------+
|                    OpenSharing server                       |
|                                                             |
|  Provider-admin API             Recipient protocol API      |
|  - principal / share /          - list & get tables         |
|    recipient / permission       - temporary-table-          |
|  - add object (resolved in the    credentials               |
|    catalog AS THE CALLER)       - authN recipient, authZ,   |
|  - recipient token comes with     IP access list            |
|    creation, rotate to replace  - re-resolves the object    |
|                                   before vending access     |
|                                 - read ops dispatch by      |
|                                   format: delta, iceberg    |
+------------+---------------------------------+--------------+
             |                                 |
   Stores (JPA)                       CatalogConnector (SPI)
   principals, shares + permissions,  resolveAsset(lookup, caller, intent)
   recipients + tokens,               getStorageCredentials() -> [prefix..]
   shared data objects                listChildren() (shared schemas only)
                                              |
             |                       LocalCatalogConnector
   H2 / Postgres / MySQL             (declarative file)
                                               |
                                       cloud object storage
                                       (recipient reads directly)
```

### Layout

One Maven module builds one jar, `opensharing-server`. The things the server governs get a package
each, and each one holds everything about that thing: its JPA entity, its repository, its store, the
provider-admin endpoints that touch it, and the request and response types they use. The
recipient-facing protocol is the one exception, and has a package of its own.

| Package | What it holds |
|---|---|
| `principal` | `PrincipalEntity` and `PrincipalStore`, the `/principals` endpoints, and `Caller` — the identity the admin filter resolves and controllers ask for when a write needs an owner. |
| `share` | `ShareEntity`, `SharePermissionEntity` and `SharePrivilege`, `ShareStore`, the provider-admin share and permission endpoints, `ShareAccessService` (may this recipient read this share?) and `ShareMapper`. |
| `asset` | Everything about a shared asset that does not depend on its format: `SharedDataObjectEntity` and its status, `SharedDataObjectStore` and `SharedDataObjectService`, `SharedAliases` (the alias rules), `SharedTableService` (which tables a share holds, expanding a shared schema), `AssetResolutionService` (the server's use of the catalog trait), `CredentialVendingService` and `TableMapper`. One subpackage per table format, each implementing `TableOperations`: `asset.delta` holds url access mode — `DeltaLogReader` over Delta Kernel, the response mapper and the `UrlSigner` implementations — and `asset.iceberg` holds the stub that says so. |
| `serving` | The recipient-facing protocol: `RecipientApi` (every route it serves), the share, schema and table discovery endpoints, credential vending, the four table read operations, the Iceberg REST catalog surface, and the `TableOperations` seam those read operations dispatch across. |
| `recipient` | `RecipientEntity`, `RecipientTokenEntity` and `AuthType`, `RecipientStore`, the provider-admin recipient and rotation endpoints, token minting, rotation and one-time activation, `IpAccessList`, and the filter and principal that authenticate a recipient request. |
| `catalog` | The `CatalogConnector` trait a new catalog implements and the types it exchanges, including `CatalogCaller`. Each implementation gets a subpackage: `catalog.local` is the file-backed one. |
| `protocol` | Every wire shape the spec defines, inbound and outbound, and nothing else: `Share`, `Schema`, `Table`, `TemporaryCredentials` and its request body, and the profile file. No behaviour, no dependencies. |
| `auth` | Admin authentication, bearer-token extraction and the token hashing every side shares. |
| `config` | Properties and bean wiring: which catalog, which filters, which argument resolvers, and how the two APIs are published as OpenAPI. |
| `http` | What both APIs put on the wire regardless of endpoint: the `{errorCode, message}` body with its code vocabulary and status mapping, the `{items, nextPageToken}` envelope with the paging machinery that fills it, `@AdminJson` (the snake_case marker), and the protocol content type. |

`serving` sits apart from the per-thing packages because the protocol is a single contract that cuts
across all of them. One request walks a share, a schema, a table and a format, and a recipient calling
`/metadata` neither knows nor should know which of those the answer came from. Kept together, the whole
recipient surface is one package to diff against `spec/protocols/`, the same reason `protocol` holds
the wire shapes; scattered by entity, the surface could only be reconstructed by reading four
controllers and hoping none was missed. The admin API stays with each thing it administers, where its
entity and store are.

`asset` is split by format rather than by asset type because the read operations of
`spec/protocols/TABLES.md` are defined for a table, whatever the bytes underneath are — so the endpoint
is format-neutral and dispatch happens behind `TableOperations`, one implementation per format. `SCHEMA`
is shareable but has no package of its own, because a schema is not served: it resolves to the tables it
holds, which `SharedTableService` does before anything reaches a format.

The root package holds the application class and the two types every feature package needs:
`BaseEntity`, the JPA id and timestamps, and `ObjectNames`, the name rules of
`spec/protocols/OVERVIEW.md` plus the lower-casing that makes lookups case-insensitive.

Two boundaries are deliberate. The spec's shapes stay together in `protocol`, separate from the
admin shapes in each feature package, because the two answer to different contracts: `Share` carries
exactly what `spec/protocols/SHARES.md` defines and cannot change without breaking recipients, while
`ShareResponse` is this server's own admin type and is free to grow. Keeping `protocol` dependency-
free also makes it the one package to diff against the spec. The `CatalogConnector` interface is the
seam that matters most: nothing outside `catalog` knows which connector is in use.

Permissions couple `share` and `recipient`, so the dependency runs both ways by nature. The permission
lives in `share`, since it is a property of the share; deleting a recipient calls into `ShareStore` to
revoke what it held, and deleting a share calls into `SharedDataObjectStore` to remove the objects
inside it. Those two cascades are the only cross-package writes.

### Design decisions worth knowing

- **Every admin write has an identity behind it.** A provider-admin call authenticates as a
  `Principal` and the server stamps `owner_id`, `created_by`, `updated_by`, `added_by` and `granted_by`
  from it. An endpoint that needs an owner takes a `Caller` parameter, so the owner is something the
  handler cannot forget rather than a lookup it might skip.
- **A share or a recipient may only be changed by the principal that owns it.** Reading is open to any
  principal, so an admin can see what a colleague shares; every write goes through `requireOwned` and is
  refused with `PERMISSION_DENIED` for anyone else. `Ownership` states the rule once, which is where
  group membership will widen it — until then a `GROUP` owns only in its own right.
- **Creating administrators is separated from being one.** The bootstrap token may only register
  principals, and only it may: neither credential can do the other's job, so a stolen admin token
  cannot mint itself accomplices, and a stolen bootstrap token cannot read or change any data.
- **Only a hash of a principal's token is stored**, which has a consequence worth stating: the server
  can query the catalog as the caller while their request is in flight, and never afterwards. So adding
  an object resolves as the admin — the catalog decides whether they may share it — while serving a
  recipient later resolves as the server itself.
- **A recipient sees an alias, not a catalog name.** An object stores the canonical `main.sales.orders`
  and the two-level `shared_as` alias, defaulting to the last two levels of the source name. Splitting
  that alias is what gives the protocol its schema level, so the two halves are stored normalized for
  case-insensitive lookups.
- **Schemas are derived, not stored.** A schema exists exactly as long as an object is shared under
  it, which keeps the stored schema to the entities the protocol actually needs to govern. A shared
  schema is the one row that names a schema directly, under a one-level alias, and it is why the name
  half of an alias is nullable.
- **A shared schema is a grant, not a copy.** Adding one writes a single row and no rows for the tables
  it covers: the catalog is what knows those, and it may answer differently an hour later. So they are
  asked for on every listing and assembled per request, which is the whole point — a table added to the
  schema is shared without anyone touching the share, and a dropped one stops being offered. The cost
  is a catalog call per listing, and holding a schema's tables in memory to page over them; a share
  with no schema grant keeps the database-paged query it always used.
- **A broken object stops being served instead of failing forever.** Re-resolution records
  `SOURCE_NOT_FOUND` or `PERMISSION_DENIED` on the object and recipient-facing queries filter to
  `ACTIVE`, so a dropped table leaves the listing while staying visible to the admin, and comes back on
  its own if the catalog resolves it again.
- **The object type is kept as a seam.** Everything stored and served carries an `AssetType`; `TABLE`
  and `SCHEMA` are accepted and the rest are refused explicitly, so adding volumes, skills or models
  later is additive.
- **Timestamps that mean "when the row appeared" are not duplicated.** `added_at` and `granted_at` are
  the row's `created_at`; a token's `superseded_at` and `revoked_at` are real columns, because they say
  something `created_at` cannot.
- **The database never holds a usable secret.** Issuing a recipient token only creates an
  activation link. The bearer token is minted when the recipient opens that link, returned once
  inside `config.share`, and stored only as a SHA-256 hash.
- **A token comes with the recipient, and only rotation replaces it.** There is no issue endpoint, so
  credentials cannot pile up by accident and a recipient can never exist without a way in. Tokens are
  still rows of their own keyed by recipient, which is what makes rotation safe: the superseded token
  stays usable for its grace window while the partner installs the new profile file. Any token that
  is still usable authenticates, and revoked and expired rows stay behind as the audit trail.
- **Storage locations are re-resolved before access is granted.** Listings are served from the stored
  snapshot; anything that vends credentials asks the catalog again, so a relocated or dropped table is
  never served from stale state.
- **An ungranted share is reported as missing, not forbidden**, so recipients cannot probe for the
  names of other tenants' shares. An IP access list is the opposite: it says `PERMISSION_DENIED`,
  because the recipient holds a valid token and the network is the thing being refused.
- **A Delta table can be read either way, and the server only offers what it can serve.** In `dir`
  access mode the recipient gets credentials and replays the log itself. In `url` mode the server
  replays the log and hands back one signed url per file, so a recipient holds no credential at all. A
  table is only advertised as `url` when this build can actually serve it, since the protocol lets a
  client pick a mode from that list and being sent down a dead end is worse than not being offered it.
- **The server never opens a data file.** Reading the log is the only time it touches storage, and it
  does so with the same credentials it would hand a recipient — never with a standing secret of its
  own, which it does not have.

## Running it

Requirements: Java 21 and Maven.

```bash
cd server
mvn install
mvn spring-boot:run \
  -Dspring-boot.run.arguments=--opensharing.admin.bootstrap-token=demo-bootstrap-token
```

The server listens on `http://localhost:8080` with an H2 file database under `server/data/` and the
sample catalog in `src/main/resources/local-catalog.yml`. Configure
`opensharing.admin.bootstrap-token`; with none set a random one is generated and logged at startup, so
it changes on every restart. That token registers principals and does nothing else, and it is the only
credential that may — every other admin call authenticates as a principal.

Then walk through the whole provider-to-recipient flow (requires `jq`):

```bash
./scripts/demo.sh
```

## API surface

### Protocol endpoints (recipient, `Authorization: Bearer <token>`)

Mounted under `opensharing.protocol-prefix`, default `/open-sharing`. Every route below is declared in
`RecipientApi` and served from the `serving` package.

```
GET  /shares
GET  /shares/{share}
GET  /shares/{share}/schemas
GET  /shares/{share}/all-tables
GET  /shares/{share}/schemas/{schema}/tables
POST /shares/{share}/schemas/{schema}/tables/{table}/temporary-table-credentials
GET  /shares/{share}/schemas/{schema}/tables/{table}/version
GET  /shares/{share}/schemas/{schema}/tables/{table}/metadata
POST /shares/{share}/schemas/{schema}/tables/{table}/query
GET  /shares/{share}/schemas/{schema}/tables/{table}/changes

GET  /iceberg/v1/config?warehouse={share}
GET  /iceberg/v1/shares/{share}/namespaces
GET  /iceberg/v1/shares/{share}/namespaces/{namespace}
GET  /iceberg/v1/shares/{share}/namespaces/{namespace}/tables
GET  /iceberg/v1/shares/{share}/namespaces/{namespace}/tables/{table}
POST /iceberg/v1/shares/{share}/namespaces/{namespace}/tables/{table}/metrics
```

Everything the spec defines for shares, schemas and tables is served, in both access modes.

`version`, `metadata`, `query` and `changes` belong to a table rather than to a format, so the endpoint
resolves the table and then asks whichever `TableOperations` implementation serves its format. A Delta
table is answered in full; an Iceberg or bare-parquet table is answered `NOT_IMPLEMENTED` with the route
that does work, since a recipient asking about a table they hold has asked a reasonable question this
build cannot yet answer. Everything below describes the Delta implementation, the only one there is.

These four are url access mode. They read the table's Delta log and answer in newline-delimited JSON,
one action per line, in the protocol's parquet response format —
`Delta-Table-Version` on every response, `protocol` and `metaData` lines, and for `query` a `file` line
per data file carrying a signed url and its expiry. A client can send `delta-sharing-capabilities` to
say what it accepts; asking for the delta response format alone gets `NOT_IMPLEMENTED`, since only
parquet format is produced, and asking for `includeEndStreamAction=true` adds the closing line.

`changes` answers with the table's history over a window rather than its state: an `add`, `cdf` or
`remove` line per change file, each carrying the version and commit timestamp a streaming reader
tracks. Either end of the window can be named by version or by timestamp — `startingVersion` and
`startingTimestamp` are mutually exclusive, as are the two ending forms — and a timestamp resolves the
way the spec asks, the start forward to the first version at or after it and the end back to the last
version at or before it. Omitting the window reads from the table's first version to its latest. The
`cdf` lines are the before-and-after an update recorded, so they only appear for a window of commits a
writer made with `delta.enableChangeDataFeed` on; a window without them still reports its adds and
removes. The metadata sent is the ending version's, so `includeHistoricalMetadata=true` — which asks
for each schema the window passed through — answers `NOT_IMPLEMENTED` rather than quietly sending one.

The Iceberg catalog operations are mounted and authorized like the rest but answer `NOT_IMPLEMENTED`
until the REST catalog is built. Only the `/v1/config` handshake is served, so a client can discover
the server and learn that it addresses a share as the path prefix `shares/{share}`; that path is where
the profile file's `icebergEndpoint` points. The volume, skill and model endpoints of the spec are not
served yet.

One path is not in the spec as written: `temporary-table-credentials` follows the naming of
`temporary-volume-credentials` in VOLUMES.md, because TABLES.md links to a
`GenerateTemporaryTableCredential` section that the document does not contain. The four Delta
operations come from the Delta Sharing protocol, which `TABLES.md` defers to for Delta access. There is
no `GET .../tables/{table}`: the protocol has no such endpoint, and a table's details come from a
listing or from `metadata`.

All list endpoints accept `maxResults` and `pageToken` and return `{items, nextPageToken}`.

### Provider-admin endpoints (`Authorization: Bearer <principal's token>`)

Mounted under `opensharing.admin.base-path`, default `/api/admin/v1`. Bodies are snake_case. Any
principal may read; a `PATCH`, `DELETE` or `rotate-token` on a share or a recipient is refused unless
the caller owns it.

```
POST   /principals                                  register a principal (bootstrap token only)
GET    /principals                                  list principals
GET    /principals/{name}                           get a principal
PATCH  /principals/{name}                           rename it or replace its bearer token
DELETE /principals/{name}                           delete it, if it owns nothing
POST   /shares                                      create a share
GET    /shares                                      list shares
GET    /shares/{name}                               get a share with its objects
PATCH  /shares/{name}                               update metadata, add or remove objects
DELETE /shares/{name}                               delete a share, its objects and its permissions
GET    /shares/{name}/permissions                   list who may read the share
PATCH  /shares/{name}/permissions                   grant or revoke privileges
POST   /recipients                                  create a recipient with its first token
GET    /recipients                                  list recipients
GET    /recipients/{name}                           get a recipient with its token history
PATCH  /recipients/{name}                           update the IP access list or properties
DELETE /recipients/{name}                           delete a recipient, its tokens and its grants
POST   /recipients/{name}/rotate-token              replace the token, returns an activation URL
GET    /recipients/{name}/share-permissions         list the shares granted to the recipient
```

**Registering a principal** is what the bootstrap administrator token is for, and the two directions of
that are both enforced: it is the only call the bootstrap token may make, and it is the only credential
that may make the call. A registered principal asking to register another is answered
`403 PERMISSION_DENIED`, so creating administrators stays an operator action rather than something an
admin credential can do on its own. Everything else needs a principal's own token, because everything
else records who did it:

```bash
curl -X POST "$ADMIN/principals" \
  -H "Authorization: Bearer $BOOTSTRAP_TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"USER","id":"941a703c-ff3c-4d6f-8fb8-0e5aca154ed4",
       "name":"alice@example.com","bearer_token":"caller-generated-secret"}'
```

The plaintext is never stored, returned or logged. `id` may be given so a principal keeps the id an
external directory already uses; it must be a UUID and one that is free, and leaving it out has the
server generate one. This is the only object whose id a caller chooses — a share's and a recipient's
are the server's to assign, as the spec has them.

Deleting a principal is refused while anything it owns, authored or granted still exists, and the
refusal counts what is holding it, because an audit trail naming nobody is worse than one naming
someone who has left.

**Adding a table** names the source in the catalog and, optionally, the alias recipients will see:

```bash
curl -X PATCH "$ADMIN/shares/sales_share" \
  -H "Authorization: Bearer caller-generated-secret" -H 'Content-Type: application/json' \
  -d '{"updates":[{"action":"ADD","data_object":
        {"name":"main.sales.orders","type":"TABLE","shared_as":"sales.orders"}}]}'
```

The server resolves the source in the catalog **as the calling principal**, so the catalog decides
whether they may share it, and records the source's asset id, subtype and format. `type` defaults to
`TABLE`; `SCHEMA` is the other accepted value and anything else is refused for now. `shared_as`
defaults to the last two levels of the source name, so `main.sales.orders` is served as schema `sales`,
table `orders`. `REMOVE` takes the same `data_object`, matched on either the source name or the alias.
Updates apply in order and the whole `PATCH` is one transaction, so a rejected object leaves the share
untouched.

**Adding a schema** shares every table in it, including the ones added to it after this call:

```bash
curl -X PATCH "$ADMIN/shares/sales_share" \
  -H "Authorization: Bearer caller-generated-secret" -H 'Content-Type: application/json' \
  -d '{"updates":[{"action":"ADD","data_object":
        {"name":"main.hr","type":"SCHEMA","shared_as":"hr"}}]}'
```

A schema's alias is one level, since the schema is itself the level a recipient sees, and it defaults
to the last level of the source name. Nothing is written for the tables it covers: they are read from
the catalog whenever a recipient lists or reads one, so the share stays in step with the schema by
never having its own copy of what is in it. The trade is that the provider gives up naming them — a
table appears under the name the catalog gives it — and that the catalog must be able to enumerate,
which is checked here rather than left for a recipient to discover.

Two schemas cannot be shared under one alias, since a recipient's table would then be ambiguous. A
table shared in its own right may sit in a shared schema's alias, and wins over the schema's own table
of that name, which is how a table from elsewhere joins the schema a recipient sees. Removing the grant
takes the one-level alias, or the source name.

**Granting** resolves both names to their ids and stamps `granted_by`:

```bash
curl -X PATCH "$ADMIN/shares/sales_share/permissions" \
  -H "Authorization: Bearer caller-generated-secret" -H 'Content-Type: application/json' \
  -d '{"changes":[{"recipient_name":"partner_acme","add":["SELECT"]}]}'
```

`SELECT` is the only privilege today, and it is what the protocol endpoints check. `remove` takes the
same list, and both are idempotent.

**A token comes with the recipient.** There is no endpoint that issues one on its own, so a recipient
always has a way in and credentials cannot pile up by accident. The create response is the only place
the activation URL appears:

```json
{"recipient": {"recipient_id": "...", "name": "partner_acme", ...},
 "token": {"token_id": "...", "activation_url": "https://.../activation/...", "expires_at": "..."}}
```

Replacing it is a rotation. The superseded token keeps working for `existing_token_expire_in_seconds`
(default `recipient-tokens.rotation-grace`, `0` to cut it off at once), which is the window the
recipient has to install the new `config.share`; the row keeps a `superseded_at` so the history shows
what happened. An activation link that was never opened is invalidated instead, since nobody is
holding the token behind it.

```bash
curl -X POST "$ADMIN/recipients/partner_acme/rotate-token" \
  -H "Authorization: Bearer caller-generated-secret" -H 'Content-Type: application/json' \
  -d '{"existing_token_expire_in_seconds":86400}'
```

Rotating with `0` is also how a compromised token is killed: it is revoked at once and a replacement
is waiting.

### Activation (unauthenticated, single use)

```
GET /activation/{nonce}    ->  config.share profile file
```

### The API described (unauthenticated)

```
GET /v3/api-docs/protocol  ->  OpenAPI for the recipient protocol
GET /v3/api-docs/admin     ->  OpenAPI for the provider-admin API
GET /swagger-ui.html       ->  both, browsable
```

The documents are read out of the controllers and the records they return, so they cannot promise
something the server does not serve. They exist for the other side of the wire: point a generator at
one and get a typed client instead of hand-writing calls. Note that `spec/protocols/` remains the
normative definition of the protocol — this describes one server's rendering of it.

The two APIs are separate documents because they answer to different contracts and even spell their
fields differently: the protocol is camelCase and fixed by the spec, the admin API snake_case and
this server's own. Neither is behind authentication, since the filters cover only the two API
prefixes; what a server accepts is not a secret, but a deployment that disagrees can set
`springdoc.api-docs.enabled=false`.

One thing the document says poorly: the four table read operations answer with newline-delimited
JSON, a stream of actions rather than one object, so their bodies appear there as text. What a line
holds is described under the protocol endpoints above.

## Configuration

Everything lives under the `opensharing` prefix in `src/main/resources/application.yml`. Any key can be
overridden without editing the file, either as `--opensharing.admin.base-path=/admin` on the command
line or as the upper-case underscored form of the same path in the environment, so
`opensharing.admin.bootstrap-token` is `OPENSHARING_ADMIN_BOOTSTRAP_TOKEN`.

| Key | Default | Purpose |
|---|---|---|
| `protocol-prefix` | `/open-sharing` | Prefix for protocol endpoints; also what goes in the profile file. |
| `admin.base-path` | `/api/admin/v1` | Prefix for the provider-admin API. |
| `admin.bootstrap-token` | generated | The only credential that may `POST /principals`, and its only privilege. Set it; a generated one changes each restart. |
| `activation.base-path` | `/activation` | Prefix the single-use activation endpoint is served under. |
| `activation.external-base-url` | `http://localhost:8080` | Public base URL used to build activation URLs and the profile endpoint. |
| `activation.ttl` | `72h` | How long an unused activation link stays valid. |
| `recipient-tokens.default-ttl` | `90d` | Token lifetime when none is given; blank means never expires. |
| `recipient-tokens.rotation-grace` | `24h` | How long a superseded token keeps working when a rotation does not say. |
| `asset-credentials.ttl` | `1h` | Lifetime requested from the catalog for storage credentials. |
| `pagination.default-max-results` / `max-max-results` | `500` / `1000` | List page sizes. |
| `delta.url-access-enabled` | `true` | Read Delta logs and serve `version`, `metadata` and `query`. Off leaves `dir` mode alone and stops `url` being advertised. |
| `delta.url-ttl` | `1h` | Lifetime of a signed file url, capped by the credentials it was signed with. |
| `delta.s3-region` | `us-east-1` | Region used to sign S3 urls when the catalog's credentials do not name one. |
| `catalog.type` | `local` | Only `local` is shipped. |
| `catalog.local.file` | `classpath:local-catalog.yml` | Spring resource location of the catalog file. |

### The catalog trait

`CatalogConnector` is the whole seam between the server and a system of record for assets. Two methods
are required, because Unity Catalog, Polaris and Iceberg REST all answer the same two questions and the
server asks nothing else of a connector that shares assets one at a time:

```java
public interface CatalogConnector {
  String name();

  ResolvedAsset resolveAsset(AssetLookup lookup, CatalogCaller caller, AssetAction intent);
  List<StorageCredentials> getStorageCredentials(CredentialRequest request);

  // Optional. Required only to share a whole schema.
  default List<ResolvedAsset> listChildren(AssetLookup parent, CatalogCaller caller) { ... }
}
```

`resolveAsset` answers where an asset lives, how it can be read, what subtype and format it is, and —
for formats that need one — the `metadataLocation` a client uses to interpret the bytes, plus the
`schema` if the catalog states one. It is called when an admin adds the object (so a share can never
point at something that does not exist) and again before every vend (so a relocated asset is never
served from a stale snapshot). `caller` carries the name and bearer token of the principal on whose
behalf the question is asked, or `CatalogCaller.server()` when the server asks on its own to serve a
recipient, and `intent` says whether the asset is about to be shared or read, since permission to do
one is not permission to do the other. A connector that denies the caller throws
`AssetAccessDeniedException`, which the admin API reports as `PERMISSION_DENIED` and serving records on
the object's status.

Resolution is also the existence check. There is no separate `exists`, because every caller that wants
to know whether an asset exists needs the metadata that proves it, so the cheaper call could never be
used. Nor is there a separate `authorize`: the caller and the intent are already arguments here, and an
exception says more than a boolean would.

`getStorageCredentials` returns a prefix, a cloud provider, an opaque credential map and an expiry —
never a long-lived secret the server would have to keep. It returns a list because Iceberg's
`LoadCredentialsResponse` does: an asset may span prefixes, and `AssetResolutionService` picks the
narrowest entry covering the location being read, which is what the Iceberg spec asks of clients and
keeps a bucket-wide grant from shadowing a table-specific one. Its argument is a `CredentialRequest`
rather than a location, because a catalog may vend by asset id instead of by path — Unity Catalog's
`POST /temporary-table-credentials` takes a `table_id` — and because the asset type selects the
endpoint on catalogs that vend volumes separately from tables.

`listChildren` is the one optional method, and exists for exactly one caller: a shared schema, whose
tables have to come from somewhere and are not stored here. It defaults to refusing, so a catalog that
cannot enumerate stays a perfectly good catalog for sharing assets one at a time — and adding a schema
to a share is then rejected while the provider is still on the phone, rather than at the first listing
by a recipient. It answers with full `ResolvedAsset`s rather than bare identifiers because the catalogs
worth plugging in already carry that in their list response — Unity Catalog's `GET /tables` returns each
table's columns and location — so asking for identifiers alone would throw it away and buy it back one
request per child.

Provider-side browsing beyond that is deliberately absent. Recipient-facing listings of *shared*
objects are served from the metadata store, so nothing else needs to walk the catalog.

### The trivial implementation

`LocalCatalogConnector` implements that trait against a declarative file, so there is nothing to
authenticate to and nothing to deploy. Point `catalog.local.file` at a YAML file describing tables:

```yaml
credentials:
  provider: AWS        # AWS | AZURE | GCP | R2
  mode: FAKE           # FAKE generates placeholders; STATIC returns credentials.values verbatim
assets:
  - identifier: main.hr
    type: SCHEMA         # shareable as a whole; holds every main.hr.* table below
  - identifier: main.sales.orders
    subtype: MANAGED     # optional; recorded on the shared object as source_subtype
    storageLocation: s3://acme-lake/sales/orders/
    format: delta        # delta | iceberg | parquet
    metadataLocation:    # optional; what a client needs to interpret the bytes
      s3://acme-lake/sales/orders/metadata/v3.metadata.json
    schema: '{"type":"struct","fields":[]}'   # optional; as the catalog states it
    auxiliaryLocations:  # optional extra locations credentials may be scoped to
      - s3://acme-overflow/sales/orders/
    sharableBy:          # optional; omit and any principal may share it
      - alice@example.com
```

Identifiers are matched case-insensitively and `type` defaults to `TABLE`. A `SCHEMA` entry needs no
location of its own and holds whatever names it: `main.hr` contains `main.hr.employees`, which is all
the hierarchy a flat file needs for a schema to be shared as a whole. `sharableBy` is how the file
stands in for a real catalog's permission model: a principal outside the list is refused when adding
the object, which is the check `resolveAsset` exists to make. It bears on sharing alone, so an object
already in a share keeps serving — those resolutions arrive as the server, with `READ` intent. Unknown
keys, an unsupported
`format` and an unsupported `accessModes` entry all fail the file at startup, so a typo cannot silently
drop a table or leave one that only breaks when a recipient reaches it. `mode: FAKE` logs a warning at
startup, because the credentials it hands out open nothing.

### A real catalog

Add a subpackage under `catalog` — `catalog.unity` next to `catalog.local` — implement
`CatalogConnector` there and contribute it as a `@Bean`. `CatalogConfiguration` declares its default
`@ConditionalOnMissingBean`, so yours takes over with no other change to the server, and that class
is the only one outside `catalog` that ever names an implementation.

### Databases

H2 by default. Both other drivers ship in the jar, and switching is configuration only — the URL is
enough, since the driver is derived from it and Hibernate detects the dialect from the connection:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="\
  --spring.datasource.url=jdbc:postgresql://localhost:5432/opensharing \
  --spring.datasource.username=opensharing \
  --spring.datasource.password=secret"
```

The entities are written to keep that true. Every unique key stays inside InnoDB's 3072-byte index
limit under utf8mb4 — the widest is `(share_id, name_lower)` at 2192 bytes, which is why a catalog name
is capped at 512 characters — and case-insensitive lookups go through explicit `*_lower` columns rather
than relying on collation, so MySQL's case-insensitive default and Postgres's case-sensitive one behave
identically.

Schema management is `hibernate.ddl-auto=update`, which suits a prototype; a real deployment should
use versioned migrations.

## Tests

```bash
mvn test
```

The server tests boot the whole application against an in-memory database and the file-backed catalog,
then drive it exactly as a provider admin and a recipient client would, following the flow above:
register Alice with the bootstrap token, create a share as her, add tables under aliases, create a
recipient, grant `SELECT`, activate the token minted with the recipient, discover the tables, vend
credentials, and rotate the token with and without a grace window. Sharing a schema gets the same
treatment, from the other end: nothing is added under a name, and the recipient still lists the tables,
pages through them, and vends credentials for one — plus the cases only a grant has, an alias claimed
twice, a table the schema does not hold, and a table shared in its own right winning over the schema's.
They also confirm what must be
refused: the bootstrap token creating anything it cannot own, a principal who may not share a table, a
principal writing to a share or a recipient it does not own, a principal who still owns something being
deleted, duplicate names in either case, an unsupported object
type, a source the catalog does not know, missing, invalid, superseded and expired tokens, a stale
activation link, a request from outside the IP access list, ungranted shares, and unknown schemas and
tables. Unit tests cover the name rules, the token predicates, the catalog file and the status
transitions a broken source causes.

The published OpenAPI is tested as what it is, a contract a client is generated from: that the two
APIs stay separate documents, that each is described in the spelling it is served in, that the caller
the token identifies is not asked for as a parameter, and that a streamed response names its media
type once.

## Not implemented yet

- Volumes, agent skills and models. The protocol defines them and `AssetType` names them; only tables
  can be shared, whether directly or through the schema holding them.
- Any real catalog. `CatalogConnector` is the seam a Unity Catalog, Polaris or Iceberg REST
  implementation drops into; only the file-backed one exists today.
- `auth_type: OIDC` on a recipient. It is accepted and stored, but only `TOKEN` is authenticated.
- Group membership. `PrincipalType.GROUP` exists so a share can be owned by a team, and nothing
  resolves a user to the groups it belongs to yet.
- The delta response format, and with it any table whose reader version is above 1 — deletion vectors
  and column mapping among them. Such a table is refused rather than flattened into a parquet-format
  response a client would misread.
- A table whose log names files outside its own directory, which a shallow clone writes as absolute
  paths. Signing one would hand a recipient bytes from a location nobody shared, so url access mode
  refuses the table; `dir` access mode still serves what is genuinely under the shared root.
- Narrowing the metadata a recipient sees. The log's `schemaString` and the whole table
  `configuration` are passed through, so column metadata and every table property travel with them.
  The OSS server strips column metadata down to comments and reduces the configuration to
  `enableChangeDataFeed`, and this should do the same.
- Reading a Delta log on S3, Azure or GCS out of the box. The credential mapping is there, but Kernel
  reaches storage through Hadoop, so `hadoop-aws` or `hadoop-azure` has to be on the classpath;
  neither is shipped, because their AWS SDK bundles dwarf the rest of the server. A table on such
  storage says so, and `dir` access mode works regardless. GCS also needs a signing key to presign,
  which credential vending does not provide.
- Filtering by `predicateHints`, `jsonPredicateHints` or `limitHint`, and paginating a query. All are
  best-effort in the protocol, so every file of the version asked for is returned.
- Asynchronous queries and the query-info endpoint they need.
- The five Iceberg REST catalog operations. The `/v1/config` handshake at `icebergEndpoint` is served;
  `loadTable` can now be built, since `ResolvedAsset` carries `metadataLocation` and `schema`.
- Outbound connection config for a real catalog: an endpoint and an auth mode (OAUTH, BEARER, SIGV4)
  per connector. `CatalogCaller` carries the calling principal's token, but nothing models how to reach
  and authenticate to the catalog itself.
- Provider-side catalog browsing. `listChildren` exists on the SPI for expanding a shared schema, but
  no admin endpoint exposes it, so a provider cannot list a catalog before deciding what to share.
- `Agent` and `Page` asset types, which are still community proposals.
- Audit logging of credential vending, and rate limiting.
