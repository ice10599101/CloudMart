#!/usr/bin/env bash
# ENG-01: one-command build verification (backend + 3 frontends), non-zero exit on any failure
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== [1/2] backend: full-module compile + unit tests（根 POM 单一口径，与 CI 一致） ==="
./mvnw -B test

echo "=== [2/2] frontends: lockfile install + typecheck + web tests ==="
for dir in CloudMart-ui cloudmart-app cloudmart-mobile; do
  echo "--- $dir ---"
  (cd "$dir" && npm ci)
  (cd "$dir" && npx tsc --noEmit)
done
(cd CloudMart-ui && npx vitest run)

echo "=== ALL PASSED ==="
