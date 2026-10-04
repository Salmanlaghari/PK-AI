// Puter.js Free AI Stack (https://developer.puter.com)
// ----------------------------------------------------------------------------
// Text, image AND video generation — free for every user via Puter's
// "User-Pays" model: each user covers their own AI usage through their own
// free Puter account. The developer (PK-AI) pays nothing, no API keys are
// hardcoded, and no tokens/cookies are ever pasted by the user.
//
//  - Text  : puter.ai.chat()      (GPT-4o-mini and 400+ other models)
//  - Image : puter.ai.txt2img()   (FLUX.1-schnell)
//  - Video : puter.video.generate() (Google Veo 3.1 Fast) — if the loaded
//            SDK version exposes it; otherwise a clear message is shown.
//
// Auth is one tap: puter.auth.signIn() opens Puter's own sign-in popup and
// returns an authenticated session. Nothing leaves the device except the
// user's prompts sent to Puter's API under their own account.
//
// NOTE: puter.js ships without bundled TypeScript types, so the SDK is
// accessed via (window as any).puter with defensive shape checks.

export interface PuterUser {
  username: string;
  uuid: string;
}

export interface PuterProgress {
  message: string;
  partialText?: string;
}

export interface PuterTextResult {
  ok: boolean;
  text?: string;
  error?: string;
  needsAuth?: boolean;
  quotaExceeded?: boolean;
}

export interface PuterMediaResult {
  ok: boolean;
  url?: string;
  error?: string;
  needsAuth?: boolean;
  quotaExceeded?: boolean;
}

const PUTER_SDK_URL = "https://js.puter.com/v2/";
const STORAGE_KEY = "pkai_puter_user";
const SDK_TIMEOUT_MS = 30000;

// In-flight SDK load only — never a settled promise, so a stale rejection can
// never wipe a newer generation and concurrent callers join one script load.
let sdkLoad: Promise<any> | null = null;

/** Raw script-tag load of the Puter SDK (single-flight via [sdkLoad]). */
function startSdkLoad(): Promise<any> {
  const p: Promise<any> = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    let settled = false;
    const done = (fn: () => void) => {
      if (!settled) {
        settled = true;
        fn();
      }
    };
    const timer = setTimeout(() => {
      script.remove(); // don't pile up dead script tags across retries
      done(() =>
        reject(internalError("AI load nahi ho saka (timeout) — internet check karein."))
      );
    }, SDK_TIMEOUT_MS);

    script.src = PUTER_SDK_URL;
    script.async = true;
    script.onload = () => {
      clearTimeout(timer);
      const puter = (window as any).puter;
      if (!puter) script.remove();
      done(() =>
        puter
          ? resolve(puter)
          : reject(internalError("AI load ho gaya lekin tayyar nahi hua."))
      );
    };
    script.onerror = () => {
      clearTimeout(timer);
      script.remove();
      done(() => reject(internalError("AI download nahi ho saka — internet check karein.")));
    };
    document.head.appendChild(script);
  });

  // Identity-checked settle: only the current generation clears the slot, so a
  // slow older load can never cancel a newer one, and failures stay retryable.
  sdkLoad = p;
  p.then(
    () => {
      if (sdkLoad === p) sdkLoad = null;
    },
    () => {
      if (sdkLoad === p) sdkLoad = null;
    }
  );
  return p;
}

/** Load the puter.js SDK once; resolves to window.puter. */
export function loadPuterSDK(): Promise<any> {
  const existing = (window as any).puter;
  if (existing) return Promise.resolve(existing);
  if (sdkLoad) return sdkLoad;
  return startSdkLoad();
}

/**
 * Force a fresh SDK load. Used when the cached SDK object is stale or partial
 * (e.g. window.puter exists but ai.txt2img is missing) — one retry before the
 * user ever sees a dead-end error. Joins an already in-flight load instead of
 * injecting a duplicate script. Auth state is cookie-based, so reloading the
 * script never signs the user out.
 */
