#!/usr/bin/env bash
# Rebuilds the Ultra AI web UI and syncs it into the Android assets.
# Run this before building the APK in Android Studio so the app always
# ships the latest web bundle (CI runs the same steps automatically).
set -euo pipefail
cd "$(dirname "$0")/../ultra-ai-chat-space"
npm ci
npm run build
rm -rf ../app/src/main/assets/ultra-ai-chat-space
mkdir -p ../app/src/main/assets/ultra-ai-chat-space
cp -r dist/* ../app/src/main/assets/ultra-ai-chat-space/
echo "Web assets synced to app/src/main/assets/ultra-ai-chat-space/"
