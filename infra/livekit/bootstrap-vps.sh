#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root." >&2
  exit 1
fi

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

for value in CALL_DOMAIN TURN_DOMAIN LIVEKIT_NODE_IP; do
  if [[ -z "${!value:-}" ]]; then
    echo "Missing $value in .env" >&2
    exit 1
  fi
done

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates certbot curl docker.io docker-compose-v2 gettext-base
systemctl enable --now docker

ensure_certificate() {
  local domain="$1"
  if [[ -s "/etc/letsencrypt/live/$domain/fullchain.pem" ]]; then
    return
  fi

  if [[ "$domain" == *.duckdns.org ]]; then
    if [[ -z "${DUCKDNS_TOKEN:-}" ]]; then
      echo "DUCKDNS_TOKEN is required to issue a certificate for $domain" >&2
      return 1
    fi
    install -d -m 700 /etc/takt-livekit
    printf '%s' "$DUCKDNS_TOKEN" > /etc/takt-livekit/duckdns-token
    chmod 600 /etc/takt-livekit/duckdns-token
    chmod 700 "$ROOT/duckdns-auth-hook.sh" "$ROOT/duckdns-cleanup-hook.sh"
    # DNS-01 leaves TCP 80/443 untouched, so unrelated VPS services never
    # have to be stopped to obtain or renew the DuckDNS certificate.
    certbot certonly --manual --preferred-challenges dns \
      --manual-auth-hook "$ROOT/duckdns-auth-hook.sh" \
      --manual-cleanup-hook "$ROOT/duckdns-cleanup-hook.sh" \
      --non-interactive --agree-tos --register-unsafely-without-email \
      --cert-name "$domain" -d "$domain"
    return
  fi

  certbot certonly --standalone \
    --non-interactive --agree-tos --register-unsafely-without-email \
    --preferred-challenges http --cert-name "$domain" -d "$domain"
}

for domain in "$CALL_HOST" "$TURN_DOMAIN"; do
  if ! getent ahostsv4 "$domain" | awk '{print $1}' | grep -Fxq "$LIVEKIT_NODE_IP"; then
    echo "$domain does not resolve to $LIVEKIT_NODE_IP" >&2
    exit 1
  fi
  ensure_certificate "$domain"
done

if command -v ufw >/dev/null && ufw status | grep -q '^Status: active'; then
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw allow 443/udp
  ufw allow 8443/tcp
  ufw allow 5349/tcp
  ufw allow 7881/tcp
  ufw allow 50000:50100/udp
fi

install -d -m 755 /etc/letsencrypt/renewal-hooks/deploy
install -m 755 /dev/stdin /etc/letsencrypt/renewal-hooks/deploy/takt-livekit-reload <<'HOOK'
#!/usr/bin/env bash
docker restart takt-livekit takt-call-caddy >/dev/null 2>&1 || true
HOOK

./deploy.sh
./check.sh
