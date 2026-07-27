# Google Play Protect Warning Guide

This guide explains why Google Play Protect may warn or block the app during installation, and how you can safely bypass it for development and testing.

## Why is Google Play Protect Block Triggered?

When you install `app-debug.apk` directly via download or USB, Android displays a Google Play Protect prompt stating:
> **"App blocked to protect your device"**
> *Play Protect hasn't seen an app from this developer before. It may be unsafe.*

This occurs due to several standard safety measures:

1. **Generic Debug Signature**:
   The debug build of PK AI is signed using Android's standard, auto-generated Gradle debug key (`androiddebugkey`). Since this key is publicly available and not associated with a unique, verified human identity, Google's heuristic scan naturally flags it as low-trust.

2. **Low Package Reputation**:
   The unique application ID `com.salmanlaghari.pkai` is totally new and hasn't yet accumulated installation volume or reputation history on the Google Play Store network. Heuristic scanners automatically flag low-reputation apps to prevent side-loading.

3. **No Play Console Registration**:
   The application signature has not been uploaded to a verified Google Developer Console account. In a production release, Google scans the app in advance during the store listing process, which whitelist-verifies the package name.

---

## How to Install and Bypass Play Protect Safely for Testing

Since you are building and compiling this app directly from the source code, you know it contains **zero** malicious functions, trackers, or dangerous payloads.

### Method 1: Bypass During Installation
1. On the Play Protect blocking dialog, look for the **"More details"** dropdown arrow.
2. Tap on **"More details"** to expand the dialog.
3. Tap the option that appears: **"Install anyway"**.
4. The application will install and run perfectly on your device.

### Method 2: Temporary Disable Play Protect (For Developers)
If you are frequently installing and testing custom developer builds on your phone:
1. Open the **Google Play Store** app.
2. Tap your **profile icon** in the top right corner.
3. Select **Play Protect** from the menu.
4. Tap the **Settings gear icon** in the top-right corner.
5. Turn **off** "Scan apps with Play Protect" and "Improve harmful app detection".
*(Remember to turn these back on once you are done testing to keep your phone protected from real-world online threats).*

---

## Safe Production Recommendations

When you are ready to release the application to the public:
* **Obtain a Private Keystore**: Always generate a unique, password-protected production signing keystore (`.jks`) using Android Studio.
* **Enable R8 / Proguard Code Shrinking**: Set `isMinifyEnabled = true` in your release build configuration inside `app/build.gradle.kts`. This obfuscates and optimizes classes, preventing reverse engineering and false-positive scanner flags.
* **Publish via Google Play Console**: Even an internal or closed beta track on Google Play Console pre-registers your app's signature, clearing any automatic block flags on end-user devices.
