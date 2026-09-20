import { useState, useEffect, useRef } from "react";
import { X, Mic, Square, Volume2 } from "lucide-react";

interface VoiceModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSendTranscript: (text: string) => void;
}

export default function VoiceModal({ isOpen, onClose, onSendTranscript }: VoiceModalProps) {
  const [isListening, setIsListening] = useState(false);
  const [transcript, setTranscript] = useState("");
  const [interimTranscript, setInterimTranscript] = useState("");
  const recognitionRef = useRef<any>(null);

  useEffect(() => {
    if (!isOpen) {
      setIsListening(false);
      setTranscript("");
      setInterimTranscript("");
      if (recognitionRef.current) {
        recognitionRef.current.stop();
        recognitionRef.current = null;
      }
      return;
    }

    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setTranscript("Speech recognition is not supported in this browser.");
      return;
    }

    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = "en-US";

    recognition.onresult = (event: any) => {
      let interim = "";
      let final = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcriptPart = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          final += transcriptPart;
        } else {
          interim += transcriptPart;
        }
      }
      if (final) {
        setTranscript((prev) => prev + final);
      }
      setInterimTranscript(interim);
    };

    recognition.onerror = (event: any) => {
      console.error("Speech recognition error:", event.error);
      setIsListening(false);
    };

    recognition.onend = () => {
      setIsListening(false);
    };

    recognitionRef.current = recognition;
    recognition.start();
    setIsListening(true);

    return () => {
      recognition.stop();
    };
  }, [isOpen]);

  const handleSend = () => {
    const fullText = transcript + interimTranscript;
    if (fullText.trim()) {
      onSendTranscript(fullText.trim());
    }
    onClose();
  };

  const toggleListening = () => {
    if (!recognitionRef.current) return;
    if (isListening) {
      recognitionRef.current.stop();
      setIsListening(false);
    } else {
      recognitionRef.current.start();
      setIsListening(true);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl w-full max-w-md overflow-hidden">
        <div className="flex items-center justify-between p-4 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <Volume2 className="w-5 h-5 text-cyan-400" />
            <h2 className="text-lg font-semibold text-slate-100">Voice Mode</h2>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 flex flex-col items-center gap-4">
          <div className="relative">
            <button
              onClick={toggleListening}
              className={`w-20 h-20 rounded-full flex items-center justify-center transition-all ${
                isListening
                  ? "bg-red-500/20 border-2 border-red-500/60 text-red-400 animate-pulse"
                  : "bg-cyan-500/20 border-2 border-cyan-500/60 text-cyan-400"
              }`}
            >
              {isListening ? (
                <Square className="w-8 h-8 fill-current" />
              ) : (
                <Mic className="w-8 h-8" />
              )}
            </button>
          </div>

          <div className="text-center">
            <div className="text-sm text-slate-300 mb-1">
              {isListening ? "Listening..." : "Tap to start speaking"}
            </div>
            <div className="text-xs text-slate-500">
              {isListening ? "Speak clearly into your microphone" : "Voice input is ready"}
            </div>
          </div>

          {(transcript || interimTranscript) && (
            <div className="w-full p-4 rounded-xl bg-slate-950/80 border border-slate-800 text-sm text-slate-300 max-h-[200px] overflow-y-auto custom-scrollbar">
              <p>{transcript}</p>
              <p className="text-slate-500 italic">{interimTranscript}</p>
            </div>
          )}

          <div className="flex gap-2 w-full">
            <button
              onClick={onClose}
              className="flex-1 py-2.5 rounded-xl bg-slate-800/60 text-slate-300 hover:text-white border border-slate-700 text-sm font-medium transition-all"
            >
              Cancel
            </button>
            <button
              onClick={handleSend}
              disabled={!transcript && !interimTranscript}
              className="flex-1 py-2.5 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 text-white hover:from-indigo-500 hover:to-purple-500 disabled:opacity-40 text-sm font-medium transition-all"
            >
              Send
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