export function reloadPuterSDK(): Promise<any> {
  if (sdkLoad) return sdkLoad;
  try {
    delete (window as any).puter;
  } catch {
    /* non-configurable — the fresh script overwrites it on load */
  }
  document
    .querySelectorAll(`script[src="${PUTER_SDK_URL}"]`)
    .forEach((s) => s.remove());
  return startSdkLoad();
}

// ---------------------------------------------------------------------------
// Auth — one tap, free AI account, per-user quota
// ---------------------------------------------------------------------------

/** True while the native sign-in popup is on screen. */
let puterPopupOpen = false;
/**
 * Resolves when the native layer reports the auth popup closed. The close is
 * NOT a failure by itself (see signInToPuter) — it only ends the wait; the
 * live session state decides the outcome.
 */
let popupClosedResolve: (() => void) | null = null;

if (typeof window !== "undefined") {
  // Dispatched by the native layer (AiHubFragment) when the auth popup
  // opens/closes. The SDK's signIn() promise never resolves after the popup
  // is gone, so without these the UI could hang on "Connecting..." forever.
  window.addEventListener("puter-popup-opened", () => {
    puterPopupOpen = true;
  });
  window.addEventListener("puter-popup-closed", () => {
    if (!puterPopupOpen) return;
    puterPopupOpen = false;
    const resolve = popupClosedResolve;
    popupClosedResolve = null;
    resolve?.();
  });
}

/**
 * Stable sign-in error codes so the UI can tell "user closed the popup"
 * from real failures without regex-sniffing message text.
 */
export const PUTER_ERR_POPUP_CLOSED = "PUTER_POPUP_CLOSED";
export const PUTER_ERR_TIMEOUT = "PUTER_TIMEOUT";
export const PUTER_ERR_SIGNIN_FAILED = "PUTER_SIGNIN_FAILED";

/**
 * Grace window after the popup closes for a late token postMessage.
 * Used on the timeout/rejection upgrade path, where the token may still
 * be in flight when the race already failed.
 */
const SESSION_SETTLE_MS = 4000;
/**
 * Short settle for the popup-closed path: a dismissed popup must feel
 * instant, while a genuine token postMessage lands well within this.
 */
const POPUP_CLOSED_SETTLE_MS = 1500;

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export type PuterSignInStage = "sdk" | "popup-wait";

export async function isPuterSignedIn(): Promise<boolean> {
  try {
    const puter = await loadPuterSDK();
    return !!(await puter.auth.isSignedIn());
  } catch {
    return false;
  }
}

/**
 * One-tap sign-in via the provider's own popup. Resolves with the user.
 *
 * The outcome is decided by the race winner, then by live session state:
 *  - signIn() RESOLVED → the SDK only resolves it once the auth token
 *    postMessage landed, so the session IS established: return success
 *    directly. A resolved sign-in is never reported as a cancellation.
 *  - popup CLOSED → after sign-up Puter closes the popup itself and the
 *    token postMessage can still be in flight, so one short settle wait
 *    (live session) decides before giving up as user cancellation.
 *  - REJECTED / TIMEOUT → the token may still be landing, so the session
 *    poll gets one chance to upgrade to success before the real error
 *    is surfaced.
 */
