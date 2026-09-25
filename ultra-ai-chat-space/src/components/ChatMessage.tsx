import { useState } from "react";
import {
  Bot,
  Copy,
  Check,
  Music,
  ThumbsUp,
  RefreshCw,
  Download,
  Disc,
  Loader2,
  FileText,
} from "lucide-react";
import type { Message } from "../types";

interface ChatMessageProps {
  message: Message;
  userName?: string;
  onRegenerate?: () => void;
  onGenerateSongFromLyrics?: (soundPrompt: string, title: string) => void;
}

export default function ChatMessage({ message, userName, onRegenerate, onGenerateSongFromLyrics: _onGenerateSongFromLyrics }: ChatMessageProps) {
  const [copied, setCopied] = useState(false);
  const [liked, setLiked] = useState(false);

  const isAI = message.sender === "ai";

  const handleCopy = () => {
    const textToCopy = message.text + (message.lyricsText ? "\n\n" + message.lyricsText : "");
    navigator.clipboard.writeText(textToCopy).catch(() => {});
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = async (url: string, defaultName: string, mimeType: string) => {
    if (!url) return;
    try {
      const androidBridge = (window as any).AndroidOAuth;
      if (androidBridge && typeof androidBridge.downloadFile === "function") {
        androidBridge.downloadFile(url, defaultName, mimeType);
        return;
      }
      const response = await fetch(url, { mode: "cors" });
      if (!response.ok) throw new Error("Fetch failed");
      const blob = await response.blob();
      const blobUrl = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = blobUrl;
      a.download = defaultName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(blobUrl);
    } catch {
      const a = document.createElement("a");
      a.href = url;
      a.target = "_blank";
      a.download = defaultName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    }
  };

  const formatDuration = (seconds: number | null | undefined) => {
    if (!seconds) return null;
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s.toString().padStart(2, "0")}`;
  };

  return (
    <div
      className={`flex gap-3 max-w-4xl mx-auto w-full group ${
        isAI ? "justify-start" : "justify-end"
      }`}
    >
      {isAI && (
        <div className="shrink-0 w-9 h-9 rounded-xl bg-gradient-to-br from-cyan-500 via-indigo-600 to-purple-600 p-0.5 shadow-lg shadow-indigo-500/20">
          <div className="w-full h-full bg-slate-950 rounded-[10px] flex items-center justify-center text-cyan-400">
            <Bot className="w-5 h-5" />
          </div>
        </div>
      )}

      <div
        className={`flex flex-col space-y-2 max-w-[85%] ${
          isAI ? "items-start" : "items-end"
        }`}
      >
        <div className="flex items-center gap-2 text-[11px] text-slate-400 px-1">
          <span className="font-semibold text-slate-300">
            {isAI ? (message.modelName || "Ultra AI Engine") : (userName ? `Aap (${userName})` : "Aap (Prince Laghari)")}
          </span>
          <span>•</span>
          <span>{message.timestamp}</span>
        </div>

        <div
          className={`p-4 rounded-2xl text-sm leading-relaxed shadow-xl backdrop-blur-md transition-all ${
            isAI
              ? "bg-slate-900/90 border border-slate-800 text-slate-100 rounded-tl-xs"
              : "bg-gradient-to-r from-indigo-600 via-purple-600 to-pink-600 text-white rounded-tr-xs shadow-purple-500/10"
          }`}
        >
          <p className="whitespace-pre-wrap">{message.text}</p>

          {/* REAL AI SONG CARD */}
          {message.type === "real_song" && message.audioUrl && (
            <div className="mt-3 p-4 rounded-2xl bg-slate-950/90 border border-cyan-500/40 shadow-2xl flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Disc className="w-5 h-5 text-cyan-400 animate-spin" style={{ animationDuration: '6s' }} />
                  <div>
                    <div className="font-bold text-slate-100 text-sm">{message.songTitle || "AI Generated Track"}</div>
                    <div className="text-[10px] text-pink-400 font-mono">Ultra AI 4 Audio Engine</div>
                  </div>
                </div>
                <span className="text-[10px] px-2.5 py-0.5 rounded-full bg-pink-950 text-pink-300 border border-pink-800 font-semibold">
                  FLOWMUSIC AUDIO
                </span>
              </div>

              <div className="flex flex-col sm:flex-row gap-3 items-center bg-slate-900/90 p-3 rounded-xl border border-slate-800">
                {message.coverImageUrl ? (
                  <img
                    src={message.coverImageUrl}
                    alt="Cover"
                    className="w-16 h-16 rounded-lg object-cover shadow-md"
                  />
                ) : (
                  <div className="w-16 h-16 rounded-lg bg-gradient-to-tr from-cyan-600 to-purple-600 flex items-center justify-center text-white">
                    <Music className="w-7 h-7" />
                  </div>
                )}

                <div className="flex-1 w-full flex flex-col gap-2">
                  <audio
                    src={message.audioUrl}
                    controls
                    className="w-full h-9 rounded-lg accent-cyan-400"
                  />
                  {message.duration && (
                    <div className="text-[10px] font-mono text-slate-400">
                      Duration: {formatDuration(message.duration)}
                    </div>
                  )}
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => setLiked(!liked)}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-medium transition-all ${
                    liked
                      ? "bg-pink-500/20 text-pink-300 border border-pink-500/40"
                      : "bg-slate-800/60 text-slate-300 hover:text-pink-300 border border-slate-700"
                  }`}
                >
                  <ThumbsUp className="w-3.5 h-3.5" />
                  {liked ? "Liked" : "Like"}
                </button>
                <button
                  onClick={() => {
                    const safeName = (message.songTitle || "ultra_ai_track").replace(/[^a-zA-Z0-9_-]/g, "_") + ".mp3";
                    handleDownload(message.audioUrl || "", safeName, "audio/mpeg");
                  }}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800/80 text-slate-300 hover:text-white hover:bg-slate-700 border border-slate-700 text-[11px] font-medium transition-all active:scale-95"
                >
                  <Download className="w-3.5 h-3.5 text-cyan-400" />
                  Download Song
                </button>
              </div>
            </div>
          )}

          {/* REAL AI IMAGE CARD */}
          {message.type === "real_image" && message.imageUrl && (
            <div className="mt-3 rounded-2xl overflow-hidden border border-slate-700 bg-slate-950">
              <img
                src={message.imageUrl}
                alt="AI Generated"
                className="w-full h-auto max-h-[400px] object-contain"
              />
              <div className="p-3 flex items-center justify-between bg-slate-900/80">
                <span className="text-[10px] text-pink-400 font-mono">Ultra AI 4 Visual Engine</span>
                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      const safeName = "ultra_ai_image_" + Date.now() + ".jpg";
                      handleDownload(message.imageUrl || "", safeName, "image/jpeg");
                    }}
                    className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800/80 text-slate-300 hover:text-white hover:bg-slate-700 border border-slate-700 transition-all text-[11px] font-medium active:scale-95"
                    title="Download Image"
                  >
                    <Download className="w-3.5 h-3.5 text-cyan-400" />
                    Download
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* REAL AI VIDEO CARD */}
          {message.type === "real_video" && message.videoUrl && (
            <div className="mt-3 rounded-2xl overflow-hidden border border-slate-700 bg-slate-950">
              <video
                src={message.videoUrl}
                controls
                className="w-full h-auto max-h-[400px]"
              />
              <div className="p-3 flex items-center justify-between bg-slate-900/80">
                <span className="text-[10px] text-violet-400 font-mono">Real AI Video Output</span>
                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      const safeName = "ultra_ai_image_" + Date.now() + ".jpg";
                      handleDownload(message.imageUrl || "", safeName, "image/jpeg");
                    }}
                    className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800/80 text-slate-300 hover:text-white hover:bg-slate-700 border border-slate-700 transition-all text-[11px] font-medium active:scale-95"
                    title="Download Image"
                  >
                    <Download className="w-3.5 h-3.5 text-cyan-400" />
                    Download
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* CODE BLOCK */}
          {message.type === "code" && message.codeSnippet && (
            <div className="mt-3 rounded-xl bg-slate-950 border border-slate-800 overflow-hidden">
              <div className="flex items-center justify-between px-3 py-2 bg-slate-900/80 border-b border-slate-800">
                <span className="text-[10px] text-slate-400 font-mono">Code</span>
                <button
                  onClick={handleCopy}
                  className="flex items-center gap-1 text-[10px] text-slate-400 hover:text-white transition-colors"
                >
                  {copied ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
                  {copied ? "Copied" : "Copy"}
                </button>
              </div>
              <pre className="p-3 overflow-x-auto text-xs leading-relaxed text-slate-300">
                <code>{message.codeSnippet}</code>
              </pre>
            </div>
          )}

          {/* LYRICS */}
          {message.type === "real_lyrics" && message.lyricsText && (
            <div className="mt-3 p-4 rounded-2xl bg-slate-950/90 border border-violet-500/40 shadow-2xl">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <FileText className="w-4 h-4 text-violet-400" />
                  <span className="text-xs font-semibold text-violet-300">AI Generated Lyrics</span>
                </div>
                <button
                  onClick={() => {
                    const blob = new Blob([message.lyricsText || ""], { type: "text/plain;charset=utf-8" });
                    const blobUrl = window.URL.createObjectURL(blob);
                    handleDownload(blobUrl, "ultra_ai_lyrics_" + Date.now() + ".txt", "text/plain");
                  }}
                  className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800/80 text-slate-300 hover:text-white border border-slate-700 text-[10px] font-medium transition-all active:scale-95"
                >
                  <Download className="w-3 h-3 text-pink-400" />
                  Download
                </button>
              </div>
              <p className="whitespace-pre-wrap text-sm text-slate-300 leading-relaxed">
                {message.lyricsText}
              </p>
            </div>
          )}

          {/* MUSIC PROMPT / GENERATING STATE */}
          {message.isGeneratingMedia && (
            <div className="mt-3 flex items-center gap-2 text-cyan-400 text-xs">
              <Loader2 className="w-4 h-4 animate-spin" />
              <span>Generating {message.mediaCategory || "media"}...</span>
            </div>
          )}

          {/* ACTION BUTTONS FOR AI */}
          {isAI && (
            <div className="mt-3 flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
              {onRegenerate && (
                <button
                  onClick={onRegenerate}
                  className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-slate-800/60 text-slate-300 hover:text-white border border-slate-700 text-[11px] font-medium transition-all"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  Regenerate
                </button>
              )}
              <button
                onClick={handleCopy}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-slate-800/60 text-slate-300 hover:text-white border border-slate-700 text-[11px] font-medium transition-all"
              >
                {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                {copied ? "Copied" : "Copy"}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
