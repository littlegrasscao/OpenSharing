#!/usr/bin/env bash
# End-to-end walkthrough of OpenSharing embedded inside Unity Catalog OSS.
#
# Prerequisite:  ./scripts/demo-embedded-up.sh
#
# Environment (or source demo.env written by demo-embedded-up.sh):
#   SERVER            the one address — UC's own public port  (default http://localhost:8080)
#   ADMIN             provider admin API                       (default $SERVER/api/2.1/opensharing/provider)
#   PROTOCOL          recipient protocol prefix                (default $SERVER/api/2.1/opensharing)
#   PROVIDER          catalog principal the provider shares as (default admin@unitycatalog.local)
#   PROVIDER_TOKEN    bearer token for admin calls; a JWT minted for $PROVIDER by
#                     demo-embedded-up.sh, whose subject is what the provider is attributed as
#                     (default: a plain string, attributed under that string verbatim)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -z "${SERVER:-}" && -f "${DEMO_HOME:-$HOME/.opensharing-embedded-demo}/demo.env" ]]; then
  # shellcheck disable=SC1090
  source "${DEMO_HOME:-$HOME/.opensharing-embedded-demo}/demo.env"
fi

SERVER="${SERVER:-http://localhost:8080}"
ADMIN="${ADMIN:-$SERVER/api/2.1/opensharing/provider}"
PROTOCOL="${PROTOCOL:-$SERVER/api/2.1/opensharing}"
PROVIDER="${PROVIDER:-admin@unitycatalog.local}"
PROVIDER_TOKEN="${PROVIDER_TOKEN:-demo-provider-token}"

SHARE="demo_share_$RANDOM"
RECIPIENT="demo_partner_$RANDOM"

admin() {
  local method="$1" path="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" "$ADMIN$path" \
      -H "Authorization: Bearer $PROVIDER_TOKEN" -H 'Content-Type: application/json' -d "$body"
  else
    curl -sS -X "$method" "$ADMIN$path" -H "Authorization: Bearer $PROVIDER_TOKEN"
  fi
}

step() { printf '\n\033[1;36m== %s\033[0m\n' "$1"; }
note() { printf '   %s\n' "$1"; }
maybe_pause() { if [[ "${PAUSE:-}" == ask ]]; then read -r -p 'Press enter...' _; fi; }

step "Unity Catalog and OpenSharing share one process and one address: $SERVER"
note "provider principal is '$PROVIDER' — the subject of PROVIDER_TOKEN's own JWT, not a"
note "  name configured on UC's side (authorization is disabled; nothing verifies it)"
note "admin calls authenticate with PROVIDER_TOKEN; the catalog is queried in-process"
maybe_pause

step "Provider creates share '$SHARE'"
admin POST /shares \
  "{\"name\":\"$SHARE\",\"display_name\":\"Embedded Demo Share\",\"comment\":\"created by demo-embedded.sh\"}" \
  | jq '{share_id,name,owner_id,created_by}'
maybe_pause

step "Provider adds main.sales.orders; the table is resolved in the local UC catalog"
admin PATCH "/shares/$SHARE" '{
  "updates": [
    {"action":"ADD","data_object":{"name":"main.sales.orders","type":"TABLE","shared_as":"sales.orders"}}
  ]}' | jq '.objects[] | {name,shared_as,type,source_format,status,storage_location}'
maybe_pause

step "A table the catalog does not know about is refused"
admin PATCH "/shares/$SHARE" \
  '{"updates":[{"action":"ADD","data_object":{"name":"main.sales.ghost"}}]}' | jq .
maybe_pause

step "Creating the recipient mints its token; only the activation URL comes back"
CREATED=$(admin POST /recipients \
  "{\"name\":\"$RECIPIENT\",\"auth_type\":\"TOKEN\",\"token_expiration_seconds\":7776000}")
echo "$CREATED" | jq '{recipient:.recipient.name,owner:.recipient.owner_id,token:.token}'
ACTIVATION_URL=$(echo "$CREATED" | jq -r .token.activation_url)
maybe_pause

step "Provider grants SELECT on the share to the recipient"
admin PATCH "/shares/$SHARE/permissions" \
  "{\"changes\":[{\"recipient_name\":\"$RECIPIENT\",\"add\":[\"SELECT\"]}]}" \
  | jq '.items[] | {share_name,recipient_name,privilege,granted_at,granted_by}'
maybe_pause

step "Recipient opens the activation URL once and receives config.share"
PROFILE=$(curl -sS "$ACTIVATION_URL")
echo "$PROFILE" | jq '{shareCredentialsVersion,endpoint,icebergEndpoint,expirationTime,bearerToken:"<redacted>"}'
TOKEN=$(echo "$PROFILE" | jq -r .bearerToken)
maybe_pause

step "The activation URL cannot be replayed"
curl -sS "$ACTIVATION_URL" | jq .
maybe_pause

recipient() { curl -sS "$PROTOCOL$1" -H "Authorization: Bearer $TOKEN"; }

step "Recipient discovers what it can read"
recipient /shares | jq '.items[] | {name,displayName}'
recipient "/shares/$SHARE/schemas" | jq '.items[].name'
recipient "/shares/$SHARE/all-tables" | jq '.items[] | {name,schema,location,accessModes}'
maybe_pause

step "Recipient asks for scoped, short-lived storage credentials"
curl -sS -X POST \
  "$PROTOCOL/shares/$SHARE/schemas/sales/tables/orders/temporary-table-credentials" \
  -H "Authorization: Bearer $TOKEN" | jq .
maybe_pause

step "Url access mode reads Delta metadata from the table location"
recipient "/shares/$SHARE/schemas/sales/tables/orders/metadata" | jq .
maybe_pause

step "Iceberg REST catalog browses the same share"
recipient "/iceberg/v1/config?warehouse=$SHARE" | jq .
ICEBERG="/iceberg/v1/shares/$SHARE"
recipient "$ICEBERG/namespaces" | jq -c .
recipient "$ICEBERG/namespaces/sales/tables" | jq -c .
maybe_pause

step "Done — embedded UC + OpenSharing end-to-end"
note "Two-process alternative: demo-unity-up.sh + standalone OpenSharing on :8099"
