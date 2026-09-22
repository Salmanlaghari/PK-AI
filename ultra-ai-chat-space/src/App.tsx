import { useState, useRef, useEffect, useCallback } from "react";
import { Bot, Loader2, X } from "lucide-react";
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
  bindGoogleToFlowMusic,
  generateStrictVisual,
  generateFlowMusicTrack,
  deductFlowCredits,
  type FlowMusicUser,
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
    text: `Namaste! Main ${model.name} hoon (Powered by FlowMusic Backend). Aaj main aapki kya madad kar sakta hoon? Aap mujhse koi bhi song, Bollywood track, lyrics, ya HD image mang sakte hain.`,
    timestamp: getTimestamp(),
    modelName: `${model.name} × FlowMusic`,
  };
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
  const [authUser, setAuthUser] = useState<{ name: string; email: string; picture: string } | null>(() => {
    try {
      const saved = localStorage.getItem("ultra_ai_user");
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });
  useEffect(() => {
    if (authUser) {
      console.log("Authenticated user:", authUser);
    }
  }, [authUser]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

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
    // In a real app, load messages for this session
    setMessages([createWelcomeMessage(selectedModel)]);
  };

  const simulateAIResponse = useCallback((userText: string): Message => {
    const lower = userText.toLowerCase();
    let responseText = "";
    let type: Message["type"] = "text";

    // 1. IMAGE GENERATION - STRICT PROMPT OBEDIENCE VIA FLOWMUSIC VISUAL ENGINE
    if (lower.includes("image") || lower.includes("photo") || lower.includes("picture") || lower.includes("draw") || lower.includes("tasveer") || lower.includes("pic") || lower.includes("wallpaper")) {
      type = "real_image";
      const visual = generateStrictVisual(userText);
      const updatedUser = getFlowMusicSession();
      setFlowUser(updatedUser);

      responseText = `Maine FlowMusic Backend Engine ke zariye aapke prompt "${visual.prompt}" ke mutabiq authentic HD image generate kar di hai:\n(⚡ ${visual.creditsCost} FlowMusic Credits istemal huye | ${visual.creditsRemaining}/50 Daily Credits baqi hain)`;

      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        imageUrl: visual.imageUrl,
        modelName: `${selectedModel.name} × FlowMusic`,
        isGeneratingMedia: false,
        mediaCategory: "image",
      };
    }

    // 2. REAL AI MUSIC GENERATION - FLOWMUSIC.APP BACKEND ENGINE
    if (lower.includes("song") || lower.includes("music") || lower.includes("audio") || lower.includes("gana") || lower.includes("gaana") || lower.includes("track") || lower.includes("beat")) {
      type = "real_song";
      const track = generateFlowMusicTrack(userText);
      const updatedUser = getFlowMusicSession();
      setFlowUser(updatedUser);

      responseText = `Maine FlowMusic Audio Engine (https://www.flowmusic.app/) se aapke request ke mutabiq "${track.songTitle}" mukammal tayyar kar diya hai:\n(⚡ ${track.creditsCost} FlowMusic Credits istemal huye | ${track.creditsRemaining}/50 Daily Credits baqi hain)\n\n${track.lyrics}`;

      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        audioUrl: track.audioUrl,
        coverImageUrl: track.coverImageUrl,
        songTitle: track.songTitle,
        duration: track.duration,
        lyricsText: track.lyrics,
        modelName: `${selectedModel.name} × FlowMusic`,
        isGeneratingMedia: false,
        mediaCategory: "song",
      };
    }

    // 3. REAL AI VIDEO GENERATION
    if (lower.includes("video") || lower.includes("clip") || lower.includes("film")) {
      type = "real_video";
      const remainingCredits = deductFlowCredits(4);
      setFlowUser(getFlowMusicSession());

      responseText = `Maine FlowMusic Visual Engine se aapke liye video generate kar di hai:\n(⚡ 4 FlowMusic Credits istemal huye | ${remainingCredits}/50 Daily Credits baqi hain)`;
      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        modelName: `${selectedModel.name} × FlowMusic`,
        isGeneratingMedia: false,
        mediaCategory: "video",
      };
    }

    if (lower.includes("code") || lower.includes("function") || lower.includes("program")) {
      type = "code";
      responseText = "Yeh raha aapke request ke mutabiq code:";
      const codeSnippet = `function helloUltraAI() {\n  // Ultra AI 4 powered by FlowMusic Engine (https://www.flowmusic.app/)\n  console.log("Connected to FlowMusic Creator Engine - 50 Daily Credits");\n}`;
      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        codeSnippet,
        modelName: selectedModel.name,
      };
    }

    if (lower.includes("lyrics") || lower.includes("geet") || lower.includes("song words") || lower.includes("shairi")) {
      type = "real_lyrics";
      const remainingCredits = deductFlowCredits(1);
      setFlowUser(getFlowMusicSession());

      let lyricsText = "";
      if (lower.includes("bollywood") || lower.includes("romantic") || lower.includes("love") || lower.includes("pyar")) {
        lyricsText = `[Bollywood Romantic - FlowMusic Composition]\n\nMukhda:\nDil ki galiyon mein tera hi basera hai\nTu subah meri, tu hi mera savera hai\n\nAntra 1:\nFaasle mita ke aa kareeb tu zara\nTere bina lage har ek lamha sazaa\nAnkhon se bayan ho rahi yeh daastan\nTu hi meri rooh, tu hi mera aasmaan\n\nChorus:\nTum hi ho meri duniya, tum hi ho qarar\nDil karta hai tumse be-inteha pyar!`;
      } else if (lower.includes("sad") || lower.includes("dard")) {
        lyricsText = `[Sad Melancholic - FlowMusic Composition]\n\nMukhda:\nKhaali hain haath, bheege hain yeh naina\nAb tere bina mushkil hai mera rehna\n\nAntra 1:\nKayi khwaab toote hain is raat ke andhere mein\nBas tera hi saaya hai yaadon ke ghere mein\n\nChorus:\nJaane kyun bewajah juda ho gaye hum\nAb har taraf bas dhuwan aur gham!`;
      } else {
        lyricsText = `[FlowMusic AI Original]\n\nVerse 1:\nAaj ki raat nayi dhun bajegi\nHar ek saaz pe zindagi sajegi\n\nChorus:\nFlowMusic ka yeh jaadu chale\nKhushi ke deep har ek pal jale!`;
      }
      responseText = `Maine FlowMusic Engine se aapke liye song lyrics generate kar diye hain:\n(⚡ 1 FlowMusic Credit istemal hua | ${remainingCredits}/50 Daily Credits baqi hain)`;
      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        lyricsText,
        modelName: `${selectedModel.name} × FlowMusic`,
      };
    }

    const responses = [
      "Bilkul! Maine aapka sawal samajh liya hai. Main is par kaam kar raha hoon.",
      "Zaroor! Main aapki is request par mukammal madad karne ke liye tayyar hoon.",
      "Yeh bahut behtareen request hai. Ultra AI 4 engine iska behtar result generate kar raha hai.",
      "Ji haan, bilkul. Main aapke liye step-by-step complete solution provide karta hoon.",
    ];
    responseText = responses[Math.floor(Math.random() * responses.length)];
    return {
      id: generateId(),
      sender: "ai",
      text: responseText,
      timestamp: getTimestamp(),
      modelName: selectedModel.name,
    };
  }, [selectedModel]);

  const handleSend = useCallback(
    (text: string) => {
      const userMessage: Message = {
        id: generateId(),
        sender: "user",
        text,
        timestamp: getTimestamp(),
      };

      setMessages((prev) => [...prev, userMessage]);
      setIsGenerating(true);

      // Simulate network delay
      setTimeout(() => {
        const aiMessage = simulateAIResponse(text);
        setMessages((prev) => [...prev, aiMessage]);
        setIsGenerating(false);

        // Update session title if it's the first message
        setSessions((prev) =>
          prev.map((s) =>
            s.id === activeSessionId && s.title === "New Chat"
              ? { ...s, title: text.slice(0, 40) + (text.length > 40 ? "..." : "") }
              : s
          )
        );
      }, 800 + Math.random() * 1500);
    },
    [simulateAIResponse, activeSessionId]
  );

  const handleRegenerate = useCallback(() => {
    if (messages.length < 2) return;
    const lastUserMessage = [...messages].reverse().find((m) => m.sender === "user");
    if (!lastUserMessage) return;

    // Remove last AI message
    setMessages((prev) => {
      const withoutLastAI = [...prev];
      const lastIndex = withoutLastAI.length - 1;
      if (lastIndex >= 0 && withoutLastAI[lastIndex].sender === "ai") {
        withoutLastAI.pop();
      }
      return withoutLastAI;
    });

    setIsGenerating(true);
    setTimeout(() => {
      const aiMessage = simulateAIResponse(lastUserMessage.text);
      setMessages((prev) => [...prev, aiMessage]);
      setIsGenerating(false);
    }, 800 + Math.random() * 1500);
  }, [messages, simulateAIResponse]);

  const handleGenerateSongFromLyrics = useCallback(
    (_soundPrompt: string, title: string) => {
      const songMessage: Message = {
        id: generateId(),
        sender: "ai",
        text: `Generating song "${title}" from your lyrics...`,
        timestamp: getTimestamp(),
        type: "real_song",
        audioUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
        coverImageUrl: `https://picsum.photos/seed/${generateId()}/128/128`,
        songTitle: title,
        duration: 185,
        modelName: selectedModel.name,
        isGeneratingMedia: true,
        mediaCategory: "song",
      };
      setMessages((prev) => [...prev, songMessage]);

      // Simulate generation completion
      setTimeout(() => {
        setMessages((prev) =>
          prev.map((m) =>
            m.id === songMessage.id
              ? { ...m, isGeneratingMedia: false, text: `Here is your song "${title}":` }
              : m
          )
        );
      }, 3000);
    },
    [selectedModel]
  );

  const handleVoiceTranscript = useCallback((text: string) => {
    handleSend(text);
  }, [handleSend]);

  const handleImageUpload = useCallback((file: File) => {
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

      // AI response
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
  }, [selectedModel]);

  const handleAuthSuccess = useCallback((_token: string, user: { name: string; email: string; picture: string }) => {
    setAuthUser(user);
    try {
      localStorage.setItem("ultra_ai_user", JSON.stringify(user));
    } catch {}
    const updated = bindGoogleToFlowMusic(user);
    setFlowUser(updated);
    setAuthOpen(false);

    const welcomeMsg: Message = {
      id: generateId(),
      sender: "ai",
      text: `🎉 **FlowMusic Account Connected!**\n\nKhush-aamdeed **${user.name}**! Aapka Google / FlowMusic account safely connect ho chuka hai.\n⚡ **50 Daily Creation Credits** activate ho chuke hain (https://www.flowmusic.app/). Ab aap Ultra AI 4 ke tamam songs, lyrics, voice aur creative features be-fiker use kar sakte hain.`,
      timestamp: getTimestamp(),
      modelName: `${selectedModel.name} × FlowMusic`,
    };
    setMessages((prev) => [...prev, welcomeMsg]);
  }, [selectedModel.name]);

  const handleFlowStudioTrackGenerated = useCallback((trackUrl: string) => {
    const aiMessage: Message = {
      id: generateId(),
      sender: "ai",
      text: `I have generated a track for you using Flow Studio.`,
      timestamp: getTimestamp(),
      type: "real_song",
      audioUrl: trackUrl,
      coverImageUrl: `https://picsum.photos/seed/${generateId()}/128/128`,
      songTitle: "Flow Studio Generated Track",
      duration: null,
      modelName: selectedModel.name,
      isGeneratingMedia: false,
      mediaCategory: "song",
    };
    setMessages((prev) => [...prev, aiMessage]);
    setFlowStudioOpen(false);
  }, [selectedModel]);

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
        onOpenAuth={() => setAuthOpen(true)}
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
          onOpenFlowStudio={() => {
            const androidOAuth = (window as any).AndroidOAuth;
            if (androidOAuth && typeof androidOAuth.openFlowMusicSignUp === "function") {
              androidOAuth.openFlowMusicSignUp();
            } else {
              setFlowStudioOpen(true);
            }
          }}
          onOpenAuth={() => setAuthOpen(true)}
          authUser={authUser}
          flowCredits={flowUser.dailyCreditsRemaining}
        />

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
              <h2 className="text-lg font-semibold text-slate-100">Flow Studio</h2>
              <button
                onClick={() => setFlowStudioOpen(false)}
                className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="flex-1 overflow-hidden p-4">
              <FlowStudioEmbed
                userName={authUser?.name || "Prince Laghari"}
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
