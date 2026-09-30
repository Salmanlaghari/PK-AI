package com.salmanlaghari.pkai.ui.aihub

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
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Ultra Chat AI music engine - real session bootstrap.
 *
 * The music engine is powered by a real Google-authenticated backend session
 * (Supabase + Google OAuth). The connect flow is fully in-app and browser-free:
 * a native Google account picker (Credential Manager) mints an ID token and
 * `grant_type=id_token` trades it for a Supabase session - no Chrome, no
 * external page. The resulting session is injected into the engine WebView,
 * so the whole experience stays inside the Ultra Chat AI interface - the user
 * never sees the FlowMusic website (no Browse / Studio UI, ever).
 *
 * NOTE (2026-09-30): an earlier Chrome Custom Tab PKCE fallback was removed.
 * It could never complete - the app's `pkai://auth-callback` redirect is not
 * allowlisted in the backend's Supabase project, so the tab stranded users on
 * the FlowMusic website. A failed connect now stays in-app with an honest
 * error and a retry affordance instead of opening the website.
 *
 * SECURITY: [SUPABASE_ANON_KEY] is a PUBLIC, client-side publishable key - the
 * exact same key that is already shipped inside the public web bundle. It is
 * not a secret and grants no privileged (service-role) access. No private keys
 * or secrets are stored in this file.
 */
object FlowMusicOAuth {

    private const val TAG = "FlowMusicOAuth"

