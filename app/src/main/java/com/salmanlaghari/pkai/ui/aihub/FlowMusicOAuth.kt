package com.salmanlaghari.pkai.ui.aihub

import android.net.Uri
import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Ultra Chat AI music engine - real session bootstrap.
 *
 * The music engine is powered by a real Google-authenticated backend session
 * (Supabase + Google OAuth). Google blocks OAuth inside embedded WebViews
 * ("Browser not supported" / disallowed_useragent), so the Google consent
 * screen runs in a Chrome Custom Tab and the PKCE authorization code is
 * captured through the `pkai://auth-callback` deep link. The resulting session
 * is then injected into the engine WebView, so the whole experience stays
 * inside the Ultra Chat AI interface - the user never has to open a separate
 * music site.
 *
 * SECURITY: [SUPABASE_ANON_KEY] is a PUBLIC, client-side publishable key - the
 * exact same key that is already shipped inside the public web bundle. It is
 * not a secret and grants no privileged (service-role) access. No private keys
 * or secrets are stored in this file.
 */
object FlowMusicOAuth {

    private const val TAG = "FlowMusicOAuth"

    /** Public Supabase project URL of the music engine backend. */
    const val SUPABASE_URL = "https://sb.flowmusic.app"

    /** Public publishable (anon) key - safe to embed, no privileged access. */
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVkbmpjY3FjbWJ4ZWF4YmlkaW5yIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzE1NjEwNjQsImV4cCI6MjA4NzEzNzA2NH0." +
            "XCXSuL7Th1xHecfRrP0vAOFmKwJxwBqVFLu06SxtVzg"

    /** Deep-link the OAuth provider redirects back to. */
    const val REDIRECT_URI = "pkai://auth-callback"

    /** localStorage key the engine backend uses to persist its session. */
    const val STORAGE_KEY = "sb-sb-auth-token"

    /**
     * Registered by AiHubFragment so MainActivity can forward the OAuth deep
     * link to the live engine WebView.
     */
    @Volatile
    var onCallback: ((Uri) -> Unit)? = null

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** RFC 7636 PKCE code_verifier. */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    /** RFC 7636 PKCE code_challenge (S256). */
    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    /**
     * Builds the authorize URL opened in the Chrome Custom Tab. `loginHint`
     * pre-selects the SAME Google account the user already signed into PK-AI
     * with, so the connection feels automatic.
     */
    fun buildAuthorizeUrl(codeChallenge: String, loginHint: String?): String {
        val sb = StringBuilder(SUPABASE_URL)
            .append("/auth/v1/authorize?provider=google")
            .append("&redirect_to=").append(Uri.encode(REDIRECT_URI))
            .append("&code_challenge=").append(Uri.encode(codeChallenge))
            .append("&code_challenge_method=s256")
        if (!loginHint.isNullOrBlank()) {
            sb.append("&login_hint=").append(Uri.encode(loginHint))
        }
        return sb.toString()
    }

    /**
     * Exchanges the PKCE authorization code for a real session object.
     * Returns the full Supabase session JSON (with `expires_at`) or null.
     */
    fun exchangeCodeForSession(authCode: String, codeVerifier: String): JSONObject? {
        return try {
            val payload = JSONObject()
                .put("auth_code", authCode)
                .put("code_verifier", codeVerifier)
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/token?grant_type=pkce")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Token exchange failed (${resp.code}): $text")
                    return null
                }
                val json = JSONObject(text)
                if (!json.has("expires_at")) {
                    val expiresIn = json.optLong("expires_in", 3600L)
                    json.put("expires_at", System.currentTimeMillis() / 1000L + expiresIn)
                }
                json
            }
        } catch (e: Exception) {
            Log.e(TAG, "exchangeCodeForSession error", e)
            null
        }
    }
}
