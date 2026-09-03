# Embedding OpenSharing in a host

OpenSharing can run as a **standalone** Spring Boot server or be **embedded** inside another
process such as [Unity Catalog OSS](https://www.unitycatalog.io/). Embedded mode exists so operators
do not run two services, duplicate provider configuration, or round-trip catalog calls over HTTP when
the catalog is already in the same JVM.

## Maven artifacts

| Artifact | Use |
|----------|-----|
| `io.opensharing:opensharing-server-core` | Library for UC embed — `OpenSharing.embedded()`, protocol serving, JPA metadata store |
| `io.opensharing:opensharing-server` | Runnable distribution (`-exec` classifier is the fat jar) |
| `io.opensharing:opensharing-server:exec` | Same as `opensharing-server-*-exec.jar` for `java -jar` |

Standalone HTTP catalog connectors (`local`, `unity`) live in `opensharing-server`, not in core.

## Publish locally for UC testing

From `server/`:

```bash
mvn install
# On machines that cannot reach Maven Central directly:
mvn -s .mvn/local-mirror-settings.xml install
```

This installs into `~/.m2/repository`:

```
io/opensharing/opensharing-server-core/0.1.0-SNAPSHOT/opensharing-server-core-0.1.0-SNAPSHOT.jar
io/opensharing/opensharing-server/0.1.0-SNAPSHOT/opensharing-server-0.1.0-SNAPSHOT-exec.jar
```

UC depends on **core only** (plain jar, not `-exec`):

```scala
// unitycatalog/build.sbt — server project libraryDependencies
"io.opensharing" % "opensharing-server-core" % "0.1.0-SNAPSHOT"
```

Unity Catalog already adds `Resolver.mavenLocal`, so `mvn install` in OpenSharing is enough before
`publishLocal` / `sbt server/compile` in UC.

After changing OpenSharing, reinstall and rebuild UC:

```bash
cd ~/OpenSharing/server && mvn install -DskipTests
cd ~/unitycatalog && sbt "server/compile"
```

## Modes

| Mode | How to start | Catalog | Provider identity |
|------|----------------|---------|-------------------|
| `standalone` | `OpenSharing.runStandalone(args)` or `OpenSharingServer.main` | `opensharing.catalog.*` (`local` file or `unity` HTTP) | `opensharing.admin.principals` provisioned at startup |
| `embedded` | `OpenSharing.embedded()...run(args)` or Spring with `opensharing.hosting.mode=embedded` | Host registers a `CatalogConnector` bean | Host may register a `ProviderIdentityResolver` |

Set `opensharing.hosting.mode` in configuration (`standalone` by default).

## Standalone (current demo)

```bash
# Unity Catalog on :8080, OpenSharing on :8099 — two processes
java -jar opensharing-server-0.1.0-SNAPSHOT-exec.jar \
  --opensharing.hosting.mode=standalone \
  --opensharing.catalog.type=unity \
  --opensharing.catalog.unity.uri=http://localhost:8080/api/2.1/unity-catalog \
  --opensharing.admin.principals[0].name=admin@example.com \
  --opensharing.admin.principals[0].bearer-token=$UC_TOKEN
```

Or from source:

```bash
cd server
mvn -pl opensharing-server spring-boot:run -Dspring-boot.run.arguments="--server.port=8099 ..."
```

## Embedded in Unity Catalog OSS (target)

The host supplies two integration points:

1. **`CatalogConnector`** — in-process adapter that calls UC repositories and
   `StorageCredentialVendor` instead of `UnityCatalogConnector` HTTP.
2. **`ProviderIdentityResolver`** (optional) — maps the current UC-authenticated principal to a
   `Caller` for provider-admin APIs, replacing `opensharing.admin.principals`.

```java
OpenSharing.embedded()
    .catalog(new UnityCatalogEmbeddedConnector(ucRepositories, credentialVendor))
    .identityResolver(request -> Optional.of(ucPrincipalToCaller(ucContext)))
  .property("opensharing.protocol-prefix", "/api/2.1/opensharing")
  .run();
```

Recipient protocol endpoints, share metadata (JPA), credential vending, and Delta/Iceberg serving
stay in OpenSharing. Only catalog access and provider authentication are delegated to the host.

## One process, one address

Embedding does not mean sharing a port automatically — UC's own gRPC-JSON transcoding server
(Armeria) and OpenSharing's embedded Tomcat are unrelated server engines that each bind their own
socket, and a TCP port can only have one listener regardless of how many frameworks share a JVM.
What makes it feel like one server to a client is UC's own public-facing listener (the Vert.x
`URLTranscoderVerticle` that already sits in front of Armeria) forwarding a request to whichever
backend its path belongs to:

```
client → UC's public port (e.g. 8080)
              │
              ├─ path under opensharing.protocol-prefix / provider-base-path / activation-base-path
              │      → embedded OpenSharing, on its own port, bound to 127.0.0.1
              │
              └─ everything else
                     → UC's own Armeria server, on port+1
```

