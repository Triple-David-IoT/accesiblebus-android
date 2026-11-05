#!/usr/bin/env bash

set -e

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
    cp .env.example .env
    echo ".env created successfully using .env.example"
fi

docker compose up -d --build

echo "App Services Initialized"
docker compose ps
