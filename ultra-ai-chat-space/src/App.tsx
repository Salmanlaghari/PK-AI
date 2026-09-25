import { useState, useRef, useEffect, useCallback } from "react";
import { Bot, Loader2, X, Music2, Plug } from "lucide-react";
import Sidebar from "./components/Sidebar";
import Header from "./components/Header";
import ChatMessage from "./components/ChatMessage";
import ChatInput from "./components/ChatInput";
import SettingsModal from "./components/SettingsModal";
import VoiceModal from "./components/VoiceModal";
import ImageModal from "./components/ImageModal";
import FlowAuthModal from "./components/FlowAuthModal";
import FlowStudioEmbed from "./components/FlowStudioEmbed";
import { models, defaultModel } from "./data/models";
import { sessions as initialSessions } from "./data/sessions";
import type { Message, AIModel } from "./types";
import {
  getFlowMusicSession,
  getFlowMusicStatus,
  connectFlowMusic,
  requestFlowMusicTrack,
  syncFlowMusicProfile,
  generateStrictVisual,
  deductFlowCredits,
  type FlowMusicUser,
  type FlowMusicStatus,
} from "./services/flowMusicService";

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
    text: `Namaste! Main ${model.name} hoon — Ultra AI 4, ab real Flow Music engine ke saath. Aap mujhse seedha gaana banao, lyrics likhwao, ya HD image banwao. Music ke liye pehle header se apne Flow Music account ko connect karein.`,
    timestamp: getTimestamp(),
    modelName: `${model.name} × FlowMusic`,
  };
}

const MUSIC_RE = /(song|music|audio|gana|gaana|track|beat|melody|tune|dhun|compose|instrumental|remix|vocal)/i;
const NON_MUSIC_RE = /(image|photo|picture|pic|tasveer|wallpaper|video|clip|code|function|program)/i;

