#!/usr/bin/env bash
set -euo pipefail
# Run on the Linux host after reviewing README and replacing the Caddy hostname.
cd "$(dirname "$0")"
test "$(pwd)" = /opt/dhyan/relay || { echo 'Place the repository at /opt/dhyan first.'; exit 1; }
command -v node >/dev/null
node -e 'if (Number(process.versions.node.split(".")[0]) < 20) process.exit(1)'
if grep -q '<ELASTIC-IP-with-dashes>' Caddyfile; then
  echo 'Replace the Caddy hostname before deployment.'; exit 1
fi
test -d ../contracts/sync/schemas || { echo "Keep the full repository layout."; exit 1; }
npm --prefix .. ci --ignore-scripts
npm ci --omit=dev
npm test
id dhyan >/dev/null 2>&1 || sudo useradd --system --no-create-home --shell /usr/sbin/nologin dhyan
sudo install -m 0644 dhyan-relay.service /etc/systemd/system/dhyan-relay.service
sudo install -m 0644 Caddyfile /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl daemon-reload
sudo systemctl enable --now dhyan-relay
sudo systemctl reload caddy
curl --fail http://127.0.0.1:8080/healthz
