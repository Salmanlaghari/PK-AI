import { useState, useRef, useEffect, useCallback } from "react";
import { Bot, Loader2, Plug } from "lucide-react";
import Sidebar from "./components/Sidebar";
import Header from "./components/Header";
import ChatMessage from "./components/ChatMessage";
import ChatInput from "./components/ChatInput";
import SettingsModal from "./components/SettingsModal";
import VoiceModal from "./components/VoiceModal";
import ImageModal from "./components/ImageModal";
import FlowAuthModal from "./components/FlowAuthModal";
import { models, defaultModel } from "./data/models";
import { sessions as initialSessions } from "./data/sessions";
import type { Message, AIModel } from "./types";
import {
  getFlowMusicSession,
  getFlowMusicStatus,
  connectFlowMusic,
  requestFlowMusicTrack,
  requestFlowMusicChat,
  syncFlowMusicProfile,
  type FlowMusicUser,
  type FlowMusicStatus,
} from "./services/flowMusicService";
import {
  isPuterSignedIn,
  getCachedPuterUser,
  signOutFromPuter,
  puterChat,
  puterGenerateImage,
  puterGenerateVideo,
  type PuterUser,
} from "./services/puterService";
import PuterAuthModal from "./components/PuterAuthModal";

function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

function getTimestamp() {
  return new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function createWelcomeMessage(model: AIModel): Message {
  return {
    id: generateId(),
    sender: "ai",
    text: `Namaste! Main ${model.name} hoon — Ultra AI 4. Header se "Connect" tap karke apna free Puter account connect karein, phir mujhse text, HD image aur video — teeno free mein banwayein (aapke apne quota mein). Gaane ke liye Ultra Chat AI (Pro) connect karein.`,
    timestamp: getTimestamp(),
    modelName: `${model.name} × Puter AI`,
  };
}

const MUSIC_RE = /(song|music|audio|gana|gaana|track|beat|melody|tune|dhun|compose|instrumental|remix|vocal)/i;
const NON_MUSIC_RE = /(image|photo|picture|pic|tasveer|wallpaper|video|clip|code|function|program)/i;
// Puter free stack: image & video intents route to puter.ai.txt2img / video.
// Noun-first routing with a verb fallback, word-boundaried throughout so
// substrings ("topic" -> "pic", "clipboard" -> "clip") never match. Bare nouns
// ("photo", "tasveer") route to image gen; verbs ("draw", "paint", "sketch",
// plus explicit generation verbs) only when an art noun follows in the SAME
// sentence. Ambiguous nouns ("icon", "logo", "portrait", "artwork") are never
// bare nouns — they need an explicit verb — so "what does the icon do" stays
// text while "generate a portrait of a cat" still generates.
const ART_NOUN_SRC =
  "images?|photos?|pictures?|drawings?|tasveer(en)?|pics?|wallpapers?|paintings?|sketch(es)?";
const AMBIG_ART_NOUN_SRC = "portraits?|artworks?|logos?|icons?";
const IMAGE_RE = new RegExp(`\\b(?:${ART_NOUN_SRC})\\b`, "i");
const IMAGE_VERB_RE = new RegExp(
  `\\b(?:draw|paint|sketch|create|make|generate|design|render|illustrate)(?:ing|ed|s)?\\b(?=[^.?!\\n]{0,40}\\b(?:${ART_NOUN_SRC}|${AMBIG_ART_NOUN_SRC})\\b)`,
  "i"
);
// Interrogative/analytical prompts ("What makes a portrait good?") aren't
// media requests — unless the prompt OPENS with an explicit generation
// request ("Can you draw a picture of a cat?", "How do I make a video?"),
// as opposed to third-person analytical verbs ("what makes…", "what creates…").
const QUESTION_RE = /^\s*(what|how|why|when|where|which|who|whom|whose|explain|describe|tell\s+me)\b/i;
const REQUEST_LEAD_RE =
  /^\s*(?:please\s+|hey[,.]?\s+|how\s+(?:can|would|should)\s+i\s+|how\s+to\s+|tell\s+me\s+(?:how\s+to\s+)?|i\s+(?:want|need)\s+(?:you\s+)?to\s+|(?:can|could|would)\s+you\s+(?:please\s+)?|how\s+do\s+i\s+)?(?:draw|paint|sketch|create|make|generate|design|render|illustrate|record|film|shoot)\b/i;
// "video call" / "video chat" / "video conference" are never generation requests.
const VIDEO_NOUN_SRC =
  "(?:videos?|clips?|animations?|films?|movies?)(?!\\s+(?:call(?:ing|s)?|chat(?:ting|s)?|conference)\\b)";
const VIDEO_RE = new RegExp(`\\b${VIDEO_NOUN_SRC}\\b`, "i");

function isMusicPrompt(text: string): boolean {
  return MUSIC_RE.test(text) && !NON_MUSIC_RE.test(text);
}

// Question-shaped prompts need a closely-governed generation request:
// verb + (article) + (up to 2 adjectives) + art noun, with no possessive,
// demonstrative or qualitative adjective in between. "How can I draw a
// portrait in oil?" yes; "How to draw better portraits?" no.
const IMAGE_TIGHT_VERB_RE = new RegExp(
  `\\b(?:draw|paint|sketch|create|make|generate|design|render|illustrate)(?:ing|ed|s)?\\s+(?:(?:a|an|the|some)\\s+)?(?!(?:this|that|these|those|my|your|his|her|its|our|their|better|best|good|great|nicer|sharper)\\b)(?:(?!(?:this|that|these|those|my|your|his|her|its|our|their|better|best|good|great|nicer|sharper)\\b)[a-z0-9]+(?:-[a-z0-9]+)*\\s+){0,2}\\b(?:${ART_NOUN_SRC}|${AMBIG_ART_NOUN_SRC})\\b`,
  "i"
);
function isImagePrompt(text: string): boolean {
  const media = (IMAGE_RE.test(text) || IMAGE_VERB_RE.test(text)) && !VIDEO_RE.test(text);
  if (!media) return false;
  if (QUESTION_RE.test(text)) {
    return REQUEST_LEAD_RE.test(text) && IMAGE_TIGHT_VERB_RE.test(text);
  }
  return true;
}

// Verb closely governing the video noun ("make a video"), not advice about
// one ("make this movie scene look better"). Possessives/demonstratives are
// excluded across the whole bridge ("make sure my video works" stays text).
const VIDEO_VERB_RE = new RegExp(
  `\\b(?:record|film|shoot|make|create|generate|render|illustrate)(?:ing|ed|s)?\\s+(?:(?:a|an|the|some)\\s+)?(?!(?:this|that|these|those|my|your|his|her|its|our|their)\\b)(?:(?!(?:this|that|these|those|my|your|his|her|its|our|their)\\b)[a-z0-9]+(?:-[a-z0-9]+)*\\s+){0,2}\\b${VIDEO_NOUN_SRC}\\b`,
  "i"
);
function isVideoPrompt(text: string): boolean {
  if (!VIDEO_RE.test(text)) return false;
  if (QUESTION_RE.test(text)) {
    // Questions need an explicit generation request: "How do I make a video?"
    // yes, "How do I make this movie scene look better?" no.
    return REQUEST_LEAD_RE.test(text) && VIDEO_VERB_RE.test(text);
  }
  return true;
}

function App() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [selectedModel, setSelectedModel] = useState<AIModel>(defaultModel);
  const [sessions, setSessions] = useState(initialSessions);
  const [activeSessionId, setActiveSessionId] = useState(initialSessions[0]?.id || "1");
  const [messages, setMessages] = useState<Message[]>([createWelcomeMessage(defaultModel)]);
  const [isGenerating, setIsGenerating] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [voiceOpen, setVoiceOpen] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [imageModal, setImageModal] = useState<{ isOpen: boolean; url: string }>({ isOpen: false, url: "" });
  const [flowUser, setFlowUser] = useState<FlowMusicUser>(() => getFlowMusicSession());
  const [flowStatus, setFlowStatus] = useState<FlowMusicStatus>(() => getFlowMusicStatus());
  // Puter free stack (text + image + video) — one-tap connect, per-user quota.
  const [puterUser, setPuterUser] = useState<PuterUser | null>(() => getCachedPuterUser());
  const [puterAuthOpen, setPuterAuthOpen] = useState(false);
  // Transient "connect failed" notice — never overwrites flowStatus.
  const [connectError, setConnectError] = useState<string | null>(null);
  const [authUser, setAuthUser] = useState<{ name: string; email: string; picture: string } | null>(() => {
    try {
      const saved = localStorage.getItem("ultra_ai_user");
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  // Track the REAL Flow Music session status reported by the native WebView.
  useEffect(() => {
    const handleStatus = (e: any) => {
      const detail: FlowMusicStatus = e?.detail || {};
      setFlowStatus(detail);
      if (detail.signedIn && detail.email) {
        const updated = syncFlowMusicProfile({
          name: detail.name || detail.email,
          email: detail.email,
          picture: authUser?.picture || "",
        });
        setFlowUser(updated);
      }
    };
    window.addEventListener("pkai:flowmusic_status", handleStatus);
    // A failed connect attempt must not touch flowStatus (see connectFlowMusic):
    // show a retry affordance instead of flipping the UI to "not connected".
    const handleConnectFailed = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      setConnectError(
        detail && typeof detail.reason === "string" && detail.reason.length > 0
          ? detail.reason
          : "Connect nahi ho saka — bridge tayyar nahi hai."
      );
    };
    const clearConnectError = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      if (detail && detail.signedIn) setConnectError(null);
    };
    window.addEventListener("pkai:flowmusic_connect_failed", handleConnectFailed);
    window.addEventListener("pkai:flowmusic_status", clearConnectError);
    // Also poll once on mount (bridge may already have a cached value).
    setFlowStatus(getFlowMusicStatus());
    return () => {
      window.removeEventListener("pkai:flowmusic_status", handleStatus);
      window.removeEventListener("pkai:flowmusic_connect_failed", handleConnectFailed);
      window.removeEventListener("pkai:flowmusic_status", clearConnectError);
    };
  }, [authUser?.picture]);

  // Puter session: verify the cached user is still signed in (SDK session).
  useEffect(() => {
    let cancelled = false;
    isPuterSignedIn().then((signedIn) => {
      if (cancelled) return;
      if (!signedIn) {
        setPuterUser(null);
      } else if (!getCachedPuterUser()) {
        setPuterUser({ username: "Puter User", uuid: "" });
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSelectModel = (model: AIModel) => {
    setSelectedModel(model);
    setMessages([createWelcomeMessage(model)]);
  };

  const handleNewChat = () => {
    const newSession = {
      id: generateId(),
      title: "New Chat",
      date: "Just now",
      modelId: selectedModel.id,
    };
    setSessions([newSession, ...sessions]);
    setActiveSessionId(newSession.id);
    setMessages([createWelcomeMessage(selectedModel)]);
    setSidebarOpen(false);
  };

  const handleSelectSession = (id: string) => {
    setActiveSessionId(id);
    setSidebarOpen(false);
    setMessages([createWelcomeMessage(selectedModel)]);
  };

  /**
   * Puter free stack: text + image + video. One-tap connect, per-user quota,
   * no API keys. Replaces the old simulated replies — every answer here is
   * real, generated under the user's own Puter account.
   */
  const sendViaPuter = useCallback(
    async (text: string) => {
      const placeholderId = generateId();

      // ---- Image ----
      if (isImagePrompt(text)) {
        const placeholder: Message = {
          id: placeholderId,
          sender: "ai",
          text: "🖼️ Image ban rahi hai...",
          timestamp: getTimestamp(),
          type: "real_image",
          modelName: "Puter AI × FLUX",
          isGeneratingMedia: true,
          mediaCategory: "image",
        };
        setMessages((prev) => [...prev, placeholder]);
        setIsGenerating(true);
        try {
          const result = await puterGenerateImage(text, (p) => {
            setMessages((prev) =>
              prev.map((m) => (m.id === placeholderId ? { ...m, text: p.message } : m))
            );
          });
          setMessages((prev) =>
            prev.map((m) =>
              m.id === placeholderId
                ? result.ok && result.url
                  ? {
                      ...m,
                      text: `Aapke prompt ke mutabiq image tayyar hai:`,
                      imageUrl: result.url,
                      isGeneratingMedia: false,
                    }
                  : {
                      ...m,
                      type: "text",
                      text: `⚠️ ${result.error || "Image nahi ban saki."}${
                        result.needsAuth ? "\n\nHeader se \"Connect\" tap karein." : ""
                      }`,
                      isGeneratingMedia: false,
                    }
                : m
            )
          );
          if (result.needsAuth) setPuterUser(null);
        } finally {
          setIsGenerating(false);
        }
        return;
      }

      // ---- Video ----
      if (isVideoPrompt(text)) {
        const placeholder: Message = {
          id: placeholderId,
          sender: "ai",
          text: "🎬 Video ban raha hai... (thoda waqt lagega)",
          timestamp: getTimestamp(),
          type: "real_video",
          modelName: "Puter AI × Veo",
          isGeneratingMedia: true,
          mediaCategory: "video",
        };
        setMessages((prev) => [...prev, placeholder]);
        setIsGenerating(true);
        try {
          const result = await puterGenerateVideo(text, (p) => {
            setMessages((prev) =>
              prev.map((m) => (m.id === placeholderId ? { ...m, text: p.message } : m))
            );
          });
          setMessages((prev) =>
            prev.map((m) =>
              m.id === placeholderId
                ? result.ok && result.url
                  ? {
                      ...m,
                      text: `Aapka video tayyar hai:`,
                      videoUrl: result.url,
                      isGeneratingMedia: false,
                    }
                  : {
                      ...m,
                      type: "text",
                      text: `⚠️ ${result.error || "Video nahi ban saka."}${
                        result.needsAuth ? "\n\nHeader se \"Connect\" tap karein." : ""
                      }`,
                      isGeneratingMedia: false,
                    }
                : m
            )
          );
          if (result.needsAuth) setPuterUser(null);
        } finally {
          setIsGenerating(false);
        }
        return;
      }

      // ---- Text chat (streaming) ----
      const placeholder: Message = {
        id: placeholderId,
        sender: "ai",
        text: "Puter AI jawab tayyar kar raha hai...",
        timestamp: getTimestamp(),
        type: "text",
        modelName: "Puter AI",
        isGeneratingMedia: false,
      };
      setMessages((prev) => [...prev, placeholder]);
      setIsGenerating(true);
      try {
        const result = await puterChat(text, (p) => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === placeholderId ? { ...m, text: p.partialText || p.message } : m
            )
          );
        });
        setMessages((prev) =>
          prev.map((m) =>
            m.id === placeholderId
              ? result.ok && result.text
                ? { ...m, text: result.text, modelName: "Puter AI", isGeneratingMedia: false }
                : {
                    ...m,
                    type: "text",
                    text: `⚠️ ${result.error || "Jawab nahi mil saka."}${
                      result.needsAuth ? "\n\nHeader se \"Connect\" tap karein." : ""
                    }`,
                    isGeneratingMedia: false,
                  }
              : m
          )
        );
        if (result.needsAuth) setPuterUser(null);
      } finally {
        setIsGenerating(false);
      }
    },
    // [] is correct here: everything referenced is module-scope or stable
    // setState — no reactive values are closed over.
    []
  );

  /** Honest prompt when neither Puter nor FlowMusic is connected — no fake replies. */
  const connectPromptMessage = useCallback(
    (): Message => ({
      id: generateId(),
      sender: "ai",
      text: "👋 Free AI use karne ke liye pehle connect karein:\n\nHeader se \"Connect\" tap karein — ek tap par apna free Puter account jud jayega, phir text, image aur video sab free (aapke apne quota mein).",
      timestamp: getTimestamp(),
      type: "text",
      modelName: selectedModel.name,
    }),
    [selectedModel]
  );

  const updateSessionTitle = useCallback(
    (text: string) => {
      setSessions((prev) =>
        prev.map((s) =>
          s.id === activeSessionId && s.title === "New Chat"
            ? { ...s, title: text.slice(0, 40) + (text.length > 40 ? "..." : "") }
            : s
        )
      );
    },
    [activeSessionId]
  );

  const handleSend = useCallback(
    async (text: string) => {
      const userMessage: Message = {
        id: generateId(),
        sender: "user",
        text,
        timestamp: getTimestamp(),
      };
      setMessages((prev) => [...prev, userMessage]);
      updateSessionTitle(text);

      // ---- REAL Flow Music generation path -------------------------------
      if (isMusicPrompt(text)) {
        const connected = getFlowMusicStatus().signedIn;
        const placeholderId = generateId();
        const placeholder: Message = {
          id: placeholderId,
          sender: "ai",
          text: connected
            ? "🎵 Ultra AI 4 is creating your song..."
            : "🎵 Ultra Chat AI connect karein — header ke 'Connect' button se apne account se sign in karein, phir dobara try karein.",
          timestamp: getTimestamp(),
          type: "real_song",
          modelName: "🎵 Ultra AI 4",
          isGeneratingMedia: connected,
          mediaCategory: "song",
        };
        setMessages((prev) => [...prev, placeholder]);

        if (!connected) {
          // Do NOT auto-fire the sign-in popup here: popups only ever open
          // from an explicit user tap (banner / header "Connect" button).
          // The placeholder message above already tells the user what to do.
          return;
        }

        setIsGenerating(false);
        const result = await requestFlowMusicTrack(text, (progress) => {
          // Stream live Flow Music progress straight into the chat bubble.
          setMessages((prev) =>
            prev.map((m) =>
              m.id === placeholderId
                ? { ...m, text: "\ud83c\udfb5 " + (progress.message || "Ultra AI 4 kaam kar raha hai...") }
                : m
            )
          );
        });
        if (result.ok && result.audioUrl) {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === placeholderId
                ? {
                    ...m,
                    text: "🎵 Ultra AI 4 ne aapka track tayyar kar diya hai:",
                    audioUrl: result.audioUrl,
                    songTitle: result.title || text,
                    duration: null,
                    isGeneratingMedia: false,
                  }
                : m
            )
          );
        } else {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === placeholderId
                ? {
                    ...m,
                    type: "text",
                    text: `⚠️ Ultra AI 4 generation mukammal nahi ho saki.\n\n${result.error || "Unknown error."}\n\nTip: Header se "Connect" tap karke apne Ultra Chat AI account se sign in karein, Ultra Studio load hone dein, phir dobara try karein.`,
                    isGeneratingMedia: false,
                  }
                : m
            )
          );
        }
        return;
      }

      // ---- Puter free stack (default): text + image + video ---------------
      // One-tap connect, per-user quota, no API keys. This is the default
      // path for every prompt — FlowMusic stays as the Pro option for songs.
      if (puterUser) {
        await sendViaPuter(text);
        return;
      }

      // ---- FlowMusic chat (Pro fallback when Puter is not connected) -------
      const connected = getFlowMusicStatus().signedIn;
      if (connected) {
        const placeholderId = generateId();
        const placeholder: Message = {
          id: placeholderId,
          sender: "ai",
          text: "Ultra AI 4 jawab tayyar kar raha hai...",
          timestamp: getTimestamp(),
          type: "text",
          modelName: "Ultra AI 4",
          isGeneratingMedia: false,
        };
        setMessages((prev) => [...prev, placeholder]);

        // Lock the input for the whole round trip (up to 270s): without this
        // a second prompt could overlap the first and clobber its reply.
        setIsGenerating(true);
        try {
          const result = await requestFlowMusicChat(text, (progress) => {
            setMessages((prev) =>
              prev.map((m) =>
                m.id === placeholderId
                  ? { ...m, text: progress.message || "Ultra AI 4 jawab tayyar kar raha hai..." }
                  : m
              )
            );
          });
          if (result.ok && result.text) {
            setMessages((prev) =>
              prev.map((m) =>
                m.id === placeholderId
                  ? {
                      ...m,
                      text: result.text as string,
                      modelName: "Ultra AI 4",
                      isGeneratingMedia: false,
                    }
                  : m
              )
            );
          } else {
            setMessages((prev) =>
              prev.map((m) =>
                m.id === placeholderId
                  ? {
                      ...m,
                      type: "text",
                      text: `⚠️ Ultra AI 4 se jawab nahi mil saka.\n\n${result.error || "Unknown error."}\n\nDobara try karein — ya header se "Connect" tap karke Ultra Chat AI dobara connect karein.`,
                      isGeneratingMedia: false,
                    }
                  : m
              )
            );
          }
        } finally {
          setIsGenerating(false);
        }
        return;
      }

      // ---- Not connected: honest prompt, never a simulated reply --------
      setMessages((prev) => [...prev, connectPromptMessage()]);
    },
    [sendViaPuter, connectPromptMessage, updateSessionTitle, puterUser]
  );

  const handleRegenerate = useCallback(() => {
    if (messages.length < 2) return;
    const lastUserMessage = [...messages].reverse().find((m) => m.sender === "user");
    if (!lastUserMessage) return;

    setMessages((prev) => {
      const withoutLastAI = [...prev];
      const lastIndex = withoutLastAI.length - 1;
      if (lastIndex >= 0 && withoutLastAI[lastIndex].sender === "ai") {
        withoutLastAI.pop();
      }
      return withoutLastAI;
    });

    handleSend(lastUserMessage.text);
  }, [messages, handleSend]);

  const handleGenerateSongFromLyrics = useCallback(
    async (_soundPrompt: string, title: string) => {
      const placeholderId = generateId();
      const songMessage: Message = {
        id: placeholderId,
        sender: "ai",
        text: `🎵 Ultra AI 4 is creating "${title}"...`,
        timestamp: getTimestamp(),
        type: "real_song",
        songTitle: title,
        modelName: "🎵 Ultra AI 4",
        isGeneratingMedia: true,
        mediaCategory: "song",
      };
      setMessages((prev) => [...prev, songMessage]);

      const result = await requestFlowMusicTrack(title, (progress) => {
        setMessages((prev) =>
          prev.map((m) =>
            m.id === placeholderId
              ? { ...m, text: "\ud83c\udfb5 " + (progress.message || "Ultra AI 4 kaam kar raha hai...") }
              : m
          )
        );
      });
      setMessages((prev) =>
        prev.map((m) =>
          m.id === placeholderId
            ? result.ok && result.audioUrl
              ? { ...m, text: `Here is your song "${title}":`, audioUrl: result.audioUrl, isGeneratingMedia: false }
              : { ...m, type: "text", text: `⚠️ ${result.error || "Generation failed."}`, isGeneratingMedia: false }
            : m
        )
      );
    },
    []
  );

  const handleVoiceTranscript = useCallback(
    (text: string) => {
      handleSend(text);
    },
    [handleSend]
  );

  const handleImageUpload = useCallback(
    (file: File) => {
      const reader = new FileReader();
      reader.onload = () => {
        const imageMessage: Message = {
          id: generateId(),
          sender: "user",
          text: "",
          timestamp: getTimestamp(),
          type: "real_image",
          imageUrl: reader.result as string,
        };
        setMessages((prev) => [...prev, imageMessage]);

        setTimeout(() => {
          const aiMessage: Message = {
            id: generateId(),
            sender: "ai",
            text: "I can see the image you uploaded. Let me analyze it for you.",
            timestamp: getTimestamp(),
            modelName: selectedModel.name,
          };
          setMessages((prev) => [...prev, aiMessage]);
        }, 1000);
      };
      reader.readAsDataURL(file);
    },
    [selectedModel]
  );

  const handleAuthSuccess = useCallback(
    (_token: string, user: { name: string; email: string; picture: string }) => {
      setAuthUser(user);
      try {
        localStorage.setItem("ultra_ai_user", JSON.stringify(user));
      } catch {}
      setFlowUser(syncFlowMusicProfile(user));
      setAuthOpen(false);
    },
    []
  );

  const handleOpenFlowMusic = useCallback(() => {
    // Prefer the real Flow Music WebView session; fall back to the auth modal.
    const bridge = (window as any).AndroidOAuth;
    if (bridge && typeof bridge.connectFlowMusic === "function") {
      connectFlowMusic();
    } else {
      setAuthOpen(true);
    }
  }, []);

  const handleOpenPuterAuth = useCallback(() => {
    setPuterAuthOpen(true);
  }, []);

  const handlePuterAuthSuccess = useCallback((user: PuterUser) => {
    setPuterUser(user);
    setPuterAuthOpen(false);
  }, []);

  const handlePuterSignOut = useCallback(() => {
    signOutFromPuter().finally(() => setPuterUser(null));
  }, []);

  /** Explicit Puter disconnect (header "Puter AI ✓" tap) — confirm first. */
  const handlePuterDisconnectRequest = useCallback(() => {
    if (
      window.confirm(
        "Puter AI disconnect karna hai? Text, image aur video ke liye dobara Connect karna hoga."
      )
    ) {
      handlePuterSignOut();
    }
  }, [handlePuterSignOut]);

  const flowConnected = flowStatus.signedIn;

  return (
    <div className="flex h-screen overflow-hidden bg-slate-950">
      <Sidebar
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        models={models}
        selectedModel={selectedModel}
        onSelectModel={handleSelectModel}
        sessions={sessions}
        activeSessionId={activeSessionId}
        onSelectSession={handleSelectSession}
        onNewChat={handleNewChat}
        onOpenSettings={() => setSettingsOpen(true)}
        onOpenVoice={() => setVoiceOpen(true)}
        authUser={authUser}
        onOpenAuth={handleOpenFlowMusic}
        onSignOut={() => {
          setAuthUser(null);
          try {
            localStorage.removeItem("ultra_ai_user");
          } catch {}
          // Puter has its own disconnect affordance (header "Puter AI ✓") —
          // signing out of Ultra Chat AI must not kill the Puter session.
        }}
        flowCredits={flowUser.dailyCreditsRemaining}
      />

      <div className="flex-1 flex flex-col min-w-0">
        <Header
          onToggleSidebar={() => setSidebarOpen(!sidebarOpen)}
          selectedModel={selectedModel}
          onOpenVoice={() => setVoiceOpen(true)}
          onOpenSettings={() => setSettingsOpen(true)}
          onOpenFlowStudio={handleOpenFlowMusic}
          onOpenAuth={handleOpenFlowMusic}
          onOpenPuterAuth={handleOpenPuterAuth}
          onPuterDisconnect={handlePuterDisconnectRequest}
          authUser={authUser}
          flowCredits={flowUser.dailyCreditsRemaining}
          puterConnected={!!puterUser}
        />

        {(!puterUser || !flowConnected || connectError) && (
          <div className={`shrink-0 px-4 py-2 border-b flex items-center justify-center gap-2 text-[12px] ${
            !puterUser && !connectError
              ? "bg-gradient-to-r from-cyan-950/60 via-indigo-950/40 to-slate-950 border-cyan-900/40 text-cyan-200"
              : "bg-gradient-to-r from-pink-950/60 via-purple-950/40 to-slate-950 border-pink-900/40 text-pink-200"
          }`}>
            <Plug className={`w-3.5 h-3.5 ${!puterUser && !connectError ? "text-cyan-400" : "text-pink-400"}`} />
            {connectError ? (
              <span>{connectError}</span>
            ) : !puterUser ? (
              <span>Free AI connect nahi hai — text, image aur video ke liye</span>
            ) : flowStatus.accountMismatch ? (
              <span>Device ka Google account PK-AI wale account se mukhtalif hai — real songs ke liye</span>
            ) : (
              <span>Ultra Chat AI connect nahi hai — real songs generate karne ke liye</span>
            )}
            <button
              onClick={() => {
                setConnectError(null);
                if (!puterUser) handleOpenPuterAuth();
                else handleOpenFlowMusic();
              }}
              className={`font-semibold underline underline-offset-2 hover:text-white ${
                !puterUser && !connectError ? "text-cyan-300" : "text-pink-300"
              }`}
            >
              {connectError ? "Dobara try karein" : "Connect karein"}
            </button>
          </div>
        )}

        <main className="flex-1 overflow-y-auto custom-scrollbar">
          <div className="py-6 space-y-6">
            {messages.map((message) => (
              <ChatMessage
                key={message.id}
                message={message}
                userName={authUser?.name}
                onRegenerate={
                  message.sender === "ai" && messages.filter((m) => m.sender === "ai").length > 1
                    ? handleRegenerate
                    : undefined
                }
                onGenerateSongFromLyrics={handleGenerateSongFromLyrics}
              />
            ))}
            {isGenerating && (
              <div className="flex gap-3 max-w-4xl mx-auto w-full justify-start">
                <div className="shrink-0 w-9 h-9 rounded-xl bg-gradient-to-br from-cyan-500 via-indigo-600 to-purple-600 p-0.5 shadow-lg shadow-indigo-500/20">
                  <div className="w-full h-full bg-slate-950 rounded-[10px] flex items-center justify-center text-cyan-400">
                    <Bot className="w-5 h-5" />
                  </div>
                </div>
                <div className="flex flex-col space-y-2 max-w-[85%] items-start">
                  <div className="flex items-center gap-2 text-[11px] text-slate-400 px-1">
                    <span className="font-semibold text-slate-300">{selectedModel.name}</span>
                    <span>•</span>
                    <span>{getTimestamp()}</span>
                  </div>
                  <div className="p-4 rounded-2xl bg-slate-900/90 border border-slate-800 text-slate-100 rounded-tl-xs">
                    <div className="flex items-center gap-2 text-cyan-400">
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span className="text-sm">Thinking...</span>
                    </div>
                  </div>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
        </main>

        <ChatInput
          onSend={handleSend}
          onImageUpload={handleImageUpload}
          onVoiceRecord={() => setVoiceOpen(true)}
          onStopGenerating={() => setIsGenerating(false)}
          isGenerating={isGenerating}
        />
      </div>

      <SettingsModal isOpen={settingsOpen} onClose={() => setSettingsOpen(false)} />
      <VoiceModal isOpen={voiceOpen} onClose={() => setVoiceOpen(false)} onSendTranscript={handleVoiceTranscript} />
      <ImageModal
        isOpen={imageModal.isOpen}
        onClose={() => setImageModal({ isOpen: false, url: "" })}
        imageUrl={imageModal.url}
      />
      <FlowAuthModal
        isOpen={authOpen}
        onClose={() => setAuthOpen(false)}
        onAuthSuccess={handleAuthSuccess}
      />
      <PuterAuthModal
        isOpen={puterAuthOpen}
        onClose={() => setPuterAuthOpen(false)}
        onAuthSuccess={handlePuterAuthSuccess}
      />
    </div>
  );
}

export default App;