export async function signInToPuter(
  onStage?: (stage: PuterSignInStage) => void
): Promise<PuterUser> {
  if (!(window as any).puter) onStage?.("sdk");
  const puter = await loadPuterSDK();
  // NOTE: no attempt_temp_user_creation — Puter disabled temporary/guest
  // accounts server-side, so the flag cannot deliver its one-tap flow and
  // only changes the popup URL. Plain signIn() shows the normal account
  // popup (sign in / sign up with email+phone verification).
  onStage?.("popup-wait");
  const signIn: Promise<unknown> = puter.auth.signIn();
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(
      () =>
        reject(
          codedError(
            PUTER_ERR_TIMEOUT,
            "Connect poora nahi ho saka (time khatam ho gaya). Popup band ho gaya ho to dobara Connect dabayein."
          )
        ),
      90000
    );
  });
  // Settles when the native popup goes away. The close itself is not a
  // failure: after a successful sign-up the popup closes on its own and the
  // token postMessage can still be in flight.
  const popupClosed = new Promise<"popup-closed">((resolve) => {
    popupClosedResolve = () => resolve("popup-closed");
  });
  // Tag which racer won — the bare signIn() promise's own settlement was
  // previously discarded, which let a resolved sign-in be reported as a
  // cancellation when the session check failed to observe it in time.
  const signInSettled = signIn.then(
    () => "sign-in-resolved" as const,
    (err: unknown) => {
      throw err;
    }
  );
  let raceError: unknown = null;
  try {
    const winner = await Promise.race([signInSettled, timeout, popupClosed]);
    if (winner === "sign-in-resolved") {
      // The SDK promise itself resolved — the token postMessage landed, so
      // the session is established. Trust the SDK, don't gamble it on the
      // session poll.
      return await readPuterUser();
    }
    // Popup closed with the SDK promise still pending: race the still-pending
    // SDK promise against the settle window. A late token resolution upgrades
    // to success instead of being discarded as a user cancellation; an SDK
    // rejection during the window surfaces as the real error.
    const settled = await Promise.race([
      signInSettled.then(() => true as const),
      waitForPuterSession(POPUP_CLOSED_SETTLE_MS),
    ]);
    if (settled) {
      return await readPuterUser();
    }
  } catch (e) {
    raceError = e;
  } finally {
    if (timer) clearTimeout(timer);
    popupClosedResolve = null;
  }

  // The race rejected (SDK failure or 90s timeout): the token may still be
  // landing, so the session poll gets one chance to upgrade to success
  // before the failure is surfaced to the user.
  if (raceError && (await waitForPuterSession(SESSION_SETTLE_MS))) {
    return await readPuterUser();
  }

  if (raceError) throw toPuterSignInError(raceError);
  // Popup closed with no session and the SDK promise never settled — the
  // sign-in was dismissed before completing.
  throw codedError(
    PUTER_ERR_POPUP_CLOSED,
    "Sign-in popup band kar diya gaya. Dobara Connect dabayein aur popup mein sign-in poora karein."
  );
}

/** Internal error tagged with a stable code for the UI to switch on. */
function codedError(code: string, message: string): Error {
  const e = internalError(message);
  (e as any).code = code;
  return e;
}

/**
 * Session probe that reads ONLY the already-loaded SDK object — it never
 * triggers a fresh script injection (unlike isPuterSignedIn, which goes
 * through loadPuterSDK and would pile up dead script tags and 30s timeouts
 * if called on a tight poll loop).
 */
async function probePuterSession(): Promise<boolean> {
  const puter = (window as any).puter;
  if (!puter) return false;
  try {
    return !!(await puter.auth.isSignedIn());
  } catch {
    return false;
  }
}

/**
 * Poll the live SDK session briefly. The auth token postMessage can land a
 * beat after the popup closes (or after the SDK promise settles), so the
 * sign-in outcome waits for the real session state instead of the race.
 *
 * Hard-bounded to timeoutMs wall time: the absolute timeout promise wins
 * every race, so even a hanging probe can never push the wait past the
 * intended window.
 */
async function waitForPuterSession(timeoutMs: number): Promise<boolean> {
  let done = false;
  const expired = sleep(timeoutMs).then(() => {
    done = true;
    return false;
  });
  const probe = (async () => {
    while (!done) {
      if (await Promise.race([probePuterSession(), expired])) return true;
      await Promise.race([sleep(250), expired]);
    }
    return false;
  })();
  return Promise.race([probe, expired]);
}

/** Best-effort user read + cache once a session is confirmed live. */
async function readPuterUser(): Promise<PuterUser> {
  const puter = await loadPuterSDK();
  let username = "AI User";
  let uuid = "";
  try {
    const u = await puter.auth.getUser();
    username = u?.username || u?.email || username;
    uuid = u?.uuid || "";
  } catch {
    // getUser is best-effort; the session itself is what matters.
  }
  const user: PuterUser = { username, uuid };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
  } catch {
    // Private mode etc. — session still lives in the SDK.
  }
  return user;
}

