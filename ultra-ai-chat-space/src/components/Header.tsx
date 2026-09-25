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
  onOpenAuth?: () => void;
  authUser?: { name: string; email: string; picture: string } | null;
  flowCredits?: number;
  flowConnected?: boolean;
}

export default function Header({
  onToggleSidebar,
  selectedModel,
  onOpenVoice,
  onOpenSettings,
  onOpenFlowStudio,
  onOpenAuth,
  flowCredits = 50,
  flowConnected = false,
}: HeaderProps) {
  return (
    <header className="h-16 border-b border-slate-800/60 bg-slate-950/60 backdrop-blur-2xl px-3 sm:px-4 flex items-center justify-between shrink-0 z-30">
      <div className="flex items-center gap-2 sm:gap-3 min-w-0">
        <button
          onClick={onToggleSidebar}
          className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors shrink-0"
          title="Toggle Navigation"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-1.5 sm:gap-2 min-w-0">
          <div className="flex items-center gap-1.5 px-2.5 py-1 sm:px-3 sm:py-1.5 rounded-full bg-slate-900/90 border border-slate-800 shrink-0">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping shrink-0" />
            <span className="text-xs font-medium text-slate-200 truncate max-w-[90px] sm:max-w-none">{selectedModel.name}</span>
            <span className="hidden sm:inline text-[10px] text-cyan-400 bg-cyan-950/60 border border-cyan-800/60 px-1.5 py-0.5 rounded-full font-semibold">
              Ultra Speed
            </span>
          </div>

          <div className="hidden md:flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-slate-900/40 border border-slate-800/40 text-[11px] text-slate-400">
            <Activity className="w-3.5 h-3.5 text-emerald-400" />
            <span>12ms Latency</span>
          </div>

          {/* FlowMusic Daily Credits Badge - Always Visible */}
          <button
            onClick={onOpenAuth || onOpenFlowStudio}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-gradient-to-r from-pink-950/70 via-purple-950/60 to-slate-900/90 border border-pink-500/40 hover:border-pink-400 text-[11px] text-pink-200 transition-all active:scale-95 shrink-0"
            title="FlowMusic.app Daily Credits & Account"
          >
            <span className="w-1.5 h-1.5 rounded-full bg-pink-400 animate-pulse shrink-0" />
            <span className="font-bold text-pink-300">{flowCredits}</span>
            <span className="text-pink-300/90 font-medium">Credits</span>
          </button>
        </div>
      </div>

      <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
        <button
          onClick={onOpenFlowStudio}
          className={`flex items-center gap-1.5 px-2.5 sm:px-3.5 py-1.5 rounded-xl border text-xs font-semibold shadow-lg transition-all active:scale-95 ${
            flowConnected
              ? "bg-gradient-to-r from-emerald-500/20 via-teal-500/20 to-cyan-500/20 border-emerald-500/50 hover:border-emerald-400 text-emerald-200 hover:text-white shadow-emerald-500/10"
              : "bg-gradient-to-r from-pink-500/20 via-rose-500/20 to-orange-500/20 border-pink-500/40 hover:border-pink-400 text-pink-200 hover:text-white shadow-pink-500/10"
          }`}
          title={flowConnected ? "Flow Music connected" : "Connect your Flow Music account"}
        >
          <Music2 className={`w-3.5 h-3.5 shrink-0 ${flowConnected ? "text-emerald-400" : "text-pink-400 animate-pulse"}`} />
          <span className="hidden xs:inline sm:inline">
            {flowConnected ? "Flow Music ✓" : "Connect"}
          </span>
        </button>

        <button
          onClick={onOpenVoice}
          className="flex items-center gap-1.5 px-2.5 sm:px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-cyan-500/20 via-indigo-500/20 to-purple-500/20 border border-cyan-500/40 hover:border-cyan-400 text-cyan-200 hover:text-white text-xs font-semibold shadow-lg shadow-cyan-500/10 transition-all active:scale-95"
          title="Voice Assistant"
        >
          <Mic className="w-3.5 h-3.5 text-cyan-400 animate-pulse shrink-0" />
          <span className="hidden sm:inline">Voice</span>
        </button>

        <button
          onClick={onOpenSettings}
          className="p-2 sm:p-2.5 rounded-xl text-slate-400 hover:text-slate-200 hover:bg-slate-800/60 border border-transparent hover:border-slate-800 transition-all shrink-0"
          title="Settings"
        >
          <Sliders className="w-4 h-4" />
        </button>
      </div>
    </header>
  );
}
