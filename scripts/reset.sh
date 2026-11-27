#!/usr/bin/env bash
# Reset the AccessBus demo trip through the operator-only API endpoint.
# This ends the active trip and creates a new one; it does not delete volumes.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

API_URL="${API_URL:-http://localhost:8000}"
API_URL="${API_URL%/}"
OPERATOR_TOKEN="${OPERATOR_TOKEN:-demo-operador-1}"

usage() {
  cat <<'USAGE'
Usage: bash scripts/reset.sh [--yes]

Ends the active demo trip and creates a new one using the operator token.
It does not stop containers or delete persistent volumes.

Environment variables:
  API_URL          API base URL (default: http://localhost:8000)
  OPERATOR_TOKEN   Operator bearer token (default: demo-operador-1)

Options:
  --yes            Skip the interactive confirmation.
USAGE
}

SKIP_CONFIRMATION=false
case "${1:-}" in
  "") ;;
  --yes) SKIP_CONFIRMATION=true ;;
  -h|--help) usage; exit 0 ;;
  *) usage >&2; exit 2 ;;
esac

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required." >&2
  exit 1
fi

if [[ "$SKIP_CONFIRMATION" != true ]]; then
  echo "This will end the active demo trip and create a new trip."
  echo "No database volumes will be deleted."
  read -r -p "Continue? [y/N] " answer
  case "$answer" in
    y|Y|yes|YES) ;;
    *) echo "Reset cancelled."; exit 0 ;;
  esac
fi

# A fresh key is required for every intentional reset. Reusing a key would
# correctly return the previous idempotent response instead of resetting again.
IDEMPOTENCY_KEY="reset-$(date +%s)-$$-${RANDOM}"
RESPONSE_FILE="$(mktemp)"
trap 'rm -f "$RESPONSE_FILE"' EXIT

HTTP_STATUS="$(curl -sS -o "$RESPONSE_FILE" -w '%{http_code}' \
  -X POST "$API_URL/demo/reset" \
  -H "Authorization: Bearer $OPERATOR_TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY")" || {
    echo "ERROR: Could not reach the API at $API_URL." >&2
    exit 1
  }

if [[ "$HTTP_STATUS" != "200" ]]; then
  echo "ERROR: Demo reset failed (HTTP $HTTP_STATUS). Response:" >&2
  cat "$RESPONSE_FILE" >&2
  echo >&2
  exit 1
fi

echo "Demo reset completed successfully. New trip details:"
cat "$RESPONSE_FILE"
echo

