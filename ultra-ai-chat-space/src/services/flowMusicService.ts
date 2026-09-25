// FlowMusic Backend Engine Service (https://www.flowmusic.app/)
// ----------------------------------------------------------------------------
// This service bridges the Ultra AI 4 chat UI to a REAL Flow Music session that
// lives inside the PK-AI Android WebView (Option D: WebView real session).
//
//  - The user signs into Flow Music with their OWN Google account inside a
//    WebView; the session (cookies + DOM storage) persists in the background.
//  - Music generation is driven through that real session via the native
//    `AndroidOAuth.generateFlowMusicTrack()` bridge, and the resulting audio is
//    streamed back into the chat UI.
//  - No API keys are ever hardcoded. When the native bridge is unavailable
//    (e.g. running the web build in a plain browser) the service reports a
//    clear, honest error instead of faking a result.

export interface FlowMusicUser {
  id: string;
  name: string;
  email: string;
  picture: string;
  membership: "Ultra AI 4 Creator (Free Tier)" | "Ultra AI 4 Pro";
  dailyCreditsTotal: number;
  dailyCreditsRemaining: number;
  lastResetDate: string; // YYYY-MM-DD
}

export interface FlowMusicStatus {
  signedIn: boolean;
  email?: string;
  name?: string;
  hasStudio?: boolean;
  url?: string;
}

export interface FlowMusicTrackResult {
  ok: boolean;
  audioUrl?: string;
  title?: string;
  prompt?: string;
  error?: string;
}

export interface FlowMusicProgress {
  stage: string;
  message: string;
  [key: string]: unknown;
}

export interface GeneratedTrackResult {
  songTitle: string;
  artist: string;
  genre: string;
  audioUrl: string;
  coverImageUrl: string;
  duration: number;
  lyrics: string;
  creditsCost: number;
  creditsRemaining: number;
}

export interface GeneratedVisualResult {
  prompt: string;
  imageUrl: string;
  creditsCost: number;
  creditsRemaining: number;
}

const STORAGE_KEY = "pkai_flowmusic_session";

function getTodayString(): string {
  return new Date().toISOString().split("T")[0];
}

function getBridge(): any {
  return (window as any).AndroidOAuth || null;
}

// ---------------------------------------------------------------------------
// Real Flow Music session status (reported by the native WebView)
// ---------------------------------------------------------------------------
export function getFlowMusicStatus(): FlowMusicStatus {
  const bridge = getBridge();
  if (bridge && typeof bridge.getFlowMusicStatus === "function") {
    try {
      const raw = bridge.getFlowMusicStatus();
      if (raw) return JSON.parse(raw) as FlowMusicStatus;
    } catch (err) {
      console.debug("getFlowMusicStatus parse failed:", err);
    }
  }
  const cached = (window as any).__flowMusicStatus;
  if (cached) return cached as FlowMusicStatus;
  return { signedIn: false };
}

export function isFlowMusicConnected(): boolean {
  return !!getFlowMusicStatus().signedIn;
}

/** Open the real Flow Music sign-in WebView (user's own Google account). */
export function connectFlowMusic(): void {
  const bridge = getBridge();
  if (bridge && typeof bridge.connectFlowMusic === "function") {
    bridge.connectFlowMusic();
  } else {
    window.open("https://www.flowmusic.app", "_blank");
  }
}

export function disconnectFlowMusic(): void {
  const bridge = getBridge();
  if (bridge && typeof bridge.disconnectFlowMusic === "function") {
    bridge.disconnectFlowMusic();
  }
}

// ---------------------------------------------------------------------------
// Local profile (used only for the credits badge / greeting)
// ---------------------------------------------------------------------------
export function getFlowMusicSession(): FlowMusicUser {
  const today = getTodayString();
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed: FlowMusicUser = JSON.parse(raw);
      if (parsed.lastResetDate !== today) {
        parsed.dailyCreditsRemaining = parsed.dailyCreditsTotal || 50;
        parsed.lastResetDate = today;
        saveFlowMusicSession(parsed);
      }
      return parsed;
    }
  } catch (err) {
    console.warn("Could not read FlowMusic session:", err);
  }

  const initialUser: FlowMusicUser = {
    id: "fm_user_local",
    name: "",
    email: "",
    picture: "",
    membership: "Ultra AI 4 Creator (Free Tier)",
    dailyCreditsTotal: 50,
    dailyCreditsRemaining: 50,
    lastResetDate: today,
  };
  saveFlowMusicSession(initialUser);
  return initialUser;
}

