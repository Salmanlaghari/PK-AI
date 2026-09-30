#!/usr/bin/env bash
# Rebuilds the Ultra AI web UI and syncs it into the Android assets.
#
# NOTE: Android builds already run these steps automatically via the
# `syncWebAssets` Gradle task in app/build.gradle.kts (best-effort: skipped
# when node is missing), so a plain Android Studio build always ships the
# latest web bundle. Wiring the same steps into the CI workflows is still
# pending the "Workflows: Read and write" permission for the GitHub App —
# until then, do NOT assume CI refreshes the assets.
#
# Invoke as: bash scripts/sync-web-assets.sh   (file is mode 100644)
set -euo pipefail
cd "$(dirname "$0")/../ultra-ai-chat-space"
npm ci
npm run build
rm -rf ../app/src/main/assets/ultra-ai-chat-space
mkdir -p ../app/src/main/assets/ultra-ai-chat-space
cp -r dist/* ../app/src/main/assets/ultra-ai-chat-space/
echo "Web assets synced to app/src/main/assets/ultra-ai-chat-space/"