function isMusicPrompt(text: string): boolean {
  return MUSIC_RE.test(text) && !NON_MUSIC_RE.test(text);
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
  const [flowStudioOpen, setFlowStudioOpen] = useState(false);
  const [imageModal, setImageModal] = useState<{ isOpen: boolean; url: string }>({ isOpen: false, url: "" });
  const [flowUser, setFlowUser] = useState<FlowMusicUser>(() => getFlowMusicSession());
  const [flowStatus, setFlowStatus] = useState<FlowMusicStatus>(() => getFlowMusicStatus());
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
    // Also poll once on mount (bridge may already have a cached value).
    setFlowStatus(getFlowMusicStatus());
    return () => window.removeEventListener("pkai:flowmusic_status", handleStatus);
  }, [authUser?.picture]);

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

  const simulateAIResponse = useCallback((userText: string): Message => {
    const lower = userText.toLowerCase();

    // IMAGE GENERATION (real, keyless Flux)
    if (/(image|photo|picture|draw|tasveer|pic|wallpaper)/.test(lower)) {
      const visual = generateStrictVisual(userText);
      setFlowUser(getFlowMusicSession());
      return {
        id: generateId(),
        sender: "ai",
        text: `Aapke prompt "${visual.prompt}" ke mutabiq HD image generate kar di gayi hai:`,
        timestamp: getTimestamp(),
        type: "real_image",
        imageUrl: visual.imageUrl,
        modelName: `${selectedModel.name} × Ultra AI`,
        isGeneratingMedia: false,
        mediaCategory: "image",
      };
    }

    // LYRICS (text)
    if (/(lyrics|geet|song words|shairi)/.test(lower)) {
      const remainingCredits = deductFlowCredits(1);
      setFlowUser(getFlowMusicSession());
      const lyricsText = `[FlowMusic AI Original]\n\nVerse 1:\nAaj ki raat nayi dhun bajegi\nHar ek saaz pe zindagi sajegi\n\nChorus:\nFlowMusic ka yeh jaadu chale\nKhushi ke deep har ek pal jale!`;
      return {
        id: generateId(),
        sender: "ai",
        text: `Yeh raha aapke liye likha gaya lyrics (${remainingCredits}/50 daily credits baqi):`,
        timestamp: getTimestamp(),
        type: "real_lyrics",
        lyricsText,
        modelName: `${selectedModel.name} × Ultra AI`,
      };
    }

    if (/(code|function|program)/.test(lower)) {
      const codeSnippet = `function helloUltraAI() {\n  // Ultra AI 4 + real Flow Music session\n  console.log("Connected to Flow Music Engine");\n}`;
      return {
        id: generateId(),
        sender: "ai",
        text: "Yeh raha aapka code:",
        timestamp: getTimestamp(),
        type: "code",
        codeSnippet,
        modelName: selectedModel.name,
      };
    }

    const responses = [
      "Bilkul! Maine aapka sawal samajh liya hai. Main is par kaam kar raha hoon.",
      "Zaroor! Main aapki is request par mukammal madad karne ke liye tayyar hoon.",
      "Yeh bahut behtareen request hai. Ultra AI 4 engine iska behtar result generate kar raha hai.",
      "Ji haan, bilkul. Main aapke liye step-by-step complete solution provide karta hoon.",
    ];
    return {
      id: generateId(),
      sender: "ai",
      text: responses[Math.floor(Math.random() * responses.length)],
      timestamp: getTimestamp(),
      modelName: selectedModel.name,
    };
  }, [selectedModel]);

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
            ? "🎵 Flow Music is creating your song..."
            : "🎵 Flow Music connect karein — header ke 'Connect' button se apne account se sign in karein, phir dobara try karein.",
          timestamp: getTimestamp(),
          type: "real_song",
          modelName: "🎵 Flow Music AI",
          isGeneratingMedia: connected,
          mediaCategory: "song",
        };
        setMessages((prev) => [...prev, placeholder]);

        if (!connected) {
          // Open the real Flow Music sign-in WebView so the user can connect.
          connectFlowMusic();
          return;
        }

        setIsGenerating(false);
        const result = await requestFlowMusicTrack(text);
        if (result.ok && result.audioUrl) {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === placeholderId
                ? {
                    ...m,
                    text: "🎵 Flow Music AI ne aapka track tayyar kar diya hai:",
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
                    text: `⚠️ Flow Music generation mukammal nahi ho saki.\n\n${result.error || "Unknown error."}\n\nTip: Header se "Connect" tap karke apne Flow Music account se sign in karein, Flow Music studio load hone dein, phir dobara try karein.`,
                    isGeneratingMedia: false,
                  }
                : m
            )
          );
        }
        return;
      }

      // ---- Standard assistant path ---------------------------------------
      setIsGenerating(true);
      setTimeout(() => {
        const aiMessage = simulateAIResponse(text);
        setMessages((prev) => [...prev, aiMessage]);
        setIsGenerating(false);
      }, 700 + Math.random() * 900);
    },
    [simulateAIResponse, updateSessionTitle]
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
        text: `🎵 Flow Music is creating "${title}"...`,
        timestamp: getTimestamp(),
        type: "real_song",
        songTitle: title,
        modelName: "🎵 Flow Music AI",
        isGeneratingMedia: true,
        mediaCategory: "song",
      };
      setMessages((prev) => [...prev, songMessage]);

      const result = await requestFlowMusicTrack(title);
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

  const handleFlowStudioTrackGenerated = useCallback(
    (trackUrl: string) => {
      const aiMessage: Message = {
        id: generateId(),
        sender: "ai",
        text: "I have generated a track for you using Flow Studio.",
        timestamp: getTimestamp(),
        type: "real_song",
        audioUrl: trackUrl,
        songTitle: "Flow Studio Generated Track",
        duration: null,
        modelName: "🎵 Flow Music AI",
        isGeneratingMedia: false,
        mediaCategory: "song",
      };
      setMessages((prev) => [...prev, aiMessage]);
      setFlowStudioOpen(false);
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
          authUser={authUser}
          flowCredits={flowUser.dailyCreditsRemaining}
          flowConnected={flowConnected}
        />

        {!flowConnected && (
          <div className="shrink-0 px-4 py-2 bg-gradient-to-r from-pink-950/60 via-purple-950/40 to-slate-950 border-b border-pink-900/40 flex items-center justify-center gap-2 text-[12px] text-pink-200">
            <Plug className="w-3.5 h-3.5 text-pink-400" />
            <span>Flow Music connect nahi hai — real songs generate karne ke liye</span>
            <button
              onClick={handleOpenFlowMusic}
              className="font-semibold text-pink-300 underline underline-offset-2 hover:text-white"
            >
              Connect karein
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
      {flowStudioOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl w-full max-w-5xl max-h-[90vh] overflow-hidden flex flex-col">
            <div className="flex items-center justify-between p-4 border-b border-slate-800">
              <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
                <Music2 className="w-5 h-5 text-pink-400" />
                Flow Studio
              </h2>
              <button
                onClick={() => setFlowStudioOpen(false)}
                className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="flex-1 overflow-hidden p-4">
              <FlowStudioEmbed
                userName={authUser?.name || "Ultra AI User"}
                onTrackGenerated={handleFlowStudioTrackGenerated}
                onClose={() => setFlowStudioOpen(false)}
                onError={(err) => console.error("Flow Studio error:", err)}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
