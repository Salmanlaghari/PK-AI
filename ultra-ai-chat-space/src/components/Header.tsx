import {
  Menu,
  Mic,
  Sliders,
  Activity,
  Music2,
} from "lucide-react";
import type { AIModel } from "../types";

interface HeaderProps {
  onToggleSidebar: () => void;
  selectedModel: AIModel;
  onOpenVoice: () => void;
  onOpenSettings: () => void;
  onOpenFlowStudio: () => void;
  flowCredits?: number;
}

export default function Header({
  onToggleSidebar,
  selectedModel,
  onOpenVoice,
  onOpenSettings,
  onOpenFlowStudio,
  flowCredits = 50,
}: HeaderProps) {
  return (
    <header className="h-16 border-b border-slate-800/60 bg-slate-950/60 backdrop-blur-2xl px-4 flex items-center justify-between shrink-0 z-30">
      <div className="flex items-center gap-3">
        <button
          onClick={onToggleSidebar}
          className="lg:hidden p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-900/90 border border-slate-800">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
            <span className="text-xs font-medium text-slate-200">{selectedModel.name}</span>
            <span className="text-[10px] text-cyan-400 bg-cyan-950/60 border border-cyan-800/60 px-1.5 py-0.5 rounded-full font-semibold">
              Ultra Speed
            </span>
          </div>

          <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-slate-900/40 border border-slate-800/40 text-[11px] text-slate-400">
            <Activity className="w-3.5 h-3.5 text-emerald-400" />
            <span>12ms Latency</span>
          </div>

          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-pink-950/50 border border-pink-800/50 text-[11px] text-pink-300" title="FlowMusic.app Daily Credits">
            <span className="w-1.5 h-1.5 rounded-full bg-pink-400 animate-pulse" />
            <span className="font-semibold">{flowCredits}</span>
            <span className="hidden md:inline text-pink-400/80">Flow Credits</span>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <button
          onClick={onOpenFlowStudio}
          className="flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-pink-500/20 via-rose-500/20 to-orange-500/20 border border-pink-500/40 hover:border-pink-400 text-pink-200 hover:text-white text-xs font-semibold shadow-lg shadow-pink-500/10 transition-all active:scale-95"
        >
          <Music2 className="w-3.5 h-3.5 text-pink-400 animate-pulse" />
          <span className="hidden md:inline">Flow Studio</span>
        </button>

        <button
          onClick={onOpenVoice}
          className="flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-cyan-500/20 via-indigo-500/20 to-purple-500/20 border border-cyan-500/40 hover:border-cyan-400 text-cyan-200 hover:text-white text-xs font-semibold shadow-lg shadow-cyan-500/10 transition-all active:scale-95"
        >
          <Mic className="w-3.5 h-3.5 text-cyan-400 animate-pulse" />
          <span className="hidden md:inline">Voice Mode</span>
        </button>

        <button
          onClick={onOpenSettings}
          className="p-2.5 rounded-xl text-slate-400 hover:text-slate-200 hover:bg-slate-800/60 border border-transparent hover:border-slate-800 transition-all"
        >
          <Sliders className="w-4 h-4" />
        </button>
      </div>
    </header>
  );
}
