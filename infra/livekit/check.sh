#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
set -a
source .env
set +a

CALL_HOST="${CALL_DOMAIN%%:*}"

curl --fail --silent --show-error \
  --retry 15 --retry-all-errors --retry-delay 2 \
  "http://127.0.0.1:7890/health" | grep -q '"ok":true'
ss -lntup | grep -E ':(80|443|8443|5349|7880|7881|7890)\b' || true
docker inspect takt-call-caddy \
  --format 'caddy network={{.HostConfig.NetworkMode}} pid={{.State.Pid}} status={{.State.Status}}'
if ! curl --fail --silent --show-error \
  --resolve "${CALL_DOMAIN}:127.0.0.1" \
  "https://${CALL_DOMAIN}/health" | grep -q '"ok":true'; then
  docker compose logs --tail=120 caddy token-service
  exit 1
fi
# A non-failing probe documents whether a standard HTTPS route already exists
# through the VPS proxy. It never prints response content or credentials.
curl --silent --show-error --insecure --connect-timeout 5 \
  --resolve "${CALL_HOST}:443:127.0.0.1" \
  --output /dev/null --write-out 'call_443_health_status=%{http_code}\n' \
  "https://${CALL_HOST}/health" || true
openssl s_client -connect "127.0.0.1:5349" -servername "${TURN_DOMAIN}" </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
docker compose ps
docker compose logs --tail=80 livekit caddy token-service
