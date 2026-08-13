# PK-AI

Premium AI Super App — a state-of-the-art native Android application that brings deep learning chat, image, video and music generation into a single premium, glassmorphic experience.

## Build Requirements

- Android SDK 36 (Android 16, targetSdk 36 / compileSdk 36)
- JDK 21 (toolchain: AGP 8.9.2 · Kotlin 2.0.21 · Gradle 8.11.1)
- `local.properties` with `sdk.dir=...` (or `ANDROID_HOME`)

## Features

- 🔐 **Email/Password Auth + Google Sign-In / Sign-Up** via Firebase (friendly error messages & correct navigation after success)
- 💬 **Free AI Chat** — Gemini Flash, no login required (WebView + JS bridge)
- 🎨 **AI Image Generator** — DALL·E via OpenAI Images API (b64 decoded in-app)
- 🎬 **AI Video Generator** — Runway-powered studio (graceful when key absent)
- 🎵 **AI Music Generator** — Suno-powered studio (graceful when key absent)
- 🧭 **Premium 3D glass sidebar** — animated Aurora backdrop, glassmorphism, smooth navigation
- 🗣 Voice assistant, chat history (Room), AdMob integration

## API Keys (auto-injected, never hard-coded)

All keys are read at build time from **`local.properties`** or **environment variables** and injected as `BuildConfig` fields. Add them to your local.properties (gitignored):

```properties
GEMINI_API_KEY=...        # Free AI Chat (Gemini Flash)
OPENAI_API_KEY=...        # AI Image Generator / ChatGPT
OPENROUTER_API_KEY=...    # Chat providers (DeepSeek, Llama, Mistral, ...)
RUNWAY_API_KEY=...        # AI Video Generator
SUNO_API_KEY=...          # AI Music Generator
```

CI (`.github/workflows/build.yml`) injects them via **GitHub Secrets** (same names), so no secrets ever live in the repository.

## Build

```bash
gradlew.bat :app:compileDebugKotlin    # compile check
gradlew.bat assembleRelease bundleRelease   # signed APK + AAB
```

## Firebase Setup

`app/google-services.json` is committed for the `ailatestfinder` project. For Google Sign-In each developer machine must have its SHA-1 fingerprint registered in the Firebase console (see `GOOGLE_SIGN_IN_SETUP.md`).

## Verification Status
- **Android 16 (SDK 36) upgrade**: compileSdk/targetSdk 36, toolchain AGP 8.9.2 · Kotlin 2.0.21 · Gradle 8.11.1
- **Auth (email/password + Google) + Free Chat + AI Image/Video/Music modules**: implemented on branch `feature/pk-ai-android16-auth-ai-modules`
