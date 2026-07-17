# Google Sign-In Setup & Configuration Guide

This guide details the complete production diagnosis of the Google Sign-In setup, why it currently reports "not configured correctly" on debug devices, and the exact steps required from you to fully activate it.

---

## 🔍 Technical Diagnosis

Google Sign-In uses the modern **Android Credential Manager** and **Google Identity Services SDK**. Unlike legacy sign-in, this modern approach does not require the heavy `google-services.json` plugin to compile, but it **absolutely enforces** strict security checks on the device:

1. **OAuth 2.0 Web Client ID Verification**:
   The `default_web_client_id` inside `strings.xml` is currently a placeholder (`104828514-placeholder.apps.googleusercontent.com`). Google Play Services rejects this placeholder instantly, throwing a `NoCredentialException`.

2. **Signature SHA-1 Certificate Check**:
   Google's authentication servers verify that the SHA-1 signature of the installed APK matches the registered certificate in the Firebase/Google API Console. Since the debug APK is compiled locally, it uses your local computer's debug keystore, which is not yet registered in your Firebase Console.

---

## 🛠️ Step-by-Step Activation Guide (Action Required from You)

Please perform these simple console operations to activate Google Sign-In on your builds:

### Step 1: Extract your Keystore Signature Fingerprints
On the computer where you compile or package the APK, open your terminal at the root of the project and run:
```bash
./gradlew signingReport
```
Look at the terminal output and locate the **SHA-1** and **SHA-256** keys under the `debug` (or `release`) variant. They look like this:
- **SHA-1**: `AA:BB:CC:DD:EE:FF:11:22...`
- **SHA-256**: `11:22:33:44:55:66...`

---

### Step 2: Register Fingerprints in Firebase / Google Console
1. Open your [Firebase Console](https://console.firebase.google.com/).
2. Click the **Settings Gear Icon** in the top left, then select **Project Settings**.
3. Under the **General** tab, scroll down to **Your apps** and select your Android application (`com.salmanlaghari.pkai`).
4. Click **Add fingerprint**.
5. Paste your **SHA-1** key and save. Repeat the process to add your **SHA-256** key.

---

### Step 3: Enable Google Provider in Firebase Authentication
1. In the Firebase Console, go to **Build** > **Authentication** from the left-hand menu.
2. Go to the **Sign-in method** tab.
3. Click **Add new provider**, select **Google**, toggle **Enable**, configure your project support email, and click **Save**.

---

### Step 4: Configure Web Client ID in the App
1. On the same Firebase **Project Settings** page, or in the [Google Cloud Console Credentials](https://console.cloud.google.com/apis/credentials) page under **OAuth 2.0 Client IDs**, locate the client of type **Web application** (not Android client).
2. Copy the **Client ID** string (it ends with `.apps.googleusercontent.com`).
3. Open `app/src/main/res/values/strings.xml` in your repository.
4. Replace the placeholder with your copied client ID:
   ```xml
   <string name="default_web_client_id" translatable="false">YOUR_COPIED_WEB_CLIENT_ID.apps.googleusercontent.com</string>
   ```

Once completed, rebuild your APK, and Google Sign-In will instantly authenticate smoothly on any device!
