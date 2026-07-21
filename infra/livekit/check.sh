#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
set -a
source .env
set +a

curl --fail --silent --show-error \
  --retry 15 --retry-all-errors --retry-delay 2 \
  "https://${CALL_DOMAIN}/health" | grep -q '"ok":true'
openssl s_client -connect "${TURN_DOMAIN}:443" -servername "${TURN_DOMAIN}" </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
docker compose ps
docker compose logs --tail=80 livekit
