import { useState, useRef, useEffect, useCallback } from "react";
import { Bot, Loader2 } from "lucide-react";
import Sidebar from "./components/Sidebar";
import Header from "./components/Header";
import ChatMessage from "./components/ChatMessage";
import ChatInput from "./components/ChatInput";
import SettingsModal from "./components/SettingsModal";
import VoiceModal from "./components/VoiceModal";
import ImageModal from "./components/ImageModal";
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
  const [imageModal, setImageModal] = useState<{ isOpen: boolean; url: string }>({ isOpen: false, url: "" });
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

    if (lower.includes("image") || lower.includes("photo") || lower.includes("picture") || lower.includes("draw")) {
      type = "real_image";
      responseText = "I have generated an image based on your request. Here it is:";
      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        imageUrl: `https://picsum.photos/seed/${generateId()}/512/512`,
        modelName: selectedModel.name,
        isGeneratingMedia: false,
        mediaCategory: "image",
      };
    }

    if (lower.includes("song") || lower.includes("music") || lower.includes("audio") || lower.includes("gana")) {
      type = "real_song";
      responseText = "Here is an AI-generated track based on your request:";
      return {
        id: generateId(),
        sender: "ai",
        text: responseText,
        timestamp: getTimestamp(),
        type,
        audioUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
        coverImageUrl: `https://picsum.photos/seed/${generateId()}/128/128`,
        songTitle: "AI Generated Track",
        duration: 185,
        modelName: selectedModel.name,
        isGeneratingMedia: false,
        mediaCategory: "song",
      };
    }

    if (lower.includes("video") || lower.includes("clip")) {
      type = "real_video";
      responseText = "I have generated a video for you. Here it is:";
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
      responseText = "Here is a code example based on your request:";
      const codeSnippet = `function hello() {\n  console.log("Hello from Ultra AI!");\n}`;
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

    if (lower.includes("lyrics") || lower.includes("geet") || lower.includes("song words")) {
      type = "real_lyrics";
      responseText = "Here are AI-generated lyrics for you:";
      const lyricsText = `Verse 1:\nDil ki baat suno meri\nAaj raat hai humari\nChand taare sath hain\nYeh pal hain yaadgaar`;

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
      "Bilkul! Main aapke sawal ka jawab dene ke liye tayyar hoon. Thoda detail mein batayein taaki main behtar madad kar sakoon.",
      "Yeh bahut interesting sawal hai. Main iske bare mein soch raha hoon...",
      "Main yeh kaam aapke liye kar sakta hoon. Kya aap kuch specific requirements chahte hain?",
      "Great idea! Aaj hum ise implement karte hain. Step by step guide follow karein.",
      "Main aapke liye ek comprehensive solution taiyar kar raha hoon. Thoda intezar karein...",
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
      />

      <div className="flex-1 flex flex-col min-w-0">
        <Header
          onToggleSidebar={() => setSidebarOpen(!sidebarOpen)}
          selectedModel={selectedModel}
          onOpenVoice={() => setVoiceOpen(true)}
          onOpenSettings={() => setSettingsOpen(true)}
        />

        <main className="flex-1 overflow-y-auto custom-scrollbar">
          <div className="py-6 space-y-6">
            {messages.map((message) => (
              <ChatMessage
                key={message.id}
                message={message}
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
    </div>
  );
}

export default App;
