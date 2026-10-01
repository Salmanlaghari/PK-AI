import { useState, useEffect, useCallback } from "react";
import { X, Sparkles, Loader2, CheckCircle2 } from "lucide-react";
import {
  signInToPuter,
  isPuterSignedIn,
  getCachedPuterUser,
  type PuterUser,
} from "../services/puterService";

interface PuterAuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  onAuthSuccess: (user: PuterUser) => void;
}

/**
 * One-tap free connect: opens Puter's own sign-in popup. The user gets a
 * free Puter account with their own quota — no API keys, no tokens, no
 * cookies to paste. After this single connect, text + image + video are
 * free inside Ultra AI 4.
 */
export default function PuterAuthModal({ isOpen, onClose, onAuthSuccess }: PuterAuthModalProps) {
  const [isSigningIn, setIsSigningIn] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setIsSigningIn(false);
      setError(null);
    }
  }, [isOpen]);

  const handleConnect = useCallback(async () => {
    setIsSigningIn(true);
    setError(null);
    try {
      // A cached session may already be valid (e.g. app was backgrounded).
      if (await isPuterSignedIn()) {
        const cached = getCachedPuterUser();
        onAuthSuccess(cached || { username: "Puter User", uuid: "" });
        return;
      }
      const user = await signInToPuter();
      onAuthSuccess(user);
    } catch (err: any) {
      const msg = String(err?.message || "Connect nahi ho saka.");
      // A closed popup is the user changing their mind — not an error.
      if (/dismiss|close|cancel|denied/i.test(msg)) {
        setIsSigningIn(false);
        return;
      }
      setError(msg);
    } finally {
      setIsSigningIn(false);
    }
  }, [onAuthSuccess]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
      onClick={onClose}
    >
      <div
        className="w-full max-w-sm rounded-2xl bg-slate-900 border border-slate-700 p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-cyan-400" />
            Free AI Connect
          </h2>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            title="Band karein"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <p className="text-sm text-slate-300 mb-4 leading-relaxed">
          Ek tap par apna <b className="text-white">free Puter account</b> connect karein —
          phir <b className="text-white">text, image aur video</b> sab free, aapke apne
          quota mein. Koi API key, token ya cookie paste nahi karni.
        </p>

        <div className="mb-4 space-y-2 text-[13px] text-slate-400">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Chat — GPT-4o-mini aur 400+ models</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Image — FLUX HD generation</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Video — Google Veo generation</span>
          </div>
        </div>

        {error && (
          <div className="mb-4 p-3 rounded-xl bg-red-950/60 border border-red-800/60 text-red-200 text-sm">
            {error}
          </div>
        )}

        <button
          onClick={handleConnect}
          disabled={isSigningIn}
          className="w-full py-3 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 hover:from-cyan-400 hover:to-indigo-500 disabled:opacity-60 text-white font-bold text-sm shadow-lg shadow-cyan-500/20 transition-all active:scale-[0.98] flex items-center justify-center gap-2"
        >
          {isSigningIn ? (
            <>
              <Loader2 className="w-5 h-5 animate-spin" />
              Connecting...
            </>
          ) : (
            "Connect — Bilkul Free"
          )}
        </button>

        <p className="mt-3 text-[11px] text-slate-500 text-center leading-relaxed">
          Sirf ek baar connect karein. Free quota har user ke apne Puter account par hota hai.
        </p>
      </div>
    </div>
  );
}
