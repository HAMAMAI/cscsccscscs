#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${DUCKDNS_TOKEN:-}" && -r /etc/takt-livekit/duckdns-token ]]; then
  DUCKDNS_TOKEN="$(< /etc/takt-livekit/duckdns-token)"
fi
: "${DUCKDNS_TOKEN:?DUCKDNS_TOKEN is required}"
: "${CERTBOT_DOMAIN:?CERTBOT_DOMAIN is required}"
: "${CERTBOT_VALIDATION:?CERTBOT_VALIDATION is required}"

suffix='.duckdns.org'
if [[ "$CERTBOT_DOMAIN" != *"$suffix" ]]; then
  echo "DuckDNS hook received a non-DuckDNS domain" >&2
  exit 1
fi
subdomain="${CERTBOT_DOMAIN%$suffix}"

response="$(curl --fail --silent --show-error --retry 5 --retry-all-errors --retry-delay 2 \
  --get 'https://www.duckdns.org/update' \
  --data-urlencode "domains=$subdomain" \
  --data-urlencode "token=$DUCKDNS_TOKEN" \
  --data-urlencode "txt=$CERTBOT_VALIDATION" \
  --data-urlencode 'clear=false')"
[[ "$response" == OK* ]]

# Give public DNS resolvers time to observe the ACME TXT value.
sleep 45
