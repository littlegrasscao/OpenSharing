#!/usr/bin/env bash
# Brings up Unity Catalog OSS with OpenSharing embedded in the same JVM.
#
# Run this once before recording or walking through ./scripts/demo-embedded.sh.
#
# Environment:
#   DEMO_HOME        runtime data and config               (default ~/.opensharing-embedded-demo)
#   UC_ROOT          path to a unitycatalog checkout       (default ~/unitycatalog)
#   UC_PORT          public Unity Catalog port             (default 8080)
#   OS_PORT          embedded OpenSharing port             (default 8099)
#   MVN_SETTINGS     Maven settings for OpenSharing build  (default server/.mvn/local-mirror-settings.xml when present)
#   MAVEN_PROXY_URL  Maven mirror for UC sbt               (default Databricks proxy when unset)
set -euo pipefail

DEMO_HOME="${DEMO_HOME:-$HOME/.opensharing-embedded-demo}"
UC_ROOT="${UC_ROOT:-$HOME/unitycatalog}"
UC_PORT="${UC_PORT:-8080}"
OS_PORT="${OS_PORT:-8099}"
UC_URI="http://localhost:$UC_PORT/api/2.1/unity-catalog"
SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$SERVER_DIR/opensharing-server-core/src/test/resources/delta-table/stocked"
CREDENTIAL_KEY="${CREDENTIAL_KEY:-b3BlbnNoYXJpbmctZGVtby1rZXktMzItYnl0ZXMhISE=}"
PROVIDER="${PROVIDER:-admin@unitycatalog.local}"

step() { printf '\n\033[1;36m== %s\033[0m\n' "$1"; }
note() { printf '   %s\n' "$1"; }

detached() { # pid-file command...
  python3 -c '
import os, sys
pid_file, argv = sys.argv[1], sys.argv[2:]
os.setsid()
with open(pid_file, "w") as f:
    f.write(str(os.getpid()) + "\n")
os.execvp(argv[0], argv)
' "$@"
}

embedded_classpath() {
  local raw="$UC_ROOT/server-embedded/target/classpath"
  [[ -s "$raw" ]] || {
    echo "Missing $raw; run the UC build step below first." >&2
    exit 1
  }
  tr ':' '\n' < "$raw" | rg -v 'log4j-to-slf4j|logback-|jul-to-slf4j' | paste -sd: -
}

if [[ -z "${MVN_SETTINGS:-}" && -f "$SERVER_DIR/.mvn/local-mirror-settings.xml" ]]; then
  MVN_SETTINGS="$SERVER_DIR/.mvn/local-mirror-settings.xml"
fi
MVN_CMD=(mvn -q)
if [[ -n "${MVN_SETTINGS:-}" ]]; then
  MVN_CMD+=(-s "$MVN_SETTINGS")
fi

if [[ -z "${MAVEN_PROXY_URL:-}" ]]; then
  export MAVEN_PROXY_URL=https://maven-proxy.dev.databricks.com
fi

step "Installing OpenSharing server-core into ~/.m2"
( cd "$SERVER_DIR" && "${MVN_CMD[@]}" install -DskipTests -pl opensharing-server-core )
note "io.opensharing:opensharing-server-core:0.1.0-SNAPSHOT"

step "Building Unity Catalog with embedded OpenSharing classpath"
[[ -d "$UC_ROOT" ]] || {
  echo "UC_ROOT=$UC_ROOT does not exist; clone unitycatalog and set UC_ROOT." >&2
  exit 1
}
(
  cd "$UC_ROOT"
  MAVEN_PROXY_URL="$MAVEN_PROXY_URL" ./build/sbt -batch \
    "server/compile" "serverSharing/compile" "serverEmbedded/exportEmbeddedClasspath"
)
note "classpath at $UC_ROOT/server-embedded/target/classpath"

step "Laying down demo data"
mkdir -p "$DEMO_HOME/etc/conf" "$DEMO_HOME/data/orders"
if [[ -d "$DEMO_HOME/data/orders/_delta_log" ]]; then
  note "already there: $DEMO_HOME/data/orders"
else
  cp -R "$FIXTURE/." "$DEMO_HOME/data/orders/"
  note "Delta fixture: 5 rows in 2 partitions"
fi

step "Configuring Unity Catalog (embedded OpenSharing on port $OS_PORT)"
# Both UC and OpenSharing point at the same H2 file. Safe within one JVM (H2 keeps one shared
# in-memory Database instance per canonical file path per process) and keeps a single database to
# back up/inspect: OpenSharing's tables (principals, shares, shared_data_objects, recipients,
# recipient_tokens, share_permissions) don't collide with UC's uc_*-prefixed ones, and each side
# updates only the tables its own JPA/Hibernate model knows about.
UC_DB_URL="jdbc:h2:file:./etc/db/h2db;DB_CLOSE_DELAY=-1"
cat > "$DEMO_HOME/etc/conf/server.properties" <<PROPERTIES
server.env=dev
server.authorization=disable
server.opensharing.enabled=true
server.opensharing.port=$OS_PORT
server.opensharing.protocol-prefix=/api/2.1/unity-catalog/sharing
server.opensharing.external-base-url=http://localhost:$OS_PORT
server.opensharing.datasource.url=$UC_DB_URL
server.opensharing.credential-encryption-key=$CREDENTIAL_KEY
server.opensharing.principal-name=$PROVIDER
PROPERTIES
cat > "$DEMO_HOME/etc/conf/hibernate.properties" <<PROPERTIES
hibernate.connection.driver_class=org.h2.Driver
hibernate.connection.url=$UC_DB_URL
hibernate.connection.username=sa
hibernate.connection.password=
hibernate.dialect=org.hibernate.dialect.H2Dialect
hibernate.hbm2ddl.auto=update
hibernate.show_sql=false
hibernate.archive.autodetection=class
PROPERTIES
note "one H2 file at $DEMO_HOME/etc/db/h2db, shared by UC and embedded OpenSharing"

