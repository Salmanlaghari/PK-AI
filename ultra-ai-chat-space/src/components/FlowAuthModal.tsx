import { useState, useEffect, useCallback } from "react";
import { X } from "lucide-react";

interface FlowAuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  onAuthSuccess: (token: string, user: { name: string; email: string; picture: string }) => void;
}

const GOOGLE_CLIENT_ID =
  (import.meta.env as any)?.VITE_GOOGLE_CLIENT_ID ||
  (window as any)?.GOOGLE_CLIENT_ID ||
  "";
const REDIRECT_URI =
  (import.meta.env as any)?.VITE_GOOGLE_REDIRECT_URI ||
  (window as any)?.GOOGLE_REDIRECT_URI ||
  window.location.origin;

export default function FlowAuthModal({ isOpen, onClose, onAuthSuccess }: FlowAuthModalProps) {
  const [isSigningIn, setIsSigningIn] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setIsSigningIn(false);
      setError(null);
    }
  }, [isOpen]);

  // Listen for native events dispatched from Android WebView
  useEffect(() => {
    const handleAuthSuccessEvent = (e: any) => {
      if (e.detail?.token && e.detail?.user) {
        onAuthSuccess(e.detail.token, e.detail.user);
        setIsSigningIn(false);
      }
    };
    const handleAuthErrorEvent = (e: any) => {
      setError(e.detail?.error || "Google Sign-In failed");
      setIsSigningIn(false);
    };

    window.addEventListener("pkai:auth_success", handleAuthSuccessEvent);
    window.addEventListener("pkai:auth_error", handleAuthErrorEvent);

    return () => {
      window.removeEventListener("pkai:auth_success", handleAuthSuccessEvent);
      window.removeEventListener("pkai:auth_error", handleAuthErrorEvent);
    };
  }, [onAuthSuccess]);

  const handleGoogleSignIn = useCallback(() => {
    const androidOAuth = (window as any).AndroidOAuth;

    // 1. Android App Native Flow via CredentialManager
    if (androidOAuth && typeof androidOAuth.startGoogleSignIn === "function") {
      setIsSigningIn(true);
      setError(null);

      (window as any).__onGoogleAuthSuccess = (
        token: string,
        user: { name: string; email: string; picture: string }
      ) => {
        onAuthSuccess(token, user);
        setIsSigningIn(false);
      };

      (window as any).__onGoogleAuthError = (err: string) => {
        setError(err || "Google Sign-In failed");
        setIsSigningIn(false);
      };

      try {
        androidOAuth.startGoogleSignIn();
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to start Google Sign-In");
        setIsSigningIn(false);
      }
      return;
    }

    // 2. Web Browser Fallback (Popup flow)
    const effectiveClientId = GOOGLE_CLIENT_ID || (window as any).GOOGLE_CLIENT_ID;
    if (!effectiveClientId) {
      setError("Google OAuth is not configured. Set VITE_GOOGLE_CLIENT_ID in your environment.");
      return;
    }

    setIsSigningIn(true);
    setError(null);

    const scope = encodeURIComponent("openid email profile");
    const state = Math.random().toString(36).slice(2);
    const authUrl = new URL("https://accounts.google.com/o/oauth2/v2/auth");
    authUrl.searchParams.set("client_id", effectiveClientId);
    authUrl.searchParams.set("redirect_uri", REDIRECT_URI);
    authUrl.searchParams.set("response_type", "token");
    authUrl.searchParams.set("scope", scope);
    authUrl.searchParams.set("state", state);
    authUrl.searchParams.set("prompt", "select_account");

    const width = 500;
    const height = 600;
    const left = window.screenX + (window.outerWidth - width) / 2;
    const top = window.screenY + (window.outerHeight - height) / 2;
    const popup = window.open(
      authUrl.toString(),
      "Google OAuth",
      `width=${width},height=${height},left=${left},top=${top},popup=yes`
    );

    if (!popup) {
      setError("Popup blocked. Please allow popups for this site.");
      setIsSigningIn(false);
      return;
    }

    const pollTimer = setInterval(() => {
      try {
        if (popup.closed) {
          clearInterval(pollTimer);
          setIsSigningIn(false);
          return;
        }
        const popupUrl = popup.location.href;
        if (popupUrl.includes(REDIRECT_URI)) {
          clearInterval(pollTimer);
          popup.close();
          const url = new URL(popupUrl);
          const fragment = url.hash.slice(1);
          const params = new URLSearchParams(fragment);
          const accessToken = params.get("access_token");
          const returnedState = params.get("state");
          if (returnedState !== state) {
            setError("Invalid OAuth state. Please try again.");
            setIsSigningIn(false);
            return;
          }
          if (!accessToken) {
            setError("No access token received.");
            setIsSigningIn(false);
            return;
          }
          fetch("https://www.googleapis.com/oauth2/v3/userinfo", {
            headers: { Authorization: `Bearer ${accessToken}` },
          })
            .then((res) => {
              if (!res.ok) throw new Error("Failed to fetch user info");
              return res.json();
            })
            .then((userInfo) => {
              onAuthSuccess(accessToken, {
                name: userInfo.name || userInfo.email,
                email: userInfo.email,
                picture: userInfo.picture || "",
              });
              setIsSigningIn(false);
            })
            .catch((err) => {
              setError(err.message);
              setIsSigningIn(false);
            });
        }
      } catch {
        // Cross-origin access check
      }
    }, 500);
  }, [onAuthSuccess]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl w-full max-w-md overflow-hidden">
        <div className="flex items-center justify-between p-4 border-b border-slate-800">
          <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
            <span>Connect FlowMusic Account</span>
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-pink-950 text-pink-300 border border-pink-800">
              50 Daily Credits
            </span>
          </h2>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          <p className="text-sm text-slate-400 leading-relaxed">
            Sign in with your Google account to link your <strong className="text-slate-200">FlowMusic.app</strong> profile with Ultra AI 4. You get <span className="text-pink-400 font-semibold">50 Free Daily Credits</span> every day to generate songs, lyrics, and high-resolution media.
          </p>

          <button
            onClick={handleGoogleSignIn}
            disabled={isSigningIn}
            className="w-full flex items-center justify-center gap-3 px-4 py-3 rounded-xl bg-white text-slate-900 font-medium text-sm hover:bg-slate-100 disabled:opacity-60 transition-all shadow-md active:scale-[0.98]"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24">
              <path
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"
                fill="#4285F4"
              />
              <path
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                fill="#34A853"
              />
              <path
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                fill="#FBBC05"
              />
              <path
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                fill="#EA4335"
              />
            </svg>
            {isSigningIn ? "Connecting..." : "Continue with Google"}
          </button>

          <button
            onClick={() => {
              const androidOAuth = (window as any).AndroidOAuth;
              if (androidOAuth && typeof androidOAuth.openFlowMusicSignUp === "function") {
                androidOAuth.openFlowMusicSignUp();
              } else {
                window.open("https://flowmusic.app", "_blank");
              }
            }}
            className="w-full flex items-center justify-center gap-2 px-4 py-3 rounded-xl bg-gradient-to-r from-pink-600 via-rose-600 to-purple-600 hover:from-pink-500 hover:to-purple-500 text-white font-medium text-sm transition-all shadow-lg shadow-pink-600/20 active:scale-[0.98]"
          >
            <span>Sign Up Directly on FlowMusic.app</span>
          </button>

          {error && (
            <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-xs">
              {error}
            </div>
          )}

          <p className="text-[11px] text-slate-500 text-center">
            By connecting, you agree to use your own Google account for this session.
          </p>
        </div>
      </div>
    </div>
  );
}