OpenSharing's own port is real (something has to bind it) but private: bound to `127.0.0.1` only,
never advertised, reached solely by that one path-based forward on the same host. A client — and a
recipient's activation URL / `config.share` — only ever sees UC's own public address, because
`opensharing.activation.external-base-url` is set to that address, not to OpenSharing's internal
port. Standalone OpenSharing (no UC in the loop) is unaffected: this routing is UC's own addition
to its already-existing transcoder, not a change to OpenSharing itself.

## No datasource config of its own — it reads the host's

OpenSharing's metadata (`os_principals`, `os_shares`, `os_shared_data_objects`, `os_recipients`,
`os_recipient_tokens`, `os_share_permissions`, all prefixed `os_` so they never collide with a
host's own tables) is stored via its own JPA/Hibernate model, independent of the host's schema. In
embedded mode there is no `server.opensharing.datasource.url` (or equivalent) to set: the host is
expected to hand OpenSharing whichever JDBC connection it already uses for its own metadata —
`spring.datasource.url`/`username`/`password`/`driver-class-name` — and both sides' tables land in
one physical database. For UC, `OpenSharingLifecycle` reads this straight out of the
`HibernateConfigurator` UC already built for itself (`hibernate.connection.url` →
`spring.datasource.url`, etc.), so there is nothing for an operator to duplicate or keep in sync;
whatever database UC points at (H2 file, Postgres, MySQL) is what OpenSharing follows.

This is safe within a single JVM: H2 keeps one shared in-memory `Database` instance per canonical
file path per process, and a real database server is designed for exactly this kind of sharing.

Two things worth knowing about it:

- **Matching credentials, before the first connect.** H2 creates its database's admin user from
  whichever username/password the *first* connection presents. If the host's Hibernate config
  doesn't set `hibernate.connection.username`/`password` explicitly (UC's own demo config doesn't),
  that first connection uses an empty username — so the mapping has to carry that through as an
  explicit empty string, not silently fall back to OpenSharing's own `application.yml` default of
  `sa`, or the second connection pool fails with `Wrong user name or password`.
- **Two connection pools, not one transaction.** This shares a database file, not a Hibernate
  `SessionFactory`/`EntityManagerFactory` or a JDBC connection. A single logical operation that
  touches both the host's tables and OpenSharing's (e.g. resolving a table while creating a share)
  still runs as two independent local transactions — there is no 2PC/XA coordination between them.
  Acceptable for a single-node deployment; not a substitute for real distributed transactions if
  that ever matters.

## What the host implements

### `CatalogConnector`

Already the main seam (`catalog/CatalogConnector.java`). For UC embed, add something like:

```java
public final class UnityCatalogEmbeddedConnector implements CatalogConnector {
  // resolveAsset → TableRepository + UC authorization
  // getStorageCredentials → StorageCredentialVendor.vendCredential(...)
  // listChildren → schema table listing
}
```

No `opensharing.catalog.unity.uri` and no duplicated bearer token: the connector uses the UC
request context or explicit `CatalogCaller` name only where UC RBAC requires a principal identity.

### `ProviderIdentityResolver`

```java
@FunctionalInterface
public interface ProviderIdentityResolver {
  Optional<Caller> resolve(HttpServletRequest request);
}
```

When present, `AdminAuthenticationFilter` uses it before falling back to configured principals.

## Spring wiring

Beans gated on hosting mode:

| Bean | Standalone | Embedded |
|------|------------|----------|
| `CatalogConfiguration` (auto connector) | yes | no — host supplies `CatalogConnector` |
| `PrincipalProvisioner` | yes | no |
| `SharingRuntime` | yes | yes |
| `EmbeddedStartupValidator` | no | yes |

Inspect runtime mode: inject `SharingRuntime` and call `hostingMode()`.

## Demo (embedded UC)

From `server/` after UC integration is built in a sibling `unitycatalog` checkout:

```bash
# terminal A — build, configure, start UC with embedded OpenSharing
UC_ROOT=~/unitycatalog ./scripts/demo-embedded-up.sh

# terminal B — walkthrough (share, recipient, protocol)
source ~/.opensharing-embedded-demo/demo.env
./scripts/demo-embedded.sh
# or step through while recording:
PAUSE=ask ./scripts/demo-embedded.sh
```

`demo-embedded-up.sh` runs `mvn install` for `opensharing-server-core`, then
`sbt serverEmbedded/exportEmbeddedClasspath` in UC. Unity Catalog listens on `UC_PORT` (default
8080), and embedded OpenSharing's admin and protocol APIs answer there too — see "One process, one
address" above. `OS_INTERNAL_PORT` (default 8099) is OpenSharing's own port, bound to `127.0.0.1`
and not meant to be reached directly.

For the two-process demo (released UC jar + standalone OpenSharing), use `demo-unity-up.sh` and
`demo.sh` instead.

## Roadmap

`UnityCatalogEmbeddedConnector` and UC startup wiring live in the UC repository (`server-sharing`
module). This repo ships the library artifact (`opensharing-server-core`) and the standalone
distribution (`opensharing-server`).
