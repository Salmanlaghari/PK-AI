import { useState, useRef, useEffect } from "react";
import { Play, Pause, Music, Sparkles, Send, Volume2, VolumeX, Check } from "lucide-react";
import {
  requestFlowMusicTrack,
  getFlowMusicStatus,
  connectFlowMusic,
} from "../services/flowMusicService";

interface FlowStudioEmbedProps {
  className?: string;
  theme?: "light" | "dark";
  onTrackGenerated?: (trackUrl: string) => void;
  onError?: (error: Error) => void;
  userName?: string;
  onClose?: () => void;
  onOpenAuth?: () => void;
}

const PRESET_GENRES = [
  { id: "urdu-pop", name: "Urdu Romantic Pop", desc: "Melodic vocals & modern bass", prompt: "Romantic Urdu pop melody with acoustic guitar and soothing beats" },
  { id: "sufi", name: "Sufi Fusion", desc: "Harmonium, flute & tabla groove", prompt: "Soulful Sufi qawwali fusion with tabla rhythm and deep pads" },
  { id: "hip-hop", name: "Urdu Hip-Hop Drill", desc: "Hard 808s & rhythmic bounce", prompt: "Punchy Urdu drill beat with dark synth strings and heavy 808" },
  { id: "lofi", name: "Lo-Fi Midnight Chill", desc: "Rain sounds & warm rhodes", prompt: "Late-night lo-fi chill beat with vinyl crackle and mellow keys" },
  { id: "synth", name: "Cyberpunk Synthwave", desc: "Analog arpeggio & neon drive", prompt: "Retro synthwave track with driving 80s drums and neon arpeggio" },
  { id: "acoustic", name: "Acoustic Melody", desc: "Clean guitar & soft percussion", prompt: "Heartwarming acoustic fingerstyle guitar song" },
];

