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
    text: `Namaste! Main ${model.name} hoon. Aaj main aapki kya madad kar sakta hoon? Main creative tasks, coding, image generation, aur voice synthesis sab mein aapki madad kar sakta hoon.`,
    timestamp: getTimestamp(),
    modelName: model.name,
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

function cleanImagePrompt(input: string): string {
  let p = input
    .replace(/^(please\s+)?(can\s+you\s+)?(make|generate|create|draw|paint|show|give|banao|dikhao|render)\s+(me\s+)?(an?\s+)?(image|photo|picture|pic|tasveer|wallpaper)\s+(of\s+|about\s+|ki\s+|ka\s+)?/i, "")
    .replace(/\s+(image|photo|picture|pic|draw|tasveer|banao)\s*$/i, "")
    .trim();
  return p.length >= 2 ? p : input.trim();
}

function parseMusicPrompt(input: string) {
  const lower = input.toLowerCase();
  let songTitle = "Ultra Flow Track";
  let audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3";
  let coverPrompt = "music album cover futuristic";

  if (lower.includes("bollywood") || lower.includes("hindi") || lower.includes("filmi")) {
    songTitle = "Bollywood Filmi Romance - Tum Hi Ho Meri Duniya";
    coverPrompt = "bollywood movie album cover couple romantic dramatic cinematic lighting";
    audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3";
  } else if (lower.includes("sufi") || lower.includes("qawwali")) {
    songTitle = "Roohani Ishq - Sufi Fusion";
    coverPrompt = "sufi mystical album cover spiritual glowing light harmony";
    audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3";
  } else if (lower.includes("sad") || lower.includes("dard") || lower.includes("gham")) {
    songTitle = "Khaali Raaste - Melancholic Melody";
    coverPrompt = "sad melancholic rainy window album cover night lo-fi aesthetic";
    audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3";
  } else if (lower.includes("rap") || lower.includes("drill") || lower.includes("hip hop")) {
    songTitle = "Desi Drill 808 - Raaston Ka Shor";
    coverPrompt = "dark urban drill street album cover neon night underground";
    audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3";
  } else {
    const cleaned = input
      .replace(/^(make|generate|play|create|gaana|gana|song|music|sunao)\s+(me\s+)?(a\s+)?/i, "")
      .replace(/\s+(song|music|audio|track|gaana)\s*$/i, "")
      .trim();
    const tag = cleaned ? cleaned.charAt(0).toUpperCase() + cleaned.slice(1) : "Flow Track";
    songTitle = `${tag} - Ultra AI Original`;
    coverPrompt = `music album cover ${tag} vibrant studio high quality`;
    audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3";
  }

  const coverImageUrl = `https://image.pollinations.ai/prompt/${encodeURIComponent(coverPrompt)}?width=400&height=400&nologo=true`;

  return { songTitle, audioUrl, coverImageUrl };
}

  const simulateAIResponse = useCallback((userText: string): Message => {
    const lower = userText.toLowerCase();
    let responseText = "";
    let type: Message["type"] = "text";

    if (lower.includes("image") || lower.includes("photo") || lower.includes("picture") || lower.includes("draw") || lower.includes("tasveer") || lower.includes("pic")) {
      type = "real_image";
      const prompt = cleanImagePrompt(userText);
      const imageUrl = `https://image.pollinations.ai/prompt/${encodeURIComponent(prompt)}?width=1024&height=1024&nologo=true&seed=${Date.now()}`;
      responseText = `Maine aapke prompt "${prompt}" ke mutabiq yeh high-quality AI image generate kar di hai:`;
      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        imageUrl,
        modelName: selectedModel.name,
        isGeneratingMedia: false,
        mediaCategory: "image",
      };
    }

    if (lower.includes("song") || lower.includes("music") || lower.includes("audio") || lower.includes("gana") || lower.includes("gaana") || lower.includes("track")) {
      type = "real_song";
      const musicData = parseMusicPrompt(userText);
      responseText = `Maine aapke request ke mutabiq "${musicData.songTitle}" generate kar diya hai:`;
      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        audioUrl: musicData.audioUrl,
        coverImageUrl: musicData.coverImageUrl,
        songTitle: musicData.songTitle,
        duration: 185,
        modelName: selectedModel.name,
        isGeneratingMedia: false,
        mediaCategory: "song",
      };
    }

    if (lower.includes("video") || lower.includes("clip")) {
      type = "real_video";
      responseText = "Maine aapke liye yeh video generate kar di hai:";
      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        modelName: selectedModel.name,
        isGeneratingMedia: false,
        mediaCategory: "video",
      };
    }

    if (lower.includes("code") || lower.includes("function") || lower.includes("program")) {
      type = "code";
      responseText = "Yeh raha aapke request ke mutabiq code:";
      const codeSnippet = `function helloUltraAI() {\n  console.log("Ultra AI 4 - Powered by Gemini & FlowMusic");\n}`;
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
      let lyricsText = "";
      if (lower.includes("bollywood") || lower.includes("romantic") || lower.includes("love") || lower.includes("pyar")) {
        lyricsText = `[Bollywood Romantic Style]\n\nMukhda:\nDil ki galiyon mein tera hi basera hai\nTu subah meri, tu hi mera savera hai\n\nAntra 1:\nFaasle mita ke aa kareeb tu zara\nTere bina lage har ek lamha sazaa\nAnkhon se bayan ho rahi yeh daastan\nTu hi meri rooh, tu hi mera aasmaan\n\nChorus:\nTum hi ho meri duniya, tum hi ho qarar\nDil karta hai tumse be-inteha pyar!`;
      } else if (lower.includes("sad") || lower.includes("dard")) {
        lyricsText = `[Sad Melancholic Style]\n\nMukhda:\nKhaali hain haath, bheege hain yeh naina\nAb tere bina mushkil hai mera rehna\n\nAntra 1:\nKayi khwaab toote hain is raat ke andhere mein\nBas tera hi saaya hai yaadon ke ghere mein\n\nChorus:\nJaane kyun bewajah juda ho gaye hum\nAb har taraf bas dhuwan aur gham!`;
      } else {
        lyricsText = `[Ultra AI Lyrics]\n\nVerse 1:\nAaj ki raat nayi dhun bajegi\nHar ek saaz pe zindagi sajegi\n\nChorus:\nUltra AI ka yeh jaadu chale\nKhushi ke deep har ek pal jale!`;
      }
      responseText = `Maine aapke request ke mutabiq yeh song lyrics generate kar diye hain:`;
      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        lyricsText,
        modelName: selectedModel.name,
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
    setAuthOpen(false);

    const welcomeMsg: Message = {
      id: generateId(),
      sender: "ai",
      text: `🎉 **FlowMusic Account Connected!**\n\nKhush-aamdeed **${user.name}**! Aapka Google / FlowMusic account safely connect ho chuka hai. Ab aap Ultra AI 4 ke tamam songs, lyrics, voice aur creative features be-fiker use kar sakte hain.`,
      timestamp: getTimestamp(),
      modelName: selectedModel.name,
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
      />

      <div className="flex-1 flex flex-col min-w-0">
        <Header
          onToggleSidebar={() => setSidebarOpen(!sidebarOpen)}
          selectedModel={selectedModel}
          onOpenVoice={() => setVoiceOpen(true)}
          onOpenSettings={() => setSettingsOpen(true)}
          onOpenFlowStudio={() => setAuthOpen(true)}
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
