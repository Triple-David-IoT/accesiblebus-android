#!/usr/bin/env bash
# API smoke test (A08): health, reset, create, repeat, conflict, accept, complete,
# and /sync with the three demo tokens.
#
# Needs curl and Python 3 (set PYTHON if the command is not python3).
# It resets the demo trip, so run it on a laboratory, never during a demo.

set -euo pipefail

API_URL="${API_URL:-http://localhost:8000}"
PYTHON="${PYTHON:-python3}"
RUN="smoke-$(date +%s)-$$"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

PASSENGER="demo-pasajero-1"
DRIVER="demo-conductor-1"
OPERATOR="demo-operador-1"
STATUS=""

fail() {
  echo "FAIL: $1"
  if [ -s "$TMP/body" ]; then
    cat "$TMP/body"
    echo
  fi
  exit 1
}

# call METHOD PATH TOKEN [KEY] [JSON_BODY]
call() {
  local args=(-s -o "$TMP/body" -w '%{http_code}' -X "$1" "$API_URL$2")
  [ -n "$3" ] && args+=(-H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && args+=(-H "Idempotency-Key: $RUN-$4")
  [ -n "${5:-}" ] && args+=(-H "Content-Type: application/json" -d "$5")
  STATUS=$(curl "${args[@]}") || fail "could not reach $API_URL"
}

expect() {
  [ "$STATUS" = "$1" ] || fail "$2: expected HTTP $1, got $STATUS"
  echo "OK: $2 (HTTP $STATUS)"
}

# json EXPRESSION: evaluates a Python expression on the last response, available as d.
json() {
  "$PYTHON" -c "import json, sys; d = json.load(open(sys.argv[1])); print($1)" "$TMP/body"
}

same() {
  [ "$1" = "$2" ] || fail "$3: expected '$2', got '$1'"
}

echo "Smoke test against $API_URL (run $RUN)"

call GET /health ""
expect 200 "health"
same "$(json 'd["status"]')" "ok" "health status"

call POST /demo/reset "$OPERATOR" reset
expect 200 "operator resets the demo trip"
TRIP=$(json 'd["trip"]["id"]')
VISIT=$(json 'd["stopVisits"][1]["id"]')
OTHER_VISIT=$(json 'd["stopVisits"][2]["id"]')

CREATE_BODY="{\"stopVisitId\":\"$VISIT\",\"assistanceType\":\"RAMP_BOARDING\"}"
call POST /requests "$PASSENGER" create "$CREATE_BODY"
expect 201 "passenger creates a request"
REQUEST=$(json 'd["id"]')
same "$(json 'd["state"], d["version"]')" "PENDING 1" "new request"

call POST /requests "$PASSENGER" create "$CREATE_BODY"
expect 200 "repeating the same key returns the stored request"
same "$(json 'd["id"]')" "$REQUEST" "repeated request id"

call POST /requests "$PASSENGER" create "{\"stopVisitId\":\"$OTHER_VISIT\",\"assistanceType\":\"RAMP_BOARDING\"}"
expect 409 "same key with another body is a conflict"
same "$(json 'd["code"]')" "IDEMPOTENCY_CONFLICT" "conflict code"

call POST "/requests/$REQUEST/accept" "$DRIVER" accept '{"expectedVersion":1}'
expect 200 "driver accepts"
same "$(json 'd["state"], d["version"]')" "ACCEPTED 2" "accepted request"

call POST "/requests/$REQUEST/complete" "$DRIVER" complete '{"expectedVersion":2}'
expect 200 "driver completes"
same "$(json 'd["state"], d["version"]')" "COMPLETED 3" "completed request"

call GET "/sync?scope=mine" "$PASSENGER"
expect 200 "passenger reads /sync"
same "$(json 'd["trip"]["id"]')" "$TRIP" "passenger trip"
same "$(json '[r["state"] for r in d["requests"] if r["id"] == "'"$REQUEST"'"]')" "['COMPLETED']" "passenger request in /sync"

call GET "/sync?scope=vehicle&vehicleId=BUS-DEMO-01" "$DRIVER"
expect 200 "driver reads /sync"
same "$(json 'd["trip"]["id"]')" "$TRIP" "driver trip"

call GET "/sync?scope=vehicle&vehicleId=BUS-DEMO-01" "$OPERATOR"
expect 200 "operator reads /sync"
same "$(json 'len(d["stopVisits"])')" "4" "operator stop visits"

echo "Smoke test passed"