    /**
     * True for a genuine refresh-token rejection.
     *
     * Supabase answers a bad/rotated refresh token with 400/401 and a JSON
     * error body. A body-string match on `invalid_grant` alone is too fragile
     * (wording changes), but status-only matching is too eager: an
     * intermediate layer (corporate proxy, WAF, captive portal, CDN) can also
     * produce 400/401, usually as an HTML page or an empty body. Middle
     * ground: 400/401 counts as a rejection only when the body is JSON AND
     * carries auth-error keys (Supabase emits {"error": ..., "error_description":
     * ...} or {"msg": ...}) — a JSON error page from a proxy/WAF without those
     * keys keeps the session. Only unambiguously transient
     * codes (408 timeout, 429 rate limit, 5xx) keep the stored session for a
     * later retry — otherwise a dead token would retry forever.
     */
    private fun isAuthRejection(httpCode: Int, body: String): Boolean {
        if (httpCode == 408 || httpCode == 429 || httpCode in 500..599) return false
        if (httpCode != 400 && httpCode != 401) return false
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            Log.w(TAG, "Empty $httpCode body on refresh; keeping session (possible proxy/captive portal)")
            return false
        }
        if (trimmed.startsWith("<")) {
            Log.w(TAG, "HTML $httpCode body on refresh; keeping session (possible proxy/WAF page)")
            return false
        }
        if (!trimmed.startsWith("{")) {
            Log.w(TAG, "Non-JSON $httpCode body on refresh; keeping session")
            return false
        }
        // JSON, but is it an AUTH error? Require Supabase-shaped keys so a
        // JSON error page from a proxy/WAF does not nuke a valid session.
        val looksLikeAuthError = try {
            val json = JSONObject(trimmed)
            json.has("error") || json.has("error_description") || json.has("msg") || json.has("message")
        } catch (_: Exception) {
            false
        }
        if (!looksLikeAuthError) {
            Log.w(TAG, "JSON $httpCode body without auth-error keys; keeping session")
            return false
        }
        Log.w(TAG, "Refresh token rejected ($httpCode): ${trimmed.take(160)}")
        return true
    }

    /** Public Supabase project URL of the music engine backend. */
    const val SUPABASE_URL = "https://sb.flowmusic.app"

    /** Public publishable (anon) key - safe to embed, no privileged access. */
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVkbmpjY3FjbWJ4ZWF4YmlkaW5yIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzE1NjEwNjQsImV4cCI6MjA4NzEzNzA2NH0." +
            "XCXSuL7Th1xHecfRrP0vAOFmKwJxwBqVFLu06SxtVzg"

    /** localStorage key the engine backend uses to persist its session. */
    const val STORAGE_KEY = "sb-sb-auth-token"

    /**
     * Cookie chunk size used by the live backend's `@supabase/ssr` storage.
     * Verified against a real Google Flow Music session (3180 chars/chunk).
     */
    const val COOKIE_CHUNK_SIZE = 3180

    /** Host whose cookies carry the engine backend session. */
    const val COOKIE_HOST = "https://www.flowmusic.app"

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

    /**
     * Result of trading a native Google ID token for a backend session.
     * Carries the backend's real answer so the UI can show an honest,
     * diagnosable error instead of a generic "failed".
     */
    sealed interface ExchangeResult {
        data class Success(val session: JSONObject) : ExchangeResult
        /** Backend answered but refused (e.g. 400 Bad ID token). */
        data class Rejected(val httpCode: Int, val errorBody: String) : ExchangeResult
        /** Backend answered 2xx but the body was not usable session JSON. */
        data class MalformedResponse(val bodySnippet: String) : ExchangeResult
        /** Transport problem (timeout, no network) - safe to retry. */
        data object TransportError : ExchangeResult
    }

    /**
     * Picks a short, human-readable snippet out of a backend error body for
     * display in the UI. Prefers Supabase's `error_description` / `error` /
     * `message` fields (that is exactly what identifies e.g. an untrusted
     * Google client id on `grant_type=id_token` failures), falls back to the
     * raw body, and scrubs anything that looks like a token so a misbehaving
     * backend can never leak secrets into the UI or logs. Null when there is
     * nothing worth showing.
     */
    fun sanitizedErrorSnippet(body: String): String? {
        if (body.isBlank()) return null
        val raw = try {
            val json = JSONObject(body)
            json.optString("error_description")
                .ifBlank { json.optString("error") }
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("msg") }
                .ifBlank { body }
        } catch (e: JSONException) {
            body
        }.trim()
        if (raw.isBlank()) return null
        return scrubTokens(raw).take(160).ifBlank { null }
    }

    /** Removes JWT-shaped strings and named token values from free text. */
    private fun scrubTokens(text: String): String =
        text.replace(Regex("[A-Za-z0-9_\\-]{16,}\\.[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}"), "[token]")
            .replace(Regex("(?i)(id_token|access_token|refresh_token)([\"'\\s:=]+)[^\"'\\s,}]{16,}"), "$1$2[token]")

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
    suspend fun exchangeIdTokenForSession(idToken: String): JSONObject? =
        when (val r = exchangeIdTokenForSessionDetailed(idToken)) {
            is ExchangeResult.Success -> r.session
            else -> null
        }

    /**
     * Detailed variant: distinguishes a backend REJECTION (the ID token was
     * refused - e.g. the backend does not trust this app's Google client id)
     * from a TRANSPORT error (timeout, no network - safe to retry).
     */
    suspend fun exchangeIdTokenForSessionDetailed(idToken: String): ExchangeResult {
        return try {
            val payload = JSONObject()
                .put("provider", "google")
                .put("id_token", idToken)
            val (code, text) = postJson("$SUPABASE_URL/auth/v1/token?grant_type=id_token", payload)
            if (code !in 200..299) {
                Log.w(TAG, "ID-token exchange failed ($code): ${sanitizedErrorSnippet(text) ?: "<no detail>"}")
                ExchangeResult.Rejected(code, text.take(500))
            } else {
                // A 2xx with an empty / non-JSON body (proxy HTML page,
                // truncated response, empty 204) is a BAD SERVER RESPONSE,
                // not a network problem - report it distinctly.
                val json = try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    Log.w(TAG, "ID-token exchange: 2xx with non-JSON body (${text.length} chars)")
                    return ExchangeResult.MalformedResponse(text.take(200))
                }
                ExchangeResult.Success(withExpiry(json))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "exchangeIdTokenForSession error", e)
            ExchangeResult.TransportError
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
                // A genuine auth failure drops the session; transient codes
                // (408/429/5xx) keep it for a later retry. 400/401 is decided
                // by isAuthRejection: status code plus a Supabase-shaped JSON
                // error body, so proxy/WAF/captive-portal pages keep the
                // session instead of forcing a re-login.
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

}
