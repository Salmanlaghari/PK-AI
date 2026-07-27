# 🔐 Google Sign-In Setup & Configuration Guide — ailatestfinder

This guide provides the complete, step-by-step instructions required to activate and configure native **Google Sign-In** for PK AI (project **ailatestfinder**) using the Android Credential Manager API.

Because Firebase and Google Cloud consoles require manual configuration tied to your developer signature, please follow these steps to register your local developer machine and active devices.

---

## 🚀 Step 1: Register the Application on Firebase Console

1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Select or create your Firebase project: **ailatestfinder**.
3. Under project settings, click **Add App** and select **Android**.
4. Enter the exact application package name:
   ```
   com.salmanlaghari.pkai
   ```
5. Enter an App nickname (e.g., `ailatestfinder-release`).
6. Complete the setup and download the `google-services.json` configuration file.

---

## 📦 Step 2: Add `google-services.json` to the Project

1. Copy the downloaded `google-services.json` file.
2. Place it directly inside your project's application folder:
   ```
   app/google-services.json
   ```
3. Make sure the Google Services Plugin is registered in your root build script. In `build.gradle.kts` (project level):
   ```kotlin
   plugins {
       id("com.google.gms.google-services") version "4.4.1" apply false
   }
   ```
4. And in your app module build script `app/build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.google.gms.google-services")
   }
   ```

---

## 🔑 Step 3: Extract and Configure SHA-1 & SHA-256 Fingerprints

For security, Google Sign-In requires your computer's signature fingerprint to be registered on the Firebase and Google Cloud Console.

### 💻 Locate Your Debug or Release Fingerprint:

#### A. Command using keytool (for `ailatestfinder-release.jks`):
```bash
keytool -list -v -keystore ailatestfinder-release.jks -alias ailatestfinder -storepass "AilaLatestFindzP ass2026!"
```

#### B. Command using Gradle:
```bash
./gradlew signingReport
```

### 📋 Exact Production/Release Fingerprints to Copy:
```text
Alias name: ailatestfinder
SHA1: 4C:2F:6A:95:F4:80:B8:5A:90:20:DA:F9:BD:61:FD:9B:21:D3:DD:F9
SHA256: 4E:28:95:B7:FA:8F:21:C6:2A:2A:73:31:98:B4:26:3F:34:4B:E8:80:09:0B:71:64:5C:00:1D:F3:78:B0:73:91
```

### ➕ Add Fingerprints to Firebase Console:
1. In the [Firebase Console](https://console.firebase.google.com/), go to **Project Settings** (gear icon) > **General**.
2. Scroll down to **Your apps** > **PK AI (Android)**.
3. Click **Add fingerprint**.
4. Paste your **SHA-1** fingerprint (`4C:2F:6A:95:F4:80:B8:5A:90:20:DA:F9:BD:61:FD:9B:21:D3:DD:F9`) and save.
5. Click **Add fingerprint** again.
6. Paste your **SHA-256** fingerprint (`4E:28:95:B7:FA:8F:21:C6:2A:2A:73:31:98:B4:26:3F:34:4B:E8:80:09:0B:71:64:5C:00:1D:F3:78:B0:73:91`) and save.

---

## 🌐 Step 4: Configure the Web Client ID in the Project

When your app connects to Firebase Authentication, it requires a **Web Client ID** to negotiate the Google ID Token.

1. Scroll to the bottom of the **Project Settings** > **General** page, or open the [Google Cloud Credentials Console](https://console.cloud.google.com/apis/credentials).
2. Locate the **Web client (auto created by Google Service)** OAuth 2.0 client ID.
3. Copy its Value (it looks like `1234567890-abc123xyz.apps.googleusercontent.com`).
4. In your PK AI code, open `app/src/main/res/values/strings.xml`.
5. Replace the placeholder for `default_web_client_id` with your copied ID:
   ```xml
   <string name="default_web_client_id" translatable="false">YOUR_COPIED_CLIENT_ID.apps.googleusercontent.com</string>
   ```

---

## 🛡️ Step 5: Activate Google Provider in Firebase Auth

1. Go to **Authentication** section on the left sidebar in Firebase Console.
2. Under **Sign-in method**, click **Add new provider**.
3. Select **Google** and toggle **Enable**.
4. Configure the project support email and save.

---

## 🛠️ Step 6: Troubleshooting Errors

If your app reports `"Google Sign-In is not configured correctly on this device."` or `NoCredentialException`:
* **Mismatched SHA-1**: Ensure the computer compiling the APK has its exact SHA-1 fingerprint registered in Firebase. If you compile in a CI workflow, the signing key differs.
* **Incorrect Client ID**: Ensure `default_web_client_id` matches the auto-created **Web Client ID** (and NOT the Android client ID).
* **Play Services**: Ensure Google Play Services are running and signed-in on the testing emulator or device.
