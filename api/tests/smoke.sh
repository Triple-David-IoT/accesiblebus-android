#!/usr/bin/env bash

set -e

API_URL="${API_URL:-http://localhost:8000}"

echo "Performing API Health Check"

response=$(curl -fsS "$API_URL/health")

if ! grep -q '"status":"ok"' <<<"$response"; then
  echo "ERROR: Unexpected API response"
  echo "$response"
  exit 1
fi

echo "OK: API is Healthy"
