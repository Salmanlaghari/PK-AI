import React, { useState, useRef, useEffect } from "react";
import { Send, Image as ImageIcon, Mic, Square } from "lucide-react";

interface ChatInputProps {
  onSend: (text: string) => void;
  onImageUpload?: (file: File) => void;
  onVoiceRecord?: () => void;
  onStopGenerating?: () => void;
  isGenerating?: boolean;
}

export default function ChatInput({
  onSend,
  onImageUpload,
  onVoiceRecord,
  onStopGenerating,
  isGenerating = false,
}: ChatInputProps) {
  const [text, setText] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
    }
  }, [text]);

  const handleSubmit = () => {
    const trimmed = text.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setText("");
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && onImageUpload) {
      onImageUpload(file);
    }
    e.target.value = "";
  };

  return (
    <div className="border-t border-slate-800/60 bg-slate-950/80 backdrop-blur-xl p-4">
      <div className="max-w-4xl mx-auto w-full">
        <div className="flex items-end gap-2 bg-slate-900/80 border border-slate-800 rounded-2xl p-2 focus-within:border-cyan-500/40 transition-colors">
          <label className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors cursor-pointer shrink-0">
            <input
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleFileChange}
            />
            <ImageIcon className="w-5 h-5" />
          </label>

          <textarea
            ref={textareaRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type a message... (Shift+Enter for new line)"
            className="flex-1 bg-transparent text-sm text-slate-100 placeholder:text-slate-500 outline-none resize-none py-2 max-h-[200px] min-h-[40px]"
            rows={1}
          />

          {isGenerating ? (
            <button
              onClick={onStopGenerating}
              className="p-2 rounded-xl bg-red-500/20 text-red-400 hover:bg-red-500/30 border border-red-500/40 transition-all shrink-0"
              title="Stop generating"
            >
              <Square className="w-5 h-5 fill-current" />
            </button>
          ) : (
            <>
              <button
                onClick={onVoiceRecord}
                className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors shrink-0"
                title="Voice input"
              >
                <Mic className="w-5 h-5" />
              </button>
              <button
                onClick={handleSubmit}
                disabled={!text.trim()}
                className="p-2 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 text-white hover:from-indigo-500 hover:to-purple-500 disabled:opacity-40 disabled:cursor-not-allowed transition-all shrink-0 shadow-lg shadow-indigo-500/20"
                title="Send message"
              >
                <Send className="w-5 h-5" />
              </button>
            </>
          )}
        </div>
        <div className="text-center mt-2 text-[10px] text-slate-500">
          Ultra AI may produce inaccurate information. Verify important details.
        </div>
      </div>
    </div>
  );
}
