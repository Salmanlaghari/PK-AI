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

let sdkPromise: Promise<any> | null = null;

/** Load the puter.js SDK once; resolves to window.puter. */
export function loadPuterSDK(): Promise<any> {
  const existing = (window as any).puter;
  if (existing) return Promise.resolve(existing);
  if (sdkPromise) return sdkPromise;

  sdkPromise = new Promise((resolve, reject) => {
    let settled = false;
    const done = (fn: () => void) => {
      if (!settled) {
        settled = true;
        fn();
      }
    };
    const timer = setTimeout(() => {
      done(() => reject(new Error("Puter SDK load nahi ho saka (timeout) — internet check karein.")));
    }, SDK_TIMEOUT_MS);

    const script = document.createElement("script");
    script.src = PUTER_SDK_URL;
    script.async = true;
    script.onload = () => {
      clearTimeout(timer);
      const puter = (window as any).puter;
      done(() =>
        puter
          ? resolve(puter)
          : reject(new Error("Puter SDK load ho gaya lekin tayyar nahi hua."))
      );
    };
    script.onerror = () => {
      clearTimeout(timer);
      done(() => reject(new Error("Puter SDK download nahi ho saka — internet check karein.")));
    };
    document.head.appendChild(script);
  });

  // Allow a retry on failure instead of caching a rejected promise forever.
  sdkPromise.catch(() => {
    sdkPromise = null;
  });
  return sdkPromise;
}

// ---------------------------------------------------------------------------
// Auth — one tap, free Puter account, per-user quota
// ---------------------------------------------------------------------------

export async function isPuterSignedIn(): Promise<boolean> {
  try {
    const puter = await loadPuterSDK();
    return !!(await puter.auth.isSignedIn());
  } catch {
    return false;
  }
}

/** One-tap sign-in via Puter's own popup. Resolves with the Puter user. */
export async function signInToPuter(): Promise<PuterUser> {
  const puter = await loadPuterSDK();
  // attempt_temp_user_creation: one-tap onboarding — auto-creates a throwaway
  // Puter account, no signup form. The user can convert to a full account later.
  // (If a popup is still needed, the native WebView handles it via onCreateWindow.)
  await puter.auth.signIn({ attempt_temp_user_creation: true });
  let username = "Puter User";
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

function classifyError(err: any): { error: string; needsAuth?: boolean; quotaExceeded?: boolean } {
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
        "Puter connect nahi hai — header se 'Connect' tap karke apne free Puter account se sign in karein, phir dobara try karein.",
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
        "Aapka free Puter quota filhal khatam ho gaya hai — thori der baad dobara try karein.",
    };
  }

  // Never surface raw SDK text: it may echo the user's prompt or PII.
  return { error: "Puter se jawab nahi mil saka — dobara try karein." };
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
    onProgress?.({ message: "Puter AI jawab tayyar kar raha hai..." });

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
          onProgress?.({ message: "Puter AI likh raha hai...", partialText: fullText });
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

    if (!fullText.trim()) throw new Error("Khali jawab mila — dobara try karein.");
    return { ok: true, text: fullText };
  } catch (err) {
    const c = classifyError(err);
    return { ok: false, ...c };
  }
}

// ---------------------------------------------------------------------------
// Image generation (FLUX.1-schnell via puter.ai.txt2img)
// ---------------------------------------------------------------------------

export async function puterGenerateImage(
  prompt: string,
  onProgress?: (p: PuterProgress) => void
): Promise<PuterMediaResult> {
  try {
    const puter = await loadPuterSDK();
    if (typeof puter.ai?.txt2img !== "function") {
      throw new Error("Is Puter version mein image generation nahi mili.");
    }
    onProgress?.({ message: "🖼️ Image ban rahi hai..." });

    // No model pinned — Puter's default image model is used.
    const img = await puter.ai.txt2img(prompt);
    const url = img?.src || img?.url || "";
    if (!url) throw new Error("Image bani lekin uska URL nahi mila.");
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
    const puter = await loadPuterSDK();
    if (typeof puter.ai?.txt2vid !== "function") {
      return {
        ok: false,
        error:
          "Is Puter version mein video generation nahi mili — text aur image istemal karein, ya Puter update ka intezar karein.",
      };
    }

    onProgress?.({ message: "🎬 Video ban raha hai... (1-3 minute lag sakte hain)" });
    // txt2vid resolves with a ready-to-play HTMLVideoElement once the
    // server-side render finishes — no client polling needed.
    const videoEl = await puter.ai.txt2vid(prompt, {
      model: "google/veo-3.1",
    });
    const url = videoEl?.src || "";
    if (!url) throw new Error("Video bana lekin uska URL nahi mila.");
    return { ok: true, url };
  } catch (err) {
    const c = classifyError(err);
    return { ok: false, ...c };
  }
}
