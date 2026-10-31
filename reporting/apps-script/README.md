# PK-AI AI-content report endpoint (no Firebase billing)

This Apps Script web app is an optional, no-cost-within-quota email transport for reports from PK-AI. It forwards a fixed, bounded report to `lovefaillaghari@gmail.com`; it does not accept a recipient address from the app, store full report records in Firebase, or use Cloud Functions. The public endpoint is not a secret. The script validates every field, applies a **best-effort** two-minute per-install cooldown through Apps Script cache (cache entries can be evicted), and enforces a hard cap of 25 report emails per UTC day. The install ID is only an abuse-throttling hint, not strong identity or authentication; the daily cap limits the effect if callers spoof it.

## Owner deployment steps

1. Sign in to the Google account that owns the support inbox and create a new Apps Script project at [script.google.com](https://script.google.com/).
2. Replace the default script with [`Code.gs`](Code.gs). In **Project Settings**, show the `appsscript.json` manifest and replace it with [`appsscript.json`](appsscript.json).
3. Save, then choose **Deploy → New deployment → Web app**. Set **Execute as** to the owner account and **Who has access** to **Anyone** so app users do not need to sign in. Google may restrict public deployment for some account/domain settings.
4. Deploy and approve the requested `script.send_mail` permission. Copy the deployed URL ending in `/exec`; do not use the `/dev` test URL. It must match `https://script.google.com/macros/s/<deployment-id>/exec`. The script emails only the fixed support address above.
5. For local builds, put `AI_REPORTS_ENDPOINT=<deployed /exec URL>` in the untracked `local.properties`. For GitHub releases, add the URL as the repository Actions **variable** named `AI_REPORTS_ENDPOINT` (not a secret); both release workflows already pass that variable into Gradle. The URL is public and is not a credential.
6. Build and test a report from both native chat and AI Hub. The Android client follows the Apps Script Content Service redirect to its JSON response. After changing the script, publish a new deployment version.

The app falls back to its disclosed email-draft/local-note choices while `AI_REPORTS_ENDPOINT` is blank. **Until the owner deploys the web app, configures the URL, and ships a build containing it, in-app report delivery is not active and the Google Play reporting requirement must not be claimed as complete.**

Google currently lists a daily email-recipient quota of 100 for consumer Gmail and 1,500 for Workspace accounts; quotas can change and are shared with other scripts under the account. This endpoint limits itself to 25 report emails per day. If Apps Script is blocked by account policy, the zero-billing Firebase alternative is a Firestore report queue on the Spark free quota, but that would require owner-created database/security rules and manual review in Firebase Console; it would not send email on its own.
