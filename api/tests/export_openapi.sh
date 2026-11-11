#!/usr/bin/env bash
# Writes contracts/openapi.yaml from the OpenAPI document of a running API.
# Usage: api/tests/export_openapi.sh [API_URL]   (needs curl and python3 with PyYAML)
set -euo pipefail

API_URL="${1:-${API_URL:-http://localhost:8000}}"
OUT="$(cd "$(dirname "$0")/../.." && pwd)/contracts/openapi.yaml"

curl -fsS -m 10 "$API_URL/openapi.json" | python3 -c '
import json, sys, yaml
spec = json.load(sys.stdin)
sys.stdout.write("# Generated from the API by api/tests/export_openapi.sh. Do not edit by hand.\n")
yaml.safe_dump(spec, sys.stdout, sort_keys=False, allow_unicode=True, width=100)
' > "$OUT"

echo "Wrote $OUT from $API_URL"
