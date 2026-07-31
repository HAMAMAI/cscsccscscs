#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${DUCKDNS_TOKEN:-}" && -r /etc/takt-livekit/duckdns-token ]]; then
  DUCKDNS_TOKEN="$(< /etc/takt-livekit/duckdns-token)"
fi
: "${DUCKDNS_TOKEN:?DUCKDNS_TOKEN is required}"
: "${CERTBOT_DOMAIN:?CERTBOT_DOMAIN is required}"

suffix='.duckdns.org'
if [[ "$CERTBOT_DOMAIN" != *"$suffix" ]]; then
  echo "DuckDNS hook received a non-DuckDNS domain" >&2
  exit 1
fi
subdomain="${CERTBOT_DOMAIN%$suffix}"

response="$(curl --fail --silent --show-error --retry 3 --retry-all-errors --retry-delay 2 \
  --get 'https://www.duckdns.org/update' \
  --data-urlencode "domains=$subdomain" \
  --data-urlencode "token=$DUCKDNS_TOKEN" \
  --data-urlencode 'clear=true')"
[[ "$response" == OK* ]]
