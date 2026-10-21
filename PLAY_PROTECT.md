# Google Play Protect Warning — What It Means and What To Do

When you install an APK downloaded directly (outside Google Play), Android may show a Google Play Protect prompt such as:

> **"App blocked to protect your device"**
> *Play Protect hasn't seen an app from this developer before. It may be unsafe.*

## Why this warning appears

This is a standard, automatic caution — it does not mean the app was found to be malicious. It commonly appears when:

1. **The build is signed with the generic Android debug key** (`androiddebugkey`), or with a key Play Protect has not built reputation for yet.
2. **The package is new** and has little installation history, so heuristic scanners treat it cautiously.
3. **The app was not installed through Google Play**, so Play has not pre-scanned this exact build during a store review.

## What you should do

- **Only download PK AI from the official GitHub releases page** of this repository. Builds shared anywhere else may have been modified.
- **Verify the SHA-256 checksum** published with the release before installing — it confirms the file is the exact build the developer released.
- **Keep Play Protect turned ON.** It is an important safety layer, and this project will never ask you to disable it.
- **If Play Protect blocks the installation, do not force it.** Delete the file, re-download it from the official release page, verify the checksum, and if the warning persists, open an issue in this repository instead of installing.

## For production releases

* Sign releases with a private, password-protected production keystore, kept offline.
* Enable R8 / ProGuard code shrinking (`isMinifyEnabled = true`) for release builds.
* Distribute through Google Play where possible, so builds are pre-scanned and the signing key builds reputation.
