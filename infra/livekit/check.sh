#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
set -a
source .env
set +a

curl --fail --silent --show-error \
  --retry 15 --retry-all-errors --retry-delay 2 \
  "http://127.0.0.1:7890/health" | grep -q '"ok":true'
if ! curl --fail --silent --show-error \
  --resolve "${CALL_DOMAIN}:443:127.0.0.1" \
  "https://${CALL_DOMAIN}/health" | grep -q '"ok":true'; then
  docker compose logs --tail=120 caddy token-service
  exit 1
fi
openssl s_client -connect "127.0.0.1:5349" -servername "${TURN_DOMAIN}" </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
docker compose ps
docker compose logs --tail=80 livekit caddy token-service
