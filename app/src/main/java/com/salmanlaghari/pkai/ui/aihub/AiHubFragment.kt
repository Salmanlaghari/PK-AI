package com.salmanlaghari.pkai.ui.aihub

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.content.DialogInterface
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.JsPromptResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.webkit.WebViewAssetLoader
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.repository.AuthRepository
import com.salmanlaghari.pkai.databinding.FragmentAiHubBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import javax.inject.Inject

/**
 * AiHubFragment hosts the full-screen "Ultra AI 4" chat space (a bundled React app)
 * and connects it to a REAL Flow Music session.
 *
 * Option D architecture (WebView real session):
 *  - The user signs into https://flowmusic.app with their own Google account inside
 *    a WebView. Cookies + DOM storage persist across app restarts, so the session
 *    stays connected in the background.
 *  - When the user types a music prompt in the Ultra AI chat, we drive the Flow
 *    Music studio WebView (type prompt -> click Create -> watch for the audio
 *    result) via the injected flowmusic-automation.js engine, then stream the
 *    result back into the chat UI. The user never leaves the chat screen.
 */
@AndroidEntryPoint
class AiHubFragment : Fragment() {

    private var _binding: FragmentAiHubBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AiHubViewModel by viewModels()

    @Inject
    lateinit var authRepository: AuthRepository

    private val statusHandler = Handler(Looper.getMainLooper())
    private var lastStatusJson = ""
    private var engineConnectedToastShown = false
    /**
     * True when the last silent auto-connect was skipped because the only
     * authorized Google account differs from the PK-AI sign-in account.
     * Surfaced to the web UI so the banner can tell the user to tap
     * "Connect karein" and pick the PK-AI account (instead of failing
     * silently with no feedback on multi-account devices).
     */
    private var silentAccountMismatch = false

    /**
     * The "connect failed" dialog. Kept in a field so onDestroyView() can
     * dismiss it — an inline dialog would leak the window on rotation or
     * navigation (WindowLeaked).
     */
    private var connectFailedDialog: AlertDialog? = null

    /** True once the hidden backend engine WebView finished its first page load. */
    private var backendPageLoaded = false

    /** Holds the Puter auth popup dialog while it is open (null otherwise). */
    private var puterPopupDialog: android.app.AlertDialog? = null

    /** The WebView hosted inside [puterPopupDialog]; destroyed with the dialog. */
    private var puterPopupWebView: WebView? = null

    /**
     * Active JS dialog (confirm/alert/prompt) and its pending result, if any.
     * Tracked so a rotation or fragment teardown can dismiss the dialog and
     * settle the result — otherwise the window leaks and the page's JS thread
     * hangs forever waiting for an answer.
     */
    private var jsDialog: AlertDialog? = null
    private var pendingJsResult: JsResult? = null
    private var pendingJsPromptResult: JsPromptResult? = null

    /** Dismiss any active JS dialog and settle its pending result. */
    private fun dismissJsDialog() {
        jsDialog?.dismiss()
        jsDialog = null
        pendingJsResult?.cancel()
        pendingJsResult = null
        pendingJsPromptResult?.cancel()
        pendingJsPromptResult = null
    }

    /**
     * Dismiss the Puter auth popup and destroy its WebView on every path
     * (cancel, window.close(), fragment teardown, or a fresh popup replacing
     * the old one). A leaked WebView keeps its renderer process alive.
     */
    private fun dismissPuterPopup() {
        try {
            puterPopupDialog?.dismiss()
        } catch (_: Exception) {
        }
        puterPopupDialog = null
        try {
            puterPopupWebView?.destroy()
        } catch (_: Exception) {
        }
        puterPopupWebView = null
    }

    /**
     * Set when [autoInjectSession] already reloaded the engine with a fresh
     * session: the following [onEngineConnected] must show the toast but
     * skip its own reload, otherwise every cold open stacks two reloads.
     */
    private var suppressNextEngineReload = false

    /**
     * Session captured by the silent auto-connect while the backend engine
     * WebView had not loaded yet - injected into localStorage on page finish.
     * (Cookies are always injected immediately; they do not need the page.)
     */
    private var pendingAutoSession: JSONObject? = null

    @Inject
    lateinit var flowMusicSessionManager: FlowMusicSessionManager

    /** Cached automation engine, injected into the Flow Music WebView. */
    private val automationScript: String by lazy {
        try {
            requireContext().assets.open("ultra-ai-chat-space/flowmusic-automation.js")
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("AiHubFragment", "Failed to load flowmusic-automation.js", e)
            ""
        }
    }