/**
 * Normalize a sign-in race failure into an Error the UI can show in-app.
 * The SDK rejects with plain objects like {error:"auth_window_closed",
 * msg:"..."} — a closed popup is tagged as user cancellation (the modal
 * resets silently); real SDK failures (popup_blocked, not_available_in_app,
 * unsupported_origin, ...) surface their own safe msg verbatim so the user
 * sees the REAL reason, never a generic one.
 */
function toPuterSignInError(err: unknown): Error {
  if ((err as any)?.isPuterInternal) return err as Error; // our own timeout etc.
  const code = String((err as any)?.error || (err as any)?.code || "");
  const msg = String((err as any)?.msg || (err as any)?.message || "");
  if (code === "auth_window_closed") {
    return codedError(
      PUTER_ERR_POPUP_CLOSED,
      "Sign-in popup band kar diya gaya. Dobara Connect dabayein aur popup mein sign-in poora karein."
    );
  }
  if (msg) {
    const e = new Error(msg);
    (e as any).code = code || PUTER_ERR_SIGNIN_FAILED;
    return e;
  }
  return codedError(PUTER_ERR_SIGNIN_FAILED, "Connect nahi ho saka — dobara try karein.");
}

export async function signOutFromPuter(): Promise<void> {
  try {
    const puter = await loadPuterSDK();
    await puter.auth.signOut();
  } catch {
    // Even if the SDK call fails, drop the cached user.
  }
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

/** Cached user for instant UI paint; the live session is checked via isPuterSignedIn(). */
export function getCachedPuterUser(): PuterUser | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as PuterUser) : null;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Error classification → honest, friendly Roman Urdu messages
// ---------------------------------------------------------------------------

/** Internal (non-SDK) errors: tagged so classifyError surfaces them verbatim. */
function internalError(message: string): Error {
  const e = new Error(message);
  (e as any).isPuterInternal = true;
  return e;
}

function classifyError(err: any): { error: string; needsAuth?: boolean; quotaExceeded?: boolean } {
  // Our own tagged internal errors are safe to surface verbatim — they never
  // contain SDK text or user content.
  if (err?.isPuterInternal) {
    return { error: String(err?.message || "AI se jawab nahi mil saka.") };
  }
  const code = String(err?.code || "").toLowerCase();
  const raw = String(err?.message || err || "Unknown error");
  const msg = raw.toLowerCase();
  // Metadata only — never log prompts or user content (raw SDK messages may echo them).
  console.debug("[puter] call failed:", code || "(no code)");

  const needsAuth =
    code === "token_missing" ||
    code === "token_auth_failed" ||
    code === "account_is_not_verified" ||
    msg.includes("not signed in") ||
    msg.includes("sign in") ||
    msg.includes("unauthorized") ||
    msg.includes("unauthenticated") ||
    msg.includes("401") ||
    msg.includes("forbidden") ||
    msg.includes("403") ||
    msg.includes("please log in") ||
    msg.includes("login required");

  if (needsAuth) {
    return {
      needsAuth: true,
      error:
        "AI connect nahi hai — header se 'Connect' tap karke apne free AI account se sign in karein, phir dobara try karein.",
    };
  }

  const quotaExceeded =
    code === "too_many_requests" ||
    msg.includes("quota") ||
    msg.includes("rate limit") ||
    msg.includes("too many requests") ||
    msg.includes("429") ||
    msg.includes("insufficient") ||
    msg.includes("fair use") ||
    msg.includes("usage limit") ||
    msg.includes("quota exceeded") ||
    msg.includes("limit exceeded");

  if (quotaExceeded) {
    return {
      quotaExceeded: true,
      error:
        "Aapka free AI quota filhal khatam ho gaya hai — thori der baad dobara try karein.",
    };
  }

  // Never surface raw SDK text: it may echo the user's prompt or PII.
  return { error: "AI se jawab nahi mil saka — dobara try karein." };
}

// ---------------------------------------------------------------------------
// Text chat (streaming)
// ---------------------------------------------------------------------------