export default function FlowStudioEmbed({
  className,
  onTrackGenerated,
  onError,
  userName = "Ultra AI User",
  onClose,
  onOpenAuth,
}: FlowStudioEmbedProps) {
  const [prompt, setPrompt] = useState("");
  const [selectedGenre, setSelectedGenre] = useState(PRESET_GENRES[0]);
  const [isGenerating, setIsGenerating] = useState(false);
  const [generatedTrack, setGeneratedTrack] = useState<{
    title: string;
    url: string;
    genre: string;
  } | null>(null);

  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(180);
  const [isMuted, setIsMuted] = useState(false);
  const [addedToChat, setAddedToChat] = useState(false);
  const [genError, setGenError] = useState<string | null>(null);

  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const handleTimeUpdate = () => setCurrentTime(audio.currentTime);
    const handleLoadedMetadata = () => setDuration(audio.duration || 180);
    const handleEnded = () => setIsPlaying(false);

    audio.addEventListener("timeupdate", handleTimeUpdate);
    audio.addEventListener("loadedmetadata", handleLoadedMetadata);
    audio.addEventListener("ended", handleEnded);

    return () => {
      audio.removeEventListener("timeupdate", handleTimeUpdate);
      audio.removeEventListener("loadedmetadata", handleLoadedMetadata);
      audio.removeEventListener("ended", handleEnded);
    };
  }, [generatedTrack]);

  const togglePlay = () => {
    if (!audioRef.current) return;
    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
    } else {
      audioRef.current.play().catch(() => {});
      setIsPlaying(true);
    }
  };

  const toggleMute = () => {
    if (!audioRef.current) return;
    audioRef.current.muted = !isMuted;
    setIsMuted(!isMuted);
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    const seekTime = Number(e.target.value);
    if (audioRef.current) {
      audioRef.current.currentTime = seekTime;
      setCurrentTime(seekTime);
    }
  };

  const handleGenerate = async () => {
    const activePrompt = prompt.trim() || selectedGenre.prompt;
    setIsGenerating(true);
    setAddedToChat(false);
    setGenError(null);

    try {
      // Ensure a real Flow Music session exists before generating.
      if (!getFlowMusicStatus().signedIn) {
        connectFlowMusic();
        setGenError(
          "Pehle apne Flow Music account se sign in karein. Sign-in window khul gayi hai — connect hone ke baad dobara Generate dabayein."
        );
        setIsGenerating(false);
        return;
      }

      const result = await requestFlowMusicTrack(activePrompt);
      if (result.ok && result.audioUrl) {
        setGeneratedTrack({
          title: result.title || `${selectedGenre.name} - ${activePrompt.slice(0, 30)}`,
          url: result.audioUrl,
          genre: selectedGenre.name,
        });
        setIsPlaying(false);
        setCurrentTime(0);
      } else {
        const msg = result.error || "Flow Music se track generate nahi ho saka.";
        setGenError(msg);
        onError?.(new Error(msg));
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Flow Music generation error.";
      setGenError(msg);
      onError?.(err instanceof Error ? err : new Error(msg));
    } finally {
      setIsGenerating(false);
    }
  };

  const handleSendToChat = () => {
    if (!generatedTrack) return;
    onTrackGenerated?.(generatedTrack.url);
    setAddedToChat(true);
    setTimeout(() => {
      onClose?.();
    }, 800);
  };

  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${m}:${s < 10 ? "0" : ""}${s}`;
  };

  return (
    <div className={`relative w-full h-full min-h-[420px] bg-slate-950 border border-slate-800/80 rounded-2xl overflow-y-auto p-4 md:p-6 custom-scrollbar ${className || ""}`}>
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 pb-4 border-b border-slate-800/80">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-pink-500 via-purple-600 to-indigo-600 p-0.5 shadow-lg shadow-purple-500/20">
            <div className="w-full h-full bg-slate-950 rounded-[10px] flex items-center justify-center">
              <Music className="w-5 h-5 text-pink-400" />
            </div>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-base font-bold text-slate-100">FlowMusic Studio</h3>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-pink-500/20 text-pink-300 border border-pink-500/30 font-semibold">
                AI Engine
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Account: <span className="text-cyan-400 font-medium">{userName}</span>
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => {
              if (onOpenAuth) {
                onOpenAuth();
              } else {
                const androidOAuth = (window as any).AndroidOAuth;
                if (androidOAuth && typeof androidOAuth.startGoogleSignIn === "function") {
                  androidOAuth.startGoogleSignIn();
                } else if (androidOAuth && typeof androidOAuth.openFlowMusicSignUp === "function") {
                  androidOAuth.openFlowMusicSignUp();
                } else {
                  window.open("https://flowmusic.app", "_blank");
                }
              }
            }}
            className="flex items-center gap-1.5 text-xs text-pink-300 bg-pink-950/60 hover:bg-pink-900/80 px-3 py-1.5 rounded-xl border border-pink-700/60 transition-colors"
          >
            <span>Sign Up / Connect FlowMusic</span>
          </button>
          <div className="hidden sm:flex items-center gap-2 text-xs text-slate-400 bg-slate-900/80 px-3 py-1.5 rounded-xl border border-slate-800">
            <Sparkles className="w-3.5 h-3.5 text-yellow-400" />
            <span>High Fidelity 320kbps AI Stems</span>
          </div>
        </div>
      </div>

      {/* Genre Selector */}
      <div className="mt-5 space-y-2">
        <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
          Music Style & Mood
        </label>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
          {PRESET_GENRES.map((genre) => {
            const isSelected = selectedGenre.id === genre.id;
            return (
              <button
                key={genre.id}
                onClick={() => {
                  setSelectedGenre(genre);
                  setPrompt(genre.prompt);
                }}
                className={`p-2.5 rounded-xl text-left transition-all border ${
                  isSelected
                    ? "bg-purple-600/20 border-purple-500 text-white shadow-md shadow-purple-500/10"
                    : "bg-slate-900/60 border-slate-800 hover:bg-slate-900 text-slate-300"
                }`}
              >
                <div className="text-xs font-semibold">{genre.name}</div>
                <div className="text-[10px] text-slate-400 truncate">{genre.desc}</div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Prompt Input */}
      <div className="mt-5 space-y-2">
        <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
          Track Prompt & Instructions
        </label>
        <div className="relative">
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            placeholder="Describe the mood, instruments, lyrics or vibe..."
            rows={2}
            className="w-full px-4 py-3 rounded-xl bg-slate-900 border border-slate-800 text-sm text-slate-100 placeholder:text-slate-500 outline-none focus:border-purple-500/60 transition-all resize-none"
          />
        </div>
      </div>

      {/* Generate Button */}
      <div className="mt-4">
        <button
          onClick={handleGenerate}
          disabled={isGenerating}
          className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-pink-600 via-purple-600 to-indigo-600 hover:from-pink-500 hover:via-purple-500 hover:to-indigo-500 text-white font-medium text-sm shadow-lg shadow-purple-500/25 transition-all duration-200 active:scale-[0.99] disabled:opacity-50 flex items-center justify-center gap-2"
        >
          {isGenerating ? (
            <>
              <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              <span>FlowMusic Synthesizing Melody...</span>
            </>
          ) : (
            <>
              <Sparkles className="w-4 h-4" />
              <span>Generate FlowMusic Track</span>
            </>
          )}
        </button>
      </div>

      {genError && (
        <div className="mt-3 p-3 rounded-xl bg-rose-950/50 border border-rose-700/60 text-rose-200 text-xs leading-relaxed">
          {genError}
        </div>
      )}

      {/* Generated Track Player */}
      {generatedTrack && (
        <div className="mt-6 p-4 rounded-2xl bg-slate-900/90 border border-purple-500/30 shadow-xl space-y-4 animate-in fade-in zoom-in-95">
          <audio ref={audioRef} src={generatedTrack.url} preload="metadata" />

          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3 min-w-0">
              <button
                onClick={togglePlay}
                className="shrink-0 w-11 h-11 rounded-xl bg-gradient-to-tr from-pink-500 to-purple-600 flex items-center justify-center text-white shadow-lg shadow-purple-500/30 hover:scale-105 active:scale-95 transition-all"
              >
                {isPlaying ? <Pause className="w-5 h-5 fill-current" /> : <Play className="w-5 h-5 fill-current ml-0.5" />}
              </button>
              <div className="min-w-0">
                <div className="text-sm font-semibold text-slate-100 truncate">{generatedTrack.title}</div>
                <div className="text-xs text-purple-400 font-medium">{generatedTrack.genre} • FlowMusic Engine</div>
              </div>
            </div>

            <button
              onClick={toggleMute}
              className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            >
              {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
            </button>
          </div>

          {/* Progress Bar & Waveform Effect */}
          <div className="space-y-1.5">
            <input
              type="range"
              min={0}
              max={duration || 180}
              value={currentTime}
              onChange={handleSeek}
              className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-pink-500"
            />
            <div className="flex justify-between text-[11px] text-slate-400 font-mono">
              <span>{formatTime(currentTime)}</span>
              <div className="flex items-center gap-1">
                <span className={`w-1 h-3 rounded-full bg-pink-500 ${isPlaying ? "animate-pulse" : "opacity-40"}`} />
                <span className={`w-1 h-4 rounded-full bg-purple-500 ${isPlaying ? "animate-pulse delay-75" : "opacity-40"}`} />
                <span className={`w-1 h-2 rounded-full bg-indigo-500 ${isPlaying ? "animate-pulse delay-150" : "opacity-40"}`} />
              </div>
              <span>{formatTime(duration)}</span>
            </div>
          </div>

          {/* Action to send to chat */}
          <button
            onClick={handleSendToChat}
            disabled={addedToChat}
            className="w-full py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold flex items-center justify-center gap-2 transition-all shadow-md shadow-emerald-600/20"
          >
            {addedToChat ? (
              <>
                <Check className="w-4 h-4" />
                <span>Track Sent to Ultra AI Chat!</span>
              </>
            ) : (
              <>
                <Send className="w-4 h-4" />
                <span>Send Track to Ultra AI Chat</span>
              </>
            )}
          </button>
        </div>
      )}
    </div>
  );
}
