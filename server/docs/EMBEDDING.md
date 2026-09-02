# Embedding OpenSharing in a host

OpenSharing can run as a **standalone** Spring Boot server or be **embedded** inside another
process such as [Unity Catalog OSS](https://www.unitycatalog.io/). Embedded mode exists so operators
do not run two services, duplicate provider configuration, or round-trip catalog calls over HTTP when
the catalog is already in the same JVM.

## Modes

| Mode | How to start | Catalog | Provider identity |
|------|----------------|---------|-------------------|
| `standalone` | `OpenSharing.runStandalone(args)` or `OpenSharingServer.main` | `opensharing.catalog.*` (`local` file or `unity` HTTP) | `opensharing.admin.principals` provisioned at startup |
| `embedded` | `OpenSharing.embedded()...run(args)` or Spring with `opensharing.hosting.mode=embedded` | Host registers a `CatalogConnector` bean | Host may register a `ProviderIdentityResolver` |

Set `opensharing.hosting.mode` in configuration (`standalone` by default).

## Standalone (current demo)

```bash
# Unity Catalog on :8080, OpenSharing on :8099 — two processes
java -jar opensharing-server.jar \
  --opensharing.hosting.mode=standalone \
  --opensharing.catalog.type=unity \
  --opensharing.catalog.unity.uri=http://localhost:8080/api/2.1/unity-catalog \
  --opensharing.admin.principals[0].name=admin@example.com \
  --opensharing.admin.principals[0].bearer-token=$UC_TOKEN
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
  .property("opensharing.protocol-prefix", "/api/2.1/unity-catalog/sharing")
  .run();
```

Recipient protocol endpoints, share metadata (JPA), credential vending, and Delta/Iceberg serving
stay in OpenSharing. Only catalog access and provider authentication are delegated to the host.

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

## Roadmap

This change introduces the **runtime API** (`io.opensharing.runtime`) and hosting-mode wiring. A
follow-up splits Maven modules (`opensharing-api`, `opensharing-runtime`, `opensharing-server`) and
adds `UnityCatalogEmbeddedConnector` in the UC repository.
