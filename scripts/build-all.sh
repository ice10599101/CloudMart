#!/usr/bin/env bash
# ENG-01: one-command build verification (backend + 3 frontends), non-zero exit on any failure
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== [1/2] backend: 22 in-scope modules compile + unit tests ==="
./mvnw -B -pl 'mall-common,mall-gateway,mall-auth,mall-user,mall-product,mall-order,mall-payment,mall-inventory,mall-coupon,mall-risk,mall-cart,mall-seckill,mall-notification,mall-ai,mall-marketing,mall-live,mall-wms,mall-admin,mall-file,mall-job,mall-gen,mall-community' -am test

echo "=== [2/2] frontends: lockfile install + typecheck + web tests ==="
for dir in CloudMart-ui cloudmart-app cloudmart-mobile; do
  echo "--- $dir ---"
  (cd "$dir" && npm ci)
  (cd "$dir" && npx tsc --noEmit)
done
(cd CloudMart-ui && npx vitest run)

echo "=== ALL PASSED ==="
