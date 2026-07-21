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

for domain in "$CALL_DOMAIN" "$TURN_DOMAIN"; do
  if ! getent ahostsv4 "$domain" | awk '{print $1}' | grep -Fxq "$LIVEKIT_NODE_IP"; then
    echo "$domain does not resolve to $LIVEKIT_NODE_IP" >&2
    exit 1
  fi
  if [[ ! -s "/etc/letsencrypt/live/$domain/fullchain.pem" ]]; then
    certbot certonly --standalone \
      --non-interactive --agree-tos --register-unsafely-without-email \
      --preferred-challenges http --cert-name "$domain" -d "$domain"
  fi
done

if command -v ufw >/dev/null && ufw status | grep -q '^Status: active'; then
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw allow 443/udp
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
