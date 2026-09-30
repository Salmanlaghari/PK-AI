package com.salmanlaghari.pkai.ui.aihub

import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    /**
     * True only for a genuine refresh-token rejection: Supabase answers a
     * bad/rotated token with 400/401 and an `invalid_grant`-style body.
     * 429 (rate limit), 408 and every other failure are transient.
     */
    private fun isAuthRejection(httpCode: Int, body: String): Boolean {
        if (httpCode != 400 && httpCode != 401) return false
        return body.contains("invalid_grant") || body.contains("refresh_token_not_found")
    }

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
     * Cookie chunk size used by the live backend's `@supabase/ssr` storage.
     * Verified against a real Google Flow Music session (3180 chars/chunk).
     */
    const val COOKIE_CHUNK_SIZE = 3180

    /** Host whose cookies carry the engine backend session. */
    const val COOKIE_HOST = "https://www.flowmusic.app"

    /**
     * Registered by AiHubFragment so MainActivity can forward the OAuth deep
     * link to the live engine WebView.
     */
    @Volatile
    var onCallback: ((Uri) -> Unit)? = null

    /**
     * Buffers a deep link that arrived before the fragment registered its
     * callback (e.g. a cold start straight from the redirect).
     */
    @Volatile
    private var pendingUri: Uri? = null

    /**
     * Delivers a deep link to the live fragment, or buffers it until the
     * fragment registers (cold-start safety).
     */
    fun deliver(uri: Uri) {
        val cb = onCallback
        if (cb != null) {
            cb(uri)
        } else {
            pendingUri = uri
        }
    }

    /**
     * Registers the fragment callback and immediately flushes any deep link
     * that arrived while the fragment was not yet alive.
     */
    fun register(callback: (Uri) -> Unit) {
        onCallback = callback
        pendingUri?.let {
            pendingUri = null
            callback(it)
        }
    }

    /** Clears the fragment callback. */
    fun unregister() {
        onCallback = null
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * POSTs a JSON payload and returns (httpCode, body).
     *
     * Cancellable: uses [Call.enqueue] instead of the blocking [Call.execute]
     * so a cancelled coroutine (fragment gone, logout racing a refresh,
     * refresh aborted while the session mutex is held) cancels the socket
     * instead of holding the mutex for up to 60s.
     */
    private suspend fun postJson(url: String, payload: JSONObject): Pair<Int, String> =
        suspendCancellableCoroutine { cont ->
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            val call = http.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val text = it.body?.string().orEmpty()
                        if (cont.isActive) cont.resume(it.code to text)
                    }
                }
            })
        }

    /** Adds `expires_at` (epoch seconds) when the backend did not send one. */
    private fun withExpiry(json: JSONObject): JSONObject {
        if (!json.has("expires_at")) {
            val expiresIn = json.optLong("expires_in", 3600L)
            json.put("expires_at", System.currentTimeMillis() / 1000L + expiresIn)
        }
        return json
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /**
     * Builds the `@supabase/ssr` session cookies exactly the way the live
     * Google Flow Music backend stores them:
     *
     *   sb-sb-auth-token.0 = "base64-" + base64url(sessionJson)
     *   sb-sb-auth-token.1 = ... (only when the value exceeds COOKIE_CHUNK_SIZE)
     *
     * Current Flow Music keeps its session in these COOKIES (not localStorage),
     * so injecting them is what actually signs the engine WebView in.
     */
    fun buildSessionCookies(session: JSONObject): List<Pair<String, String>> {
        val encoded =
            "base64-" + base64Url(session.toString().toByteArray(Charsets.UTF_8))
        val cookies = ArrayList<Pair<String, String>>()
        var index = 0
        var chunk = 0
        while (index < encoded.length) {
            val end = minOf(index + COOKIE_CHUNK_SIZE, encoded.length)
            cookies.add("$STORAGE_KEY.$chunk" to encoded.substring(index, end))
            index = end
            chunk++
        }
        return cookies
    }

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
     * Exchanges a NATIVE Google ID token (obtained from the in-app Google
     * account picker via Credential Manager) for a real Supabase session.
     *
     * This is the browser-free path the user asked for: the Google account
     * picker pops up INSIDE Ultra Chat AI (exactly like the PK-AI sign-in), and
     * the returned ID token is traded for a backend session with
     * `grant_type=id_token` - no Chrome, no external page.
     *
     * Suspend + cancellable: safe to call while holding the session mutex.
     *
     * Returns the full Supabase session JSON (with `expires_at`) or null.
     */
    suspend fun exchangeIdTokenForSession(idToken: String): JSONObject? {
        return try {
            val payload = JSONObject()
                .put("provider", "google")
                .put("id_token", idToken)
            val (code, text) = postJson("$SUPABASE_URL/auth/v1/token?grant_type=id_token", payload)
            if (code !in 200..299) {
                Log.w(TAG, "ID-token exchange failed ($code): $text")
                return null
            }
            withExpiry(JSONObject(text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "exchangeIdTokenForSession error", e)
            null
        }
    }

    /**
     * Refreshes an existing Supabase session with its refresh token.
     *
     * This is what keeps the Flow Music bridge connected SILENTLY across app
     * restarts - no Google popup, no Custom Tab, no user interaction at all.
     *
     * The result distinguishes a real AUTH REJECTION (revoked / rotated /
     * expired refresh token - the stored session must be dropped) from a
     * TRANSPORT failure (timeout, no network - the stored session must be
     * KEPT so the next attempt can retry instead of forcing manual re-auth).
     */
    sealed interface RefreshResult {
        data class Success(val session: JSONObject) : RefreshResult
        data object AuthRejected : RefreshResult
        data object TransportError : RefreshResult
    }

    suspend fun refreshSession(refreshToken: String): RefreshResult {
        return try {
            val payload = JSONObject().put("refresh_token", refreshToken)
            val (code, text) = postJson("$SUPABASE_URL/auth/v1/token?grant_type=refresh_token", payload)
            if (code !in 200..299) {
                Log.w(TAG, "Session refresh failed ($code): $text")
                // Only a genuine auth failure drops the session: Supabase
                // answers a bad/rotated refresh token with 400/401 plus
                // an invalid_grant body. Everything else - 429 rate
                // limiting, 408 timeouts, 5xx, proxy-generated 4xx - is
                // transient and must NOT destroy the stored session.
                return if (isAuthRejection(code, text)) RefreshResult.AuthRejected
                else RefreshResult.TransportError
            }
            RefreshResult.Success(withExpiry(JSONObject(text)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "refreshSession error", e)
            RefreshResult.TransportError
        }
    }

    /**
     * Exchanges the PKCE authorization code for a real session object.
     * Suspend + cancellable. Returns the full Supabase session JSON
     * (with `expires_at`) or null.
     */
    suspend fun exchangeCodeForSession(authCode: String, codeVerifier: String): JSONObject? {
        return try {
            val payload = JSONObject()
                .put("auth_code", authCode)
                .put("code_verifier", codeVerifier)
            val (code, text) = postJson("$SUPABASE_URL/auth/v1/token?grant_type=pkce", payload)
            if (code !in 200..299) {
                Log.e(TAG, "Token exchange failed ($code): $text")
                return null
            }
            withExpiry(JSONObject(text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "exchangeCodeForSession error", e)
            null
        }
    }
}