export async function puterChat(
  prompt: string,
  onProgress?: (p: PuterProgress) => void
): Promise<PuterTextResult> {
  try {
    const puter = await loadPuterSDK();
    onProgress?.({ message: "AI jawab tayyar kar raha hai..." });

    const stream = await puter.ai.chat(prompt, {
      // No model pinned — Puter's default chat model is used.
      stream: true,
    });

    let fullText = "";
    // Streaming path: async-iterable of { text } deltas.
    if (stream && typeof stream[Symbol.asyncIterator] === "function") {
      for await (const part of stream) {
        const delta = part?.text || "";
        if (delta) {
          fullText += delta;
          onProgress?.({ message: "AI likh raha hai...", partialText: fullText });
        }
      }
    }

    // Array shape: some SDK builds resolve a plain array of message objects.
    if (!fullText && Array.isArray(stream)) {
      const last = stream[stream.length - 1];
      const arrText = last?.message?.content?.[0]?.text || last?.text || "";
      if (arrText) fullText = String(arrText);
    }

    // Non-streaming fallback: some SDK shapes resolve the full response.
    if (!fullText) {
      const maybe =
        stream?.message?.content?.[0]?.text ||
        stream?.message?.content ||
        stream?.text ||
        (typeof stream === "string" ? stream : "");
      if (maybe) fullText = String(maybe);
    }

    if (!fullText.trim()) throw internalError("Khali jawab mila — dobara try karein.");
    return { ok: true, text: fullText };
  } catch (err) {
    const c = classifyError(err);
    return { ok: false, ...c };
  }
}

// ---------------------------------------------------------------------------
// Image generation (FLUX.1-schnell via puter.ai.txt2img)
// ---------------------------------------------------------------------------

/**
 * Resolve a puter SDK object exposing the given ai.* API. If the cached SDK
 * object is stale/partial, one fresh load is attempted first. A reload failure
 * (e.g. offline) throws its own actionable error instead of being swallowed,
 * so the user sees "internet check karein" rather than a generic message.
 */
async function ensurePuterApi(apiName: "txt2img" | "txt2vid"): Promise<any> {
  let puter: any = await loadPuterSDK();
  if (typeof puter?.ai?.[apiName] !== "function") {
    puter = await reloadPuterSDK();
  }
  return puter;
}

export async function puterGenerateImage(
  prompt: string,
  onProgress?: (p: PuterProgress) => void
): Promise<PuterMediaResult> {
  try {
    const puter = await ensurePuterApi("txt2img");
    if (typeof puter?.ai?.txt2img !== "function") {
      throw internalError("Image feature tayyar nahi ho saka — app band karke dobara kholein.");
    }
    onProgress?.({ message: "🖼️ Image ban rahi hai..." });

    // No model pinned — Puter's default image model is used.
    const img = await puter.ai.txt2img(prompt);
    const url = img?.src || img?.url || "";
    if (!url) throw internalError("Image bani lekin uska URL nahi mila.");
    return { ok: true, url };
  } catch (err) {
    const c = classifyError(err);
    return { ok: false, ...c };
  }
}

// ---------------------------------------------------------------------------
// Video generation (puter.ai.txt2vid — Google Veo)
// ---------------------------------------------------------------------------

export async function puterGenerateVideo(
  prompt: string,
  onProgress?: (p: PuterProgress) => void
): Promise<PuterMediaResult> {
  try {
    const puter = await ensurePuterApi("txt2vid");
    if (typeof puter?.ai?.txt2vid !== "function") {
      return {
        ok: false,
        error: "Video feature tayyar nahi ho saka — app band karke dobara kholein.",
      };
    }

    onProgress?.({ message: "🎬 Video ban raha hai... (1-3 minute lag sakte hain)" });
    // txt2vid resolves with a ready-to-play HTMLVideoElement once the
    // server-side render finishes — no client polling needed.
    const videoEl = await puter.ai.txt2vid(prompt, {
      model: "google/veo-3.1",
    });
    const url = videoEl?.src || "";
    if (!url) throw internalError("Video bana lekin uska URL nahi mila.");
    return { ok: true, url };
  } catch (err) {
    const c = classifyError(err);
    return { ok: false, ...c };
  }
}