    private val statusPoll = object : Runnable {
        override fun run() {
            probeFlowMusicSession()
            statusHandler.postDelayed(this, 2500)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        setupFlowMusicEngine()

        // Silent auto-connect: the PK-AI Google account powers the Flow Music
        // bridge automatically - no Browse UI, no extra popup here.
        restoreFlowMusicSessionSilently()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val webView = _binding?.webviewUltraAi
                if (webView != null && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    findNavController().navigateUp()
                }
            }
        })
    }

    // ==========================================================================
    // Flow Music engine (persistent real session + automation)
    // ==========================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupFlowMusicEngine() {
        // Persist cookies (including the Flow Music / Google session) to disk.
        CookieManager.getInstance().setAcceptCookie(true)

        // ---- Background engine WebView: holds the real session + runs automation
        binding.webviewFlowmusicBackend.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // A real Chrome UA improves compatibility with Google OAuth (avoids
            // the "disallowed_useragent" block that embedded WebViews can trigger).
            settings.userAgentString = CHROME_MOBILE_UA
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(FlowMusicNativeBridge(), "FlowMusicNative")
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    transport.webView = view
                    resultMsg.sendToTarget()
                    return true
                }
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d("FlowMusicJS", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()}")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val injectCss = """
                        var style = document.createElement('style');
                        style.innerHTML = 'header, nav, .top-bar, div[role="dialog"], footer { display: none !important; } body { padding-top: 0 !important; margin-top: 0 !important; background-color: #0d1117 !important; }';
                        document.head.appendChild(style);
                    """.trimIndent()
                    view?.evaluateJavascript(injectCss, null)
                    Log.d("AiHubFragment", "Flow Music engine page finished: $url")
                    if (automationScript.isNotBlank()) {
                        view?.evaluateJavascript(automationScript, null)
                    }
                    // Flush a session captured by the silent auto-connect before
                    // the engine finished loading.
                    backendPageLoaded = true
                    pendingAutoSession?.let { session ->
                        pendingAutoSession = null
                        injectLocalStorageAndReload(session)
                    }
                }
            }
            loadUrl(FLOW_MUSIC_URL)
        }

        statusHandler.post(statusPoll)
    }

    private fun probeFlowMusicSession() {
        val wv = _binding?.webviewFlowmusicBackend ?: return
        val js = "window.__FLOW_AUTOMATION__ && window.__FLOW_AUTOMATION__.probe ? window.__FLOW_AUTOMATION__.probe() : JSON.stringify({signedIn:false,hasStudio:false})"
        wv.evaluateJavascript(js) { raw ->
            val decoded = decodeJsString(raw) ?: return@evaluateJavascript
            if (decoded != lastStatusJson) {
                lastStatusJson = decoded
                dispatchStatusToJs(decoded)
                onEngineConnected(decoded)
            }
        }
    }

    /**
     * Silent auto-connect for the Flow Music bridge.
     *
     * Order of attempts (all invisible to the user):
     *  1. Persisted Supabase session - returned as-is when still valid,
     *     otherwise refreshed silently with the refresh token.
     *  2. For Google-signed-in (non-guest) users with no usable session: one
     *     silent Credential Manager attempt limited to already-authorized
     *     accounts. When the device can hand back a credential without UI,
     *     its ID token is exchanged for a bridge session.
     *
     * When everything fails the user keeps the manual connect entry points
     * (header button / banner) - guests always use those.
     */
    private fun restoreFlowMusicSessionSilently() {
        viewLifecycleOwner.lifecycleScope.launch {
            val session = flowMusicSessionManager.getValidSessionJson()
            if (session != null) {
                autoInjectSession(session)
                return@launch
            }
            val user = try {
                authRepository.getSession().first()
            } catch (e: Exception) {
                Log.w("AiHubFragment", "restoreFlowMusicSessionSilently: no user session")
                return@launch
            }
            if (user.isGuest || user.email.isNullOrBlank()) {
                // Guest users connect manually; nothing silent to do.
                return@launch
            }
            // Pass the already-read email down so the credential check uses
            // the same snapshot the gate above validated.
            val pkaiEmail = user.email!!
            if (silentGoogleBridgeConnect(pkaiEmail)) {
                flowMusicSessionManager.getValidSessionJson()?.let { autoInjectSession(it) }
            }
        }
    }

    /**
     * One silent Credential Manager attempt: only already-authorized Google
     * accounts, auto-select enabled. Returns true when a credential arrived
     * WITHOUT any user-visible UI and the bridge session was stored.
     *
     * The returned credential is matched against the PK-AI signed-in email:
     * on multi-account devices the bridge must never bind to a different
     * Google account than the one the user signed into PK-AI with.
     */
    private suspend fun silentGoogleBridgeConnect(pkaiEmail: String): Boolean {
        val clientId = try {
            getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            ""
        }
        if (clientId.isBlank()) return false

        return try {
            val credentialManager = CredentialManager.create(requireContext())
            // filterByAuthorizedAccounts = true keeps this silent in the
            // common case: the system only returns an already-authorized
            // account without a picker. (It does not strictly guarantee no
            // UI - the system may still show a sheet in edge cases - which
            // is why any exception below simply falls back to manual.)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = withContext(Dispatchers.IO) {
                credentialManager.getCredential(request = request, context = requireContext())
            }
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                if (!googleIdToken.id.equals(pkaiEmail, ignoreCase = true)) {
                    // Wrong account on a multi-account device: never bind the
                    // bridge to it. Flag the mismatch so the web banner can
                    // tell the user to connect manually with the PK-AI
                    // account (the manual picker lets them choose it).
                    Log.w("AiHubFragment", "Silent bridge credential is for a different account; skipping")
                    silentAccountMismatch = true
                    dispatchStatusToJs(statusJsonWithMismatchFlag())
                    return false
                }
                silentAccountMismatch = false
                flowMusicSessionManager.connectWithIdToken(googleIdToken.idToken)
            } else {
                false
            }
        } catch (e: Exception) {
            // Never swallow coroutine cancellation: the view lifecycle may be
            // gone (fragment popped / onDestroyView) and structured
            // concurrency must unwind instead of touching a detached fragment.
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Any other failure (incl. "UI would be required") simply means the user
            // stays on the manual connect path.
            Log.d("AiHubFragment", "Silent bridge connect unavailable: ${e.message}")
            false
        }
    }

    /**
     * Injects a silently-obtained session into the hidden backend engine.
     * Cookies are set immediately (they do not need the page); the
     * localStorage copy + reload wait for the engine's first page load.
     */
    private fun autoInjectSession(session: JSONObject) {
        activity?.runOnUiThread {
            injectSessionCookies(session)
            if (backendPageLoaded) {
                injectLocalStorageAndReload(session)
            } else {
                pendingAutoSession = session
            }
            lastStatusJson = ""
            // The reload above (or the pending one on page finish) already
            // boots the engine with this session; onEngineConnected must not
            // reload a second time when the probe reports signedIn.
            suppressNextEngineReload = true
            // Safety: if the engine page never finishes loading (or the probe
            // never reports signedIn), a leaked flag must not swallow a
            // genuinely needed reload forever.
            statusHandler.postDelayed({ suppressNextEngineReload = false }, 20000)
            statusHandler.postDelayed({ probeFlowMusicSession() }, 3000)
        }
    }

    private fun onEngineConnected(statusJson: String) {
        try {
            val obj = JSONObject(statusJson)
            val signedIn = obj.optBoolean("signedIn", false)
            if (signedIn && !engineConnectedToastShown) {
                engineConnectedToastShown = true
                if (suppressNextEngineReload) {
                    suppressNextEngineReload = false
                } else {
                    // Reload the engine so it picks up the freshly created session.
                    _binding?.webviewFlowmusicBackend?.reload()
                }
                Toast.makeText(requireContext(), "Ultra Chat AI connected ✓", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.w("AiHubFragment", "onEngineConnected: ${e.message}")
        }
    }

    /**
     * Builds the status JSON the web UI reads, injecting the
     * `accountMismatch` hint when the silent auto-connect was skipped for a
     * wrong-account credential. Only meaningful while signed out; a signed-in
     * engine clears the flag's relevance.
     */
    private fun statusJsonWithMismatchFlag(): String {
        val base = lastStatusJson.ifBlank { """{"signedIn":false,"hasStudio":false}""" }
        return try {
            val obj = JSONObject(base)
            if (silentAccountMismatch && !obj.optBoolean("signedIn", false)) {
                obj.put("accountMismatch", true)
            }
            obj.toString()
        } catch (e: Exception) {
            base
        }
    }

    private fun dispatchStatusToJs(statusJson: String) {
        activity?.runOnUiThread {
            val quoted = JSONObject.quote(statusJson)
            val js = """
                (function(){
                    try {
                        var data = JSON.parse($quoted);
                        window.__flowMusicStatus = data;
                        if (typeof window.onFlowMusicStatus === 'function') window.onFlowMusicStatus(data);
                        window.dispatchEvent(new CustomEvent('pkai:flowmusic_status', { detail: data }));
                    } catch(e) {}
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }

    private fun dispatchTrackResultToJs(resultJson: String) {
        activity?.runOnUiThread {
            val quoted = JSONObject.quote(resultJson)
            val js = """
                (function(){
                    try {
                        var data = JSON.parse($quoted);
                        if (typeof window.onFlowMusicTrackResult === 'function') window.onFlowMusicTrackResult(data);
                        window.dispatchEvent(new CustomEvent('pkai:flowmusic_track', { detail: data }));
                    } catch(e) {}
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }

    /** Delivers a real Flow Music AI chat reply to the Ultra AI chat WebView. */
    private fun dispatchChatResultToJs(resultJson: String) {
        activity?.runOnUiThread {
            val quoted = JSONObject.quote(resultJson)
            val js = """
                (function(){
                    try {
                        var data = JSON.parse($quoted);
                        if (typeof window.onFlowMusicChatResult === 'function') window.onFlowMusicChatResult(data);
                        window.dispatchEvent(new CustomEvent('pkai:flowmusic_chat', { detail: data }));
                    } catch(e) {}
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }

    /** Drives a REAL Flow Music AI chat answer inside the backend session. */
    private fun startFlowMusicChat(requestId: String?, prompt: String) {
        val wv = _binding?.webviewFlowmusicBackend
        if (wv == null) {
            dispatchChatResultToJs("""{"ok":false,"error":"Ultra AI 4 engine unavailable.","requestId":${JSONObject.quote(requestId ?: "")}}""")
            return
        }
        val quotedPrompt = JSONObject.quote(prompt)
        val quotedId = JSONObject.quote(requestId ?: "")
        val js = "if (window.__FLOW_AUTOMATION__ && window.__FLOW_AUTOMATION__.chat) { window.__FLOW_AUTOMATION__.chat($quotedId, $quotedPrompt); } else { window.FlowMusicNative && window.FlowMusicNative.onChatResult(JSON.stringify({ok:false,error:'Ultra AI 4 engine not ready. Please reconnect Ultra Chat AI.',requestId:$quotedId})); }"
        wv.evaluateJavascript(js, null)
    }

    /** Streams live generation progress into the Ultra AI chat bubble. */
    private fun dispatchProgressToJs(progressJson: String) {
        activity?.runOnUiThread {
            val quoted = JSONObject.quote(progressJson)
            val js = """
                (function(){
                    try {
                        var data = JSON.parse($quoted);
                        if (typeof window.onFlowMusicProgress === 'function') window.onFlowMusicProgress(data);
                        window.dispatchEvent(new CustomEvent('pkai:flowmusic_progress', { detail: data }));
                    } catch(e) {}
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }

    private fun startFlowMusicGeneration(requestId: String?, prompt: String) {
        val wv = _binding?.webviewFlowmusicBackend
        if (wv == null) {
            dispatchTrackResultToJs("""{"ok":false,"error":"Ultra AI 4 engine unavailable.","requestId":${JSONObject.quote(requestId ?: "")}}""")
            return
        }
        val quotedPrompt = JSONObject.quote(prompt)
        val quotedId = JSONObject.quote(requestId ?: "")
        val js = "if (window.__FLOW_AUTOMATION__ && window.__FLOW_AUTOMATION__.generate) { window.__FLOW_AUTOMATION__.generate($quotedId, $quotedPrompt); } else { window.FlowMusicNative && window.FlowMusicNative.onTrackResult(JSON.stringify({ok:false,error:'Ultra AI 4 engine not ready. Please reconnect Ultra Chat AI.',requestId:$quotedId})); }"
        wv.evaluateJavascript(js, null)
    }

    /**
     * Starts the Flow Music connection via the native Google account picker.
     *
     * Manual path (kept for guests and for cases where the silent
     * auto-connect could not obtain a session). Google-signed-in users
     * normally never reach this - the bridge connects automatically.
     * Fully in-app: the FlowMusic website is never opened.
     */
    fun connectFlowMusic() {
        activity?.runOnUiThread {
            startNativeFlowMusicConnect()
        }
    }

    /**
     * Connect: native Google account picker -> ID token -> backend session.
     * Fully in-app and browser-free. The FlowMusic website is NEVER opened:
     * an earlier Chrome Custom Tab fallback stranded users on the website
     * (the app redirect is not allowlisted backend-side), so every failure
     * now stays in-app with an honest error + retry instead.
     */
    private fun startNativeFlowMusicConnect() {
        val clientId = try {
            getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            ""
        }
        // The user is choosing an account manually now; the stale silent
        // mismatch hint no longer applies.
        silentAccountMismatch = false
        if (clientId.isBlank()) {
            // Build-time misconfiguration: no browser fallback exists anymore.
            Log.e("AiHubFragment", "Flow Music connect: default_web_client_id is blank")
            onFlowMusicConnectFailed(
                reason = "App ki setting adhoori hai (Google client id missing).",
                retryable = false
            )
            return
        }

        val credentialManager = CredentialManager.create(requireContext())
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = requireContext()
                )
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdToken.idToken
                    val email = googleIdToken.id
                    Log.i("AiHubFragment", "Native Google pop-up success for $email")

                    // Keep the PK-AI identity in sync with the same account.
                    try {
                        authRepository.loginWithGoogle(
                            idToken = idToken,
                            displayName = googleIdToken.displayName ?: "",
                            email = email,
                            photoUrl = googleIdToken.profilePictureUri?.toString() ?: ""
                        )
                    } catch (e: Exception) {
                        Log.w("AiHubFragment", "authRepository sync skipped: ${e.message}")
                    }

                    // Exchange the ID token for a real music-backend session.
                    when (val exchange = withContext(Dispatchers.IO) {
                        FlowMusicOAuth.exchangeIdTokenForSessionDetailed(idToken)
                    }) {
                        is FlowMusicOAuth.ExchangeResult.Success -> {
                            // Persist so the silent auto-connect revives it later.
                            try {
                                flowMusicSessionManager.connectWithSessionJson(exchange.session)
                            } catch (e: Exception) {
                                Log.w("AiHubFragment", "Could not persist bridge session: ${e.message}")
                            }
                            injectSessionIntoEngine(exchange.session)
                        }
                        is FlowMusicOAuth.ExchangeResult.Rejected -> {
                            // Backend refused the ID token - no browser
                            // fallback; report the real reason in-app.
                            // Classify by status so a 429 / 5xx is not
                            // misreported as "account refused".
                            val snippet = FlowMusicOAuth.sanitizedErrorSnippet(exchange.errorBody)
                            Log.w("AiHubFragment", "ID-token rejected (${exchange.httpCode})" + (snippet?.let { ": $it" } ?: ""))
                            val reason = when (exchange.httpCode) {
                                429 -> "Bahut zyada koshishen ho gayin - thodi der baad dobara try karein (429)."
                                in 500..599 -> "FlowMusic ka server abhi masla kar raha hai (code ${exchange.httpCode}) - thodi der baad try karein."
                                else -> buildString {
                                    append("FlowMusic ne Google account qabool nahi kiya (code ${exchange.httpCode}).")
                                    if (!snippet.isNullOrBlank()) append("\nWajah: $snippet")
                                }
                            }
                            onFlowMusicConnectFailed(reason = reason, retryable = true)
                        }
                        is FlowMusicOAuth.ExchangeResult.MalformedResponse -> {
                            // Bad server response (not a network problem).
                            // The stored snippet is raw - only the sanitized
                            // form may reach the UI or logs.
                            val malformedSnippet = FlowMusicOAuth.sanitizedErrorSnippet(exchange.body)
                            Log.w("AiHubFragment", "ID-token exchange: malformed server response" + (malformedSnippet?.let { ": $it" } ?: ""))
                            onFlowMusicConnectFailed(
                                reason = buildString {
                                    append("Server se ghalat jawab aaya - dobara try karein.")
                                    if (!malformedSnippet.isNullOrBlank()) append("\nWajah: $malformedSnippet")
                                },
                                retryable = true
                            )
                        }
                        FlowMusicOAuth.ExchangeResult.TransportError -> {
                            Log.w("AiHubFragment", "ID-token exchange transport error")
                            onFlowMusicConnectFailed(
                                reason = "Internet ya server se rabta nahi ho saka.",
                                retryable = true
                            )
                        }
                    }
                } else {
                    Log.w("AiHubFragment", "Unexpected credential type: ${credential.type}")
                    onFlowMusicConnectFailed(
                        reason = "Google account ki maloomat nahi mil saki.",
                        retryable = true
                    )
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                Log.d("AiHubFragment", "Native connect cancelled by user")
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                Log.w("AiHubFragment", "No Google account on device")
                onFlowMusicConnectFailed(
                    reason = "Device par koi Google account nahi mila.",
                    retryable = true
                )
            } catch (e: Exception) {
                Log.e("AiHubFragment", "Native connect failed", e)
                onFlowMusicConnectFailed(
                    reason = "Connect karte waqt kharabi ho gayi.",
                    retryable = true
                )
            }
        }
    }

    /**
     * Failed connect stays IN-APP: flips the web UI's button to its retry
     * state ("Dobara try karein") and shows a native dialog with the real
     * reason. The FlowMusic website is never opened.
     */
    private fun onFlowMusicConnectFailed(reason: String, retryable: Boolean) {
        // Non-retryable failures (e.g. a build-time misconfiguration) must
        // NOT flip the web banner to "Dobara try karein" - that would loop
        // forever on a problem retrying can never fix.
        if (retryable) dispatchConnectFailedToJs(reason)
        if (!isAdded) return
        activity?.runOnUiThread {
            if (!isAdded) return@runOnUiThread
            try {
                connectFailedDialog?.dismiss()
                val builder = AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.title_music_connect_failed))
                    .setMessage(reason)
                    .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
                if (retryable) {
                    builder.setPositiveButton(getString(R.string.btn_retry)) { d, _ ->
                        d.dismiss()
                        startNativeFlowMusicConnect()
                    }
                }
                // Manual session import: the backend rejects our Google ID
                // token (audience mismatch) and we cannot change their
                // config, so the user can paste their own FlowMusic web
                // session once instead. Never opens the website.
                builder.setNeutralButton(getString(R.string.btn_import_session)) { d, _ ->
                    d.dismiss()
                    showSessionImportDialog()
                }
                // Clear the field on dismiss so a dismissed dialog (holding
                // the Activity context) is not retained by the fragment.
                builder.setOnDismissListener { connectFailedDialog = null }
                connectFailedDialog = builder.create()
                connectFailedDialog?.show()
            } catch (e: Exception) {
                Log.w("AiHubFragment", "Could not show connect-failed dialog: ${e.message}")
            }
        }
    }

    /**
     * Manual FlowMusic session import ("jugar" for the audience mismatch we
     * cannot fix server-side). The user copies their own FlowMusic web
     * session (localStorage `sb-sb-auth-token`) once and pastes it here;
     * from then on the app persists and refreshes it like a normal session.
     * The pasted text is a live secret: it is never logged, the field is
     * cleared on submit, and only the parsed session reaches storage.
     */
    private fun showSessionImportDialog() {
        if (!isAdded) return
        activity?.runOnUiThread {
            if (!isAdded) return@runOnUiThread
            try {
                var importDialog: AlertDialog? = null
                val input = EditText(requireContext()).apply {
                    hint = getString(R.string.hint_paste_session)
                    isSingleLine = false
                    isVerticalScrollBarEnabled = true
                    movementMethod = ScrollingMovementMethod.getInstance()
                    // Keyboard's Done/✓ key acts as Connect, so the user never
                    // needs to scroll for the button.
                    imeOptions = EditorInfo.IME_ACTION_DONE
                    setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            importDialog?.getButton(DialogInterface.BUTTON_POSITIVE)
                                ?.performClick()
                            true
                        } else {
                            false
                        }
                    }
                    setOnTouchListener { v, event ->
                        // Let the field consume vertical scrolls itself instead
                        // of the dialog/window fighting over them.
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        if (event.action == MotionEvent.ACTION_UP ||
                            event.action == MotionEvent.ACTION_CANCEL
                        ) {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                }
                val container = FrameLayout(requireContext()).apply {
                    // Dialog message padding, roughly.
                    setPadding(64, 16, 64, 4)
                    // FIXED field height (not maxLines): maxLines does not cap a
                    // pasted ~4KB single-line session on all devices — the field
                    // inflated to 30+ lines and pushed Cancel/Connect off-screen.
                    // An exact height can never grow; overflow scrolls inside it.
                    val fieldHeightPx =
                        (120 * requireContext().resources.displayMetrics.density).toInt()
                    addView(
                        input,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            fieldHeightPx
                        )
                    )
                }
                importDialog = AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.title_import_session))
                    .setMessage(getString(R.string.msg_import_session_paste))
                    .setView(container)
                    .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
                    .setPositiveButton(getString(R.string.btn_import_connect)) { d, _ ->
                        val pasted = input.text.toString()
                        input.text?.clear()
                        d.dismiss()
                        importPastedSession(pasted)
                    }
                    .show()
                    .also { dlg ->
                        // Shrink the dialog above the keyboard instead of letting
                        // the keyboard cover the buttons.
                        dlg.window?.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        )
                    }
            } catch (e: Exception) {
                Log.w("AiHubFragment", "Could not show session-import dialog: ${e.message}")
            }
        }
    }

    /**
     * Validates pasted session JSON with the same strictness as the OAuth
     * session guard (real non-blank access_token + refresh_token, no error
     * key - org.json's optString() would accept a literal "null"), then
     * persists and injects it exactly like a successful native connect.
     */
    private fun importPastedSession(pasted: String) {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            val session: JSONObject? = try {
                val json = JSONObject(pasted.trim())
                val accessToken = json.opt("access_token") as? String
                val refreshToken = json.opt("refresh_token") as? String
                if (json.has("error") || accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) null
                // NOTE: no length check on the tokens - this Supabase project
                // issues short opaque refresh tokens (~12 chars); they are
                // valid and sustain the session for days. A cut-off paste
                // fails JSON parsing above instead.
                else json
            } catch (e: JSONException) {
                null
            }
            if (session == null) {
                Log.w("AiHubFragment", "Pasted session invalid (chars=${pasted.length})")
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.msg_import_session_invalid),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }
            val persisted = try {
                flowMusicSessionManager.connectWithSessionJson(session)
            } catch (e: Exception) {
                Log.w("AiHubFragment", "Could not persist pasted session: ${e.message}")
                false
            }
            if (!persisted) {
                Log.w("AiHubFragment", "Pasted session rejected by session manager")
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.msg_import_session_invalid),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }
            // Same path as a successful native connect: inject into the
            // engine, toast, and probe the session.
            injectSessionIntoEngine(session)
        }
    }

    /** Lets the web UI show its retry affordance with the real failure reason. */
    private fun dispatchConnectFailedToJs(reason: String) {
        activity?.runOnUiThread {
            val quotedReason = JSONObject.quote(reason)
            val js = """
                (function(){
                    try {
                        window.dispatchEvent(new CustomEvent('pkai:flowmusic_connect_failed', { detail: { reason: $quotedReason } }));
                    } catch(e) {}
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }


    /**
     * Sets the chunked `@supabase/ssr` session cookies for the engine host.
     * Works even before the engine page has loaded.
     */
    private fun injectSessionCookies(session: JSONObject) {
        // Current Google Flow Music stores its Supabase session in chunked
        // @supabase/ssr COOKIES (sb-sb-auth-token.0, .1, ...), NOT in
        // localStorage. Setting these cookies is what actually boots the
        // engine WebView already signed in.
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        val nowSec = System.currentTimeMillis() / 1000L
        val expiresAt = session.optLong(
            "expires_at",
            nowSec + session.optLong("expires_in", 3600L)
        )
        val maxAge = (expiresAt - nowSec).coerceAtLeast(60L)
        FlowMusicOAuth.buildSessionCookies(session).forEach { (name, value) ->
            val header = "$name=$value; path=/; domain=.flowmusic.app; " +
                "max-age=$maxAge; secure; samesite=lax"
            cookieManager.setCookie(FlowMusicOAuth.COOKIE_HOST, header, null)
        }
        cookieManager.flush()
    }

    /** Writes the legacy localStorage copy, then reloads the engine. */
    private fun injectLocalStorageAndReload(session: JSONObject) {
        val quotedSession = JSONObject.quote(session.toString())
        val quotedKey = JSONObject.quote(FlowMusicOAuth.STORAGE_KEY)
        val js = """
            (function(){
                try {
                    localStorage.setItem($quotedKey, $quotedSession);
                    return 'ok';
                } catch(e) { return 'err:' + e; }
            })();
        """.trimIndent()
        _binding?.webviewFlowmusicBackend?.evaluateJavascript(js) { _ ->
            CookieManager.getInstance().flush()
            // Reload so the engine boots with the freshly injected session.
            _binding?.webviewFlowmusicBackend?.reload()
        }
    }

    /** Persists the real session into the engine WebView and reloads it. */
    private fun injectSessionIntoEngine(session: JSONObject) {
        activity?.runOnUiThread {
            injectSessionCookies(session)
            injectLocalStorageAndReload(session)
            lastStatusJson = ""
            engineConnectedToastShown = false
            Toast.makeText(requireContext(), "Ultra Chat AI connected ✓", Toast.LENGTH_SHORT).show()
            statusHandler.postDelayed({ probeFlowMusicSession() }, 3000)
        }
    }

    /**
     * Disconnects the Flow Music account. Also drops the persisted bridge
     * session so the silent auto-connect does not immediately reconnect.
     */
    private fun disconnectFlowMusic() {
        viewLifecycleOwner.lifecycleScope.launch {
            flowMusicSessionManager.clear()
        }
        activity?.runOnUiThread {
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                _binding?.webviewFlowmusicBackend?.evaluateJavascript(
                    "try { localStorage.clear(); } catch(e) {}", null
                )
                _binding?.webviewFlowmusicBackend?.reload()
                lastStatusJson = ""
                dispatchStatusToJs("""{"signedIn":false,"hasStudio":false}""")
                Toast.makeText(requireContext(), "Ultra Chat AI disconnected", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.w("AiHubFragment", "disconnectFlowMusic: ${e.message}")
            }
        }
    }

    /** JS bridge exposed to the Flow Music WebView as window.FlowMusicNative. */
    private inner class FlowMusicNativeBridge {
        @JavascriptInterface
        fun onAutomationLog(msg: String?) {
            Log.d("FlowMusicAutomation", msg ?: "")
        }

        @JavascriptInterface
        fun onProgress(json: String?) {
            if (json.isNullOrBlank()) return
            Log.d("AiHubFragment", "Flow Music progress: $json")
            dispatchProgressToJs(json)
        }

        @JavascriptInterface
        fun onTrackResult(json: String?) {
            if (json.isNullOrBlank()) return
            val ok = try { JSONObject(json).optBoolean("ok", false) } catch (e: Exception) { false }
            Log.d("AiHubFragment", "Flow Music track result received (ok=$ok, len=${json.length})")
            dispatchTrackResultToJs(json)
        }

        @JavascriptInterface
        fun onChatResult(json: String?) {
            if (json.isNullOrBlank()) return
            // Never log the answer text: it can contain PII and Log.d is not
            // stripped in release builds.
            val ok = try { JSONObject(json).optBoolean("ok", false) } catch (e: Exception) { false }
            Log.d("AiHubFragment", "Flow Music chat result received (ok=$ok, len=${json.length})")
            dispatchChatResultToJs(json)
        }
    }

    // ==========================================================================
    // Ultra AI chat WebView
    // ==========================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webviewUltraAi
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(requireContext()))
            .build()

        webView.setBackgroundColor(0xFF020617.toInt())

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // Puter one-tap sign-in opens a popup via window.open() — without
            // multi-window support the popup dies silently and auth never
            // completes.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("AiHubFragment", "Ultra AI page finished: $url")

                val googleClientId = try {
                    getString(R.string.default_web_client_id)
                } catch (e: Exception) {
                    ""
                }
                val clientIdJs = JSONObject.quote(googleClientId)
                val js = """
                    window.GOOGLE_CLIENT_ID = $clientIdJs;
                    window.GOOGLE_REDIRECT_URI = "";
                    console.log("[AiHub] Injected GOOGLE_CLIENT_ID successfully");
                """.trimIndent()
                view?.evaluateJavascript(js, null)

                // Push the latest Flow Music status into the freshly loaded chat UI.
                if (lastStatusJson.isNotBlank()) {
                    dispatchStatusToJs(lastStatusJson)
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return super.shouldInterceptRequest(view, request)
                val intercepted = assetLoader.shouldInterceptRequest(url)
                if (intercepted != null) {
                    return intercepted
                }
                val urlString = url.toString()

                if (urlString.contains("flowmusic_track") || urlString.contains("soundhelix.com") || urlString.endsWith(".wav") || urlString.endsWith(".mp3")) {
                    try {
                        val stream: InputStream = requireContext().assets.open("ultra-ai-chat-space/assets/flowmusic_track.wav")
                        val mimeType = if (urlString.endsWith(".mp3")) "audio/mpeg" else "audio/wav"
                        val customResponse = WebResourceResponse(mimeType, "UTF-8", stream)
                        val headers = HashMap<String, String>()
                        headers["Access-Control-Allow-Origin"] = "*"
                        headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS, HEAD"
                        headers["Access-Control-Allow-Headers"] = "*"
                        headers["Accept-Ranges"] = "bytes"
                        customResponse.responseHeaders = headers
                        return customResponse
                    } catch (e: Exception) {
                        Log.w("AiHubFragment", "Audio asset fallback error: " + e.message)
                    }
                }

                if (urlString.contains("ultra-ai-chat-space")) {
                    try {
                        val path = when {
                            urlString.contains("/android_asset/") -> {
                                urlString.substringAfter("/android_asset/")
                            }
                            url.path != null && url.path!!.contains("ultra-ai-chat-space") -> {
                                "ultra-ai-chat-space" + url.path!!.substringAfter("ultra-ai-chat-space")
                            }
                            else -> null
                        }

                        if (path != null) {
                            val cleanPath = path.substringBefore("?").substringBefore("#")
                            val mimeType = when {
                                cleanPath.endsWith(".js") -> "application/javascript"
                                cleanPath.endsWith(".css") -> "text/css"
                                cleanPath.endsWith(".html") -> "text/html"
                                cleanPath.endsWith(".svg") -> "image/svg+xml"
                                cleanPath.endsWith(".png") -> "image/png"
                                cleanPath.endsWith(".json") -> "application/json"
                                else -> "application/octet-stream"
                            }
                            val stream: InputStream = requireContext().assets.open(cleanPath)
                            val response = WebResourceResponse(mimeType, "UTF-8", stream)
                            val headers = HashMap<String, String>()
                            headers["Access-Control-Allow-Origin"] = "*"
                            headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
                            headers["Access-Control-Allow-Headers"] = "*"
                            response.responseHeaders = headers
                            return response
                        }
                    } catch (e: Exception) {
                        Log.w("AiHubFragment", "Asset intercept notice: $urlString -> ${e.message}")
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("https://appassets.androidplatform.net")) {
                    return false
                }
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e("AiHubFragment", "WebView error: ${error?.description} on ${request?.url}")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            // window.confirm() from the web UI (e.g. the Puter disconnect
            // prompt): the default WebChromeClient never shows a dialog, so
            // the JS call would silently hang. Show a native confirm instead.
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val hostActivity = activity ?: return false
                dismissJsDialog()
                pendingJsResult = result
                jsDialog = AlertDialog.Builder(hostActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            // alert() with no UI would block the page's JS thread forever.
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val hostActivity = activity ?: return false
                dismissJsDialog()
                pendingJsResult = result
                jsDialog = AlertDialog.Builder(hostActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                val hostActivity = activity ?: return false
                dismissJsDialog()
                pendingJsPromptResult = result
                val input = EditText(hostActivity)
                input.setText(defaultValue)
                jsDialog = AlertDialog.Builder(hostActivity)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        result?.confirm(input.text.toString())
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            // Puter auth popup: puter.auth.signIn() calls window.open(). Show
            // the popup in a dialog WebView; Puter closes it via
            // window.close() once sign-in completes.
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val hostActivity = activity ?: return false
                // A previous popup that never closed would otherwise leak its
                // WebView when the dialog field is overwritten below.
                dismissPuterPopup()
                val popupWebView = WebView(hostActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.userAgentString = view?.settings?.userAgentString
                    // Puter auth only: block navigation away from Puter/OAuth
                    // hosts so an arbitrary page can't render inside app chrome.
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val raw = request?.url?.toString() ?: return true
                            if (raw.startsWith("about:")) return false
                            val host = request.url.host?.lowercase() ?: return true
                            val allowed = host == "puter.com" ||
                                host.endsWith(".puter.com") ||
                                host.endsWith(".google.com") ||
                                host.endsWith(".googleapis.com") ||
                                host.endsWith(".gstatic.com") ||
                                host.endsWith(".googleusercontent.com") ||
                                host == "github.com"
                            return !allowed
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onCloseWindow(window: WebView?) {
                            dismissPuterPopup()
                        }
                    }
                }
                puterPopupWebView = popupWebView
                puterPopupDialog = android.app.AlertDialog.Builder(hostActivity)
                    .setView(popupWebView)
                    .setOnCancelListener { dismissPuterPopup() }
                    .create()
                puterPopupDialog?.show()
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("AiHubJS", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()})")
                return true
            }
        }

        // Bridge exposed to the Ultra AI chat WebView as window.AndroidOAuth
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun startGoogleSignIn() {
                    Log.d("AiHubFragment", "startGoogleSignIn called from JS")
                    activity?.runOnUiThread { triggerGoogleSignIn() }
                }

                @JavascriptInterface
                fun startGoogleSignIn(clientId: String?, redirectUri: String?) {
                    Log.d("AiHubFragment", "startGoogleSignIn(clientId, redirectUri) called from JS")
                    activity?.runOnUiThread { triggerGoogleSignIn() }
                }

                /** Open the real Flow Music sign-in WebView (user's own Google account). */
                @JavascriptInterface
                fun connectFlowMusic() {
                    Log.d("AiHubFragment", "connectFlowMusic called from JS")
                    this@AiHubFragment.connectFlowMusic()
                }

                @JavascriptInterface
                fun openFlowMusicSignUp() {
                    Log.d("AiHubFragment", "openFlowMusicSignUp called from JS")
                    this@AiHubFragment.connectFlowMusic()
                }

                @JavascriptInterface
                fun openFlowMusicStudio() {
                    Log.d("AiHubFragment", "openFlowMusicStudio called from JS")
                    this@AiHubFragment.connectFlowMusic()
                }

                /** Returns the cached Flow Music session status as a JSON string. */
                @JavascriptInterface
                fun getFlowMusicStatus(): String {
                    return statusJsonWithMismatchFlag()
                }

                /** Drive real music generation inside the Flow Music session. */
                @JavascriptInterface
                fun generateFlowMusicTrack(requestId: String?, prompt: String?) {
                    // Never log the prompt itself: prompts can contain PII and
                    // Log.d is not stripped in release builds.
                    Log.d("AiHubFragment", "generateFlowMusicTrack called from JS (prompt len=${prompt?.length ?: 0})")
                    if (prompt.isNullOrBlank()) {
                        dispatchTrackResultToJs("""{"ok":false,"error":"Empty music prompt.","requestId":${JSONObject.quote(requestId ?: "")}}""")
                        return
                    }
                    activity?.runOnUiThread {
                        val signedIn = try {
                            val obj = JSONObject(lastStatusJson)
                            obj.optBoolean("signedIn", false)
                        } catch (e: Exception) {
                            false
                        }
                        if (!signedIn) {
                            startNativeFlowMusicConnect()
                        }
                        startFlowMusicGeneration(requestId, prompt)
                    }
                }

                /** Real Flow Music AI text answer for a chat prompt. */
                @JavascriptInterface
                fun generateFlowMusicChat(requestId: String?, prompt: String?) {
                    // Never log the prompt itself: prompts routinely contain
                    // PII and Log.d is not stripped in release builds.
                    Log.d("AiHubFragment", "generateFlowMusicChat called from JS (prompt len=${prompt?.length ?: 0})")
                    if (prompt.isNullOrBlank()) {
                        dispatchChatResultToJs("""{"ok":false,"error":"Empty prompt.","requestId":${JSONObject.quote(requestId ?: "")}}""")
                        return
                    }
                    activity?.runOnUiThread {
                        val signedIn = try {
                            val obj = JSONObject(lastStatusJson)
                            obj.optBoolean("signedIn", false)
                        } catch (e: Exception) {
                            false
                        }
                        if (!signedIn) {
                            dispatchChatResultToJs("""{"ok":false,"error":"Ultra Chat AI is not connected.","requestId":${JSONObject.quote(requestId ?: "")}}""")
                            return@runOnUiThread
                        }
                        startFlowMusicChat(requestId, prompt)
                    }
                }

                @JavascriptInterface
                fun disconnectFlowMusic() {
                    Log.d("AiHubFragment", "disconnectFlowMusic called from JS")
                    this@AiHubFragment.disconnectFlowMusic()
                }

                @JavascriptInterface
                fun playFlowMusicInBackend(trackUrl: String?) {
                    Log.d("AiHubFragment", "playFlowMusicInBackend: $trackUrl")
                    if (trackUrl.isNullOrBlank()) return
                    activity?.runOnUiThread {
                        val escapedUrl = JSONObject.quote(trackUrl)
                        binding.webviewFlowmusicBackend.evaluateJavascript(
                            "if (window.playTrack) { window.playTrack($escapedUrl); } else { new Audio($escapedUrl).play(); }",
                            null
                        )
                    }
                }

                @JavascriptInterface
                fun triggerFlowMusicAction(actionJson: String?) {
                    Log.d("AiHubFragment", "triggerFlowMusicAction: $actionJson")
                }

                @JavascriptInterface
                fun exitToHome() {
                    Log.d("AiHubFragment", "exitToHome called from JS")
                    activity?.runOnUiThread { findNavController().navigateUp() }
                }

                @JavascriptInterface
                fun downloadFile(fileUrl: String?, fileName: String?, mimeType: String?) {
                    Log.d("AiHubFragment", "downloadFile called from JS: $fileUrl, $fileName, $mimeType")
                    if (fileUrl.isNullOrBlank()) return
                    activity?.runOnUiThread {
                        downloadMediaToDevice(
                            fileUrl,
                            fileName ?: "ultra_ai_${System.currentTimeMillis()}",
                            mimeType ?: "*/*"
                        )
                    }
                }
            },
            "AndroidOAuth"
        )

        webView.setDownloadListener { url, _, _, mimetype, _ ->
            downloadMediaToDevice(url, "ultra_ai_${System.currentTimeMillis()}", mimetype ?: "*/*")
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/ultra-ai-chat-space/index.html")
    }

    // ==========================================================================
    // Native Google Sign-In (app-level identity, unchanged behaviour)
    // ==========================================================================

    private fun triggerGoogleSignIn() {
        try {
            val credentialManager = CredentialManager.create(requireContext())
            val clientId = try {
                getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                ""
            }

            if (clientId.isBlank()) {
                dispatchAuthErrorToJs("Google Client ID is not configured in strings.xml")
                return
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = credentialManager.getCredential(
                        request = request,
                        context = requireContext()
                    )
                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        val displayName = googleIdTokenCredential.displayName ?: ""
                        val email = googleIdTokenCredential.id
                        val photoUrl = googleIdTokenCredential.profilePictureUri?.toString() ?: ""

                        Log.i("AiHubFragment", "Google Sign-In success! Email: $email")

                        try {
                            authRepository.loginWithGoogle(
                                idToken = idToken,
                                displayName = displayName,
                                email = email,
                                photoUrl = photoUrl
                            )
                        } catch (e: Exception) {
                            Log.w("AiHubFragment", "Failed to update authRepository session: ${e.message}")
                        }

                        dispatchAuthSuccessToJs(idToken, email, displayName, photoUrl)
                    } else {
                        dispatchAuthErrorToJs("Unsupported credential type: ${credential.type}")
                    }
                } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                    dispatchAuthErrorToJs("Sign-In cancelled.")
                } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                    dispatchAuthErrorToJs("No Google account found on this device.")
                } catch (e: Exception) {
                    Log.e("AiHubFragment", "CredentialManager getCredential failed", e)
                    dispatchAuthErrorToJs(e.localizedMessage ?: "Google Sign-In failed")
                }
            }
        } catch (e: Exception) {
            Log.e("AiHubFragment", "Failed to initialize Google Sign-In", e)
            dispatchAuthErrorToJs(e.localizedMessage ?: "Failed to start Google Sign-In")
        }
    }

    private fun dispatchAuthSuccessToJs(token: String, email: String, name: String, picture: String) {
        activity?.runOnUiThread {
            val tokenJs = JSONObject.quote(token)
            val emailJs = JSONObject.quote(email)
            val nameJs = JSONObject.quote(name)
            val pictureJs = JSONObject.quote(picture)

            val js = """
                (function() {
                    var user = { name: $nameJs, email: $emailJs, picture: $pictureJs };
                    var token = $tokenJs;
                    if (typeof window.onAndroidGoogleAuthSuccess === 'function') {
                        window.onAndroidGoogleAuthSuccess(token, user);
                    }
                    if (typeof window.__onGoogleAuthSuccess === 'function') {
                        window.__onGoogleAuthSuccess(token, user);
                    }
                    window.dispatchEvent(new CustomEvent('pkai:auth_success', { detail: { token: token, user: user } }));
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }

    private fun dispatchAuthErrorToJs(error: String) {
        activity?.runOnUiThread {
            val errorJs = JSONObject.quote(error)
            val js = """
                (function() {
                    var err = $errorJs;
                    if (typeof window.onAndroidGoogleAuthError === 'function') {
                        window.onAndroidGoogleAuthError(err);
                    }
                    if (typeof window.__onGoogleAuthError === 'function') {
                        window.__onGoogleAuthError(err);
                    }
                    window.dispatchEvent(new CustomEvent('pkai:auth_error', { detail: { error: err } }));
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }

    // ==========================================================================
    // Lifecycle
    // ==========================================================================

    override fun onPause() {
        super.onPause()
        // Persist the Flow Music session cookies to disk.
        CookieManager.getInstance().flush()
    }

    override fun onDestroyView() {
        statusHandler.removeCallbacksAndMessages(null)
        connectFailedDialog?.dismiss()
        connectFailedDialog = null
        dismissJsDialog()
        dismissPuterPopup()
        CookieManager.getInstance().flush()
        _binding?.webviewFlowmusicBackend?.removeJavascriptInterface("FlowMusicNative")
        super.onDestroyView()
        _binding = null
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return try {
            val v = JSONTokener(raw).nextValue()
            v?.toString()
        } catch (e: Exception) {
            raw.trim('"')
        }
    }

    private fun downloadMediaToDevice(url: String, fileName: String, mimeType: String) {
        try {
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("Downloading from Ultra AI 4...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                if (mimeType.isNotBlank() && mimeType != "*/*") {
                    setMimeType(mimeType)
                }
            }
            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.enqueue(request)
            Toast.makeText(requireContext(), "Downloading $fileName to storage...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AiHubFragment", "Failed to start DownloadManager: ${e.message}", e)
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (err: Exception) {
                Toast.makeText(requireContext(), "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val FLOW_MUSIC_URL = "https://www.flowmusic.app"
        private const val CHROME_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
