#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "Copy .env.example to .env and fill the values first." >&2
  exit 1
fi

set -a
source .env
set +a

CALL_HOST="${CALL_DOMAIN%%:*}"
export CALL_HOST

for value in CALL_DOMAIN TURN_DOMAIN LIVEKIT_API_KEY LIVEKIT_API_SECRET LIVEKIT_NODE_IP SUPABASE_URL SUPABASE_PUBLISHABLE_KEY; do
  if [[ -z "${!value:-}" ]]; then
    echo "Missing $value in .env" >&2
    exit 1
  fi
done

install -d -m 700 generated
envsubst < livekit.template.yaml > generated/livekit.yaml
envsubst < Caddyfile.template > generated/Caddyfile
chmod 600 generated/livekit.yaml

docker compose config --quiet
docker compose pull
docker compose up -d --remove-orphans --force-recreate
docker compose ps