export function saveFlowMusicSession(user: FlowMusicUser): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
  } catch (err) {
    console.warn("Could not save FlowMusic session:", err);
  }
}

/** Reflect the real signed-in Flow Music identity into the local profile. */
export function syncFlowMusicProfile(googleUser: { name: string; email: string; picture?: string }): FlowMusicUser {
  const current = getFlowMusicSession();
  const today = getTodayString();
  const updated: FlowMusicUser = {
    ...current,
    id: "fm_google_" + (googleUser.email || Math.random().toString(36).substring(2, 9)),
    name: googleUser.name || current.name,
    email: googleUser.email || current.email,
    picture: googleUser.picture || current.picture,
    lastResetDate: today,
  };
  saveFlowMusicSession(updated);
  return updated;
}

export function deductFlowCredits(amount: number): number {
  const user = getFlowMusicSession();
  user.dailyCreditsRemaining = Math.max(0, user.dailyCreditsRemaining - amount);
  saveFlowMusicSession(user);
  return user.dailyCreditsRemaining;
}

// ---------------------------------------------------------------------------
// Real music generation via the native Flow Music WebView session
// ---------------------------------------------------------------------------
export function requestFlowMusicTrack(
  prompt: string,
  onProgress?: (progress: FlowMusicProgress) => void
): Promise<FlowMusicTrackResult> {
  return new Promise((resolve) => {
    const bridge = getBridge();
    if (!bridge || typeof bridge.generateFlowMusicTrack !== "function") {
      resolve({
        ok: false,
        error:
          "Ultra AI 4 generation is only available inside the PK-AI Android app with a connected Ultra Chat AI account.",
      });
      return;
    }

    let settled = false;

    // Live progress listener (queued -> generating -> done), streamed by native.
    const progressHandler = (e: Event) => {
      if (settled) return;
      const detail = (e as CustomEvent).detail as FlowMusicProgress;
      if (detail && typeof onProgress === "function") onProgress(detail);
    };
    window.addEventListener("pkai:flowmusic_progress", progressHandler);

    const timeout = setTimeout(() => {
      if (settled) return;
      settled = true;
      cleanup();
      resolve({ ok: false, error: "Ultra AI 4 generation timed out. Please try again." });
    }, 270000);

    function cleanup() {
      clearTimeout(timeout);
      window.removeEventListener("pkai:flowmusic_progress", progressHandler);
      delete (window as any).onFlowMusicTrackResult;
    }

    (window as any).onFlowMusicTrackResult = (data: FlowMusicTrackResult) => {
      if (settled) return;
      settled = true;
      cleanup();
      resolve(data || { ok: false, error: "No response from Ultra AI 4." });
    };

    try {
      bridge.generateFlowMusicTrack(prompt);
    } catch (err) {
      settled = true;
      cleanup();
      resolve({ ok: false, error: err instanceof Error ? err.message : String(err) });
    }
  });
}

// ---------------------------------------------------------------------------
// Image generation (real, keyless — Pollinations Flux)
// ---------------------------------------------------------------------------
export function generateStrictVisual(prompt: string): GeneratedVisualResult {
  let cleanPrompt = prompt
    .replace(/^(please\s+)?(can\s+you\s+)?(make|generate|create|draw|paint|show|give|banao|dikhao|render)\s+(me\s+)?(an?\s+)?(image|photo|picture|pic|tasveer|wallpaper)\s+(of\s+|about\s+|ki\s+|ka\s+)?/i, "")
    .replace(/\s+(image|photo|picture|pic|draw|tasveer|banao)\s*$/i, "")
    .trim();

  if (!cleanPrompt) cleanPrompt = prompt.trim();

  const encoded = encodeURIComponent(cleanPrompt);
  const seed = Math.floor(Math.random() * 1000000);
  const imageUrl = `https://image.pollinations.ai/prompt/${encoded}?width=800&height=800&nologo=true&model=flux&seed=${seed}`;

  const creditsCost = 2;
  const creditsRemaining = deductFlowCredits(creditsCost);

  return { prompt: cleanPrompt, imageUrl, creditsCost, creditsRemaining };
}