step "Starting Unity Catalog + embedded OpenSharing"
if curl -s -o /dev/null -m 2 "$UC_URI/catalogs"; then
  note "something already answers on $UC_PORT, leaving it alone"
else
  CP="$(embedded_classpath)"
  (
    cd "$DEMO_HOME"
    detached unity-catalog.pid \
      java -cp "$CP" io.unitycatalog.server.UnityCatalogServer -p "$UC_PORT" \
      > unity-catalog.log 2>&1 &
  )
  for _ in $(seq 90); do
    curl -s -o /dev/null -m 2 "$UC_URI/catalogs" \
      && curl -s -o /dev/null -m 2 "http://localhost:$OS_PORT/api/admin/v1/shares" \
        -H "Authorization: Bearer demo" \
      && break
    sleep 2
  done
  curl -s -o /dev/null -m 2 "$UC_URI/catalogs" \
    || { echo "UC did not come up; see $DEMO_HOME/unity-catalog.log" >&2; exit 1; }
  curl -s -o /dev/null -m 2 "http://localhost:$OS_PORT/api/admin/v1/shares" \
    -H "Authorization: Bearer demo" \
    || { echo "OpenSharing did not come up; see $DEMO_HOME/unity-catalog.log" >&2; exit 1; }
  note "pid $(cat "$DEMO_HOME/unity-catalog.pid"), log at $DEMO_HOME/unity-catalog.log"
fi

column() { # name type position [partition_index]
  local extra=""
  [[ -n "${4:-}" ]] && extra=",\"partition_index\":$4"
  printf '{"name":"%s","type_text":"%s","type_name":"%s","type_json":"{\\"name\\":\\"%s\\",\\"type\\":\\"%s\\",\\"nullable\\":true,\\"metadata\\":{}}","position":%s,"nullable":true%s}' \
    "$1" "$(tr '[:upper:]' '[:lower:]' <<<"$2")" "$2" "$1" "$(tr '[:upper:]' '[:lower:]' <<<"$2")" "$3" "$extra"
}

step "Seeding catalog main.sales.orders"
uc() {
  local method="$1" path="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" "$UC_URI$path" -H 'Content-Type: application/json' -d "$body"
  else
    curl -sS -X "$method" "$UC_URI$path"
  fi
}
uc POST /catalogs '{"name":"main"}' >/dev/null || true
uc POST /schemas '{"name":"sales","catalog_name":"main"}' >/dev/null || true
cat > "$DEMO_HOME/orders-table.json" <<JSON
{"name":"orders","catalog_name":"main","schema_name":"sales",
 "table_type":"EXTERNAL","data_source_format":"DELTA",
 "storage_location":"file://$DEMO_HOME/data/orders",
 "columns":[$(column order_id LONG 0),$(column amount DOUBLE 1),$(column country STRING 2 0)]}
JSON
uc POST /tables "$(cat "$DEMO_HOME/orders-table.json")" >/dev/null || true
uc GET /tables/main.sales.orders \
  | jq -c '{full_name:"main.sales.orders",table_type,data_source_format,storage_location}'

cat > "$DEMO_HOME/demo.env" <<ENV
# Written by demo-embedded-up.sh; sourced by demo-embedded.sh
UC_URI=$UC_URI
UC_PORT=$UC_PORT
OS_PORT=$OS_PORT
SERVER=http://localhost:$OS_PORT
ADMIN=http://localhost:$OS_PORT/api/admin/v1
PROTOCOL=http://localhost:$OS_PORT/api/2.1/unity-catalog/sharing
PROVIDER=$PROVIDER
PROVIDER_TOKEN=demo-provider-token
DEMO_DATA=$DEMO_HOME/data
ENV

step "Ready"
ADMIN_URL="http://localhost:$OS_PORT/api/admin/v1"
PROTOCOL_URL="http://localhost:$OS_PORT/api/2.1/unity-catalog/sharing"
cat <<NEXT

  source $DEMO_HOME/demo.env
  $SERVER_DIR/scripts/demo-embedded.sh

  Provider admin:  $ADMIN_URL  (Authorization: Bearer \$PROVIDER_TOKEN)
  Protocol base:   $PROTOCOL_URL
  Unity Catalog:   $UC_URI

  Stop:  kill \$(cat $DEMO_HOME/unity-catalog.pid)
  Reset: rm -rf $DEMO_HOME
NEXT
