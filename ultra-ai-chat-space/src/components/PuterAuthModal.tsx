import { useState, useEffect, useCallback, useRef } from "react";
import { X, Sparkles, Loader2, CheckCircle2, Bell, ArrowRight, Zap } from "lucide-react";
import {
  signInToPuter,
  isPuterSignedIn,
  getCachedPuterUser,
  loadPuterSDK,
  PUTER_ERR_POPUP_CLOSED,
  type PuterUser,
} from "../services/puterService";

interface PuterAuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  onAuthSuccess: (user: PuterUser) => void;
}

/**
 * Premium notification-style free AI connect sheet.
 * Slides up from the bottom like a system notification — user taps once,
 * a free AI account is set up, and text+image+video become free.
 * No API keys, no tokens, no cookies to paste.
 */
export default function PuterAuthModal({ isOpen, onClose, onAuthSuccess }: PuterAuthModalProps) {
  const [isSigningIn, setIsSigningIn] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [stage, setStage] = useState<null | "sdk" | "popup-wait" | "popup-open">(null);
  const [hint, setHint] = useState<string | null>(null);
  const [sdkReady, setSdkReady] = useState(false);
  const [visible, setVisible] = useState(false);

  // Guards the connect attempt: handleClose supersedes the in-flight
  // attempt so a late onAuthSuccess can never flip auth state after the
  // user dismissed the sheet; mountedRef covers actual unmount.
  const attemptIdRef = useRef(0);
  const mountedRef = useRef(true);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (!isOpen) {
      setIsSigningIn(false);
      setError(null);
      setStage(null);
      setHint(null);
      setVisible(false);
      return;
    }
    // Trigger slide-up animation after mount.
    const raf = requestAnimationFrame(() => setVisible(true));
    // Preload the SDK while the sheet is open so the Connect tap needs no
    // network awaits: puter.auth.signIn() opens a real popup only while the
    // tap's user activation is alive. The button stays disabled until ready.
    setSdkReady(false);
    let cancelled = false;
    loadPuterSDK().catch(() => {
      // The tap will retry and surface the real error.
    }).finally(() => {
      if (!cancelled) setSdkReady(true);
    });
    return () => {
      cancelled = true;
      cancelAnimationFrame(raf);
    };
  }, [isOpen]);

  // Once the sign-in popup is requested, watch for it actually opening —
  // if it never opens the user would otherwise stare at a spinner forever.
  useEffect(() => {
    if (stage !== "popup-wait") return;
    const onOpened = () => {
      setStage("popup-open");
      setHint(null);
    };
    window.addEventListener("puter-popup-opened", onOpened);
    const t = setTimeout(() => {
      setHint("Popup nazar nahi aa raha? Cancel karke dobara Connect dabayein.");
    }, 12000);
    return () => {
      window.removeEventListener("puter-popup-opened", onOpened);
      clearTimeout(t);
    };
  }, [stage]);

  useEffect(() => {
    if (stage !== "popup-wait") return;
    const iv = setInterval(() => {
      try {
        const dlg = document.querySelector("puter-dialog");
        const btn = dlg?.shadowRoot?.querySelector(
          "#launch-auth-popup"
        ) as HTMLElement | null;
        if (btn) {
          clearInterval(iv);
          btn.click();
        }
      } catch {
        // SDK internals changed — the dialog is top-layer and tappable.
      }
    }, 800);
    return () => clearInterval(iv);
  }, [stage]);

  const handleClose = useCallback(() => {
    // Supersede any in-flight connect attempt: its late completion must
    // not touch UI state or fire onAuthSuccess after the user cancelled.
    attemptIdRef.current += 1;
    setVisible(false);
    setTimeout(onClose, 250);
  }, [onClose]);

  const handleConnect = useCallback(async () => {
    const attemptId = ++attemptIdRef.current;
    const isCurrent = () =>
      attemptIdRef.current === attemptId && mountedRef.current;
    setIsSigningIn(true);
    setError(null);
    setHint(null);
    try {
      // A cached session may already be valid (e.g. app was backgrounded).
      if (await isPuterSignedIn()) {
        if (!isCurrent()) return;
        const cached = getCachedPuterUser();
        onAuthSuccess(cached || { username: "AI User", uuid: "" });
        return;
      }
      // Guard the stage callback too: a superseded attempt must not touch
      // UI state (setStage is likewise skipped in the completion paths).
      const user = await signInToPuter((s) => {
        if (isCurrent()) setStage(s);
      });
      if (!isCurrent()) return;
      onAuthSuccess(user);
    } catch (err: any) {
      if (!isCurrent()) return;
      // A closed popup is the user changing their mind — not an error, so
      // reset silently. The service tags this with a stable code (the legacy
      // regex covers messages from older native builds).
      const code = String(err?.code || "");
      const msg = String(err?.msg || err?.message || "Connect nahi ho saka.");
      const userCancelled =
        code === PUTER_ERR_POPUP_CLOSED ||
        /dismiss|close|cancel|denied|band kar diya/i.test(msg);
      if (userCancelled) {
        setIsSigningIn(false);
        return;
      }
      // Real failures surface the REAL reason in-app (timeout, popup
      // blocked, SDK errors) — never a silent spinner or generic message.
      setError(msg);
    } finally {
      if (isCurrent()) {
        setIsSigningIn(false);
        setStage(null);
      }
    }
  }, [onAuthSuccess]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center sm:items-center bg-black/70 backdrop-blur-sm"
      onClick={handleClose}
    >
      {/* Premium notification sheet — slides up from bottom */}
      <div
        className={`w-full sm:max-w-sm rounded-t-3xl sm:rounded-3xl overflow-hidden shadow-2xl transform transition-transform duration-300 ease-out ${
          visible ? "translate-y-0" : "translate-y-full sm:translate-y-8 sm:opacity-0"
        }`}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Premium gradient header */}
        <div className="bg-gradient-to-br from-violet-600 via-indigo-600 to-cyan-500 p-5 pb-6">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <div className="w-11 h-11 rounded-2xl bg-white/20 backdrop-blur flex items-center justify-center">
                <Bell className="w-6 h-6 text-white" />
              </div>
              <div>
                <h2 className="text-white font-bold text-lg leading-tight">
                  Free AI Unlock
                </h2>
                <p className="text-white/80 text-xs">
                  Ek tap — sab kuch free
                </p>
              </div>
            </div>
            <button
              onClick={handleClose}
              className="p-1.5 rounded-full bg-white/15 text-white/90 hover:bg-white/25 transition-colors"
              title="Band karein"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* 3-step premium visual */}
          <div className="mt-4 flex items-center gap-1.5">
            {["Tap", "Account", "Free"].map((label, i) => (
              <div key={label} className="flex items-center gap-1.5 flex-1">
                <div className="flex flex-col items-center gap-1 flex-1">
                  <div className="w-7 h-7 rounded-full bg-white text-indigo-600 flex items-center justify-center text-xs font-bold">
                    {i + 1}
                  </div>
                  <span className="text-[10px] text-white/90 font-medium">{label}</span>
                </div>
                {i < 2 && <ArrowRight className="w-3.5 h-3.5 text-white/60 -mt-5" />}
              </div>
            ))}
          </div>
        </div>

        {/* Body */}
        <div className="bg-slate-900 p-5 pt-4">
          <div className="space-y-2.5 mb-4">
            {[
              { icon: Zap, text: "Chat — free AI models", color: "text-amber-400" },
              { icon: Sparkles, text: "Image — free HD generation", color: "text-cyan-400" },
              { icon: CheckCircle2, text: "Video — Google Veo generation", color: "text-emerald-400" },
            ].map(({ icon: Icon, text, color }) => (
              <div key={text} className="flex items-center gap-2.5 bg-slate-800/60 rounded-xl px-3 py-2.5">
                <Icon className={`w-4.5 h-4.5 ${color} shrink-0`} />
                <span className="text-sm text-slate-200">{text}</span>
              </div>
            ))}
          </div>

          {error && (
            <div className="mb-4 p-3 rounded-xl bg-red-950/60 border border-red-800/60 text-red-200 text-sm">
              {error}
            </div>
          )}

          <button
            onClick={handleConnect}
            disabled={isSigningIn || !sdkReady}
            className="w-full py-3.5 rounded-2xl bg-gradient-to-r from-violet-500 via-indigo-500 to-cyan-500 hover:from-violet-400 hover:via-indigo-400 hover:to-cyan-400 disabled:opacity-60 text-white font-bold text-[15px] shadow-lg shadow-indigo-500/30 transition-all active:scale-[0.98] flex items-center justify-center gap-2"
          >
            {!sdkReady ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" />
                Taiyar ho raha hai...
              </>
            ) : isSigningIn ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" />
                {stage === "popup-open"
                  ? "Popup mein sign-in poora karein..."
                  : stage === "popup-wait"
                    ? "Sign-in popup khul raha hai..."
                    : "Connecting..."}
              </>
            ) : (
              <>
                <Sparkles className="w-5 h-5" />
                Connect — Bilkul Free
              </>
            )}
          </button>

          {hint && !error && (
            <p className="mt-2 text-[11px] text-amber-300/90 text-center leading-relaxed">
              {hint}
            </p>
          )}

          <p className="mt-3 text-[11px] text-slate-500 text-center leading-relaxed">
            Sirf ek baar setup. Aapka apna free AI account banega — koi API key,
            token ya cookie nahi. Free quota har user ke apne account par hota hai.
          </p>

          {/* Bottom safe-area padding for gesture nav */}
          <div className="h-2" />
        </div>
      </div>
    </div>
  );
}
