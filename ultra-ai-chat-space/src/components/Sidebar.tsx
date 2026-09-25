import { useState } from "react";
import {
  Sparkles,
  ArrowLeft,
  LogOut,
  MessageSquare,
  Plus,
  Settings,
  ChevronDown,
  Zap,
  BrainCircuit,
  Bot,
  Music2,
} from "lucide-react";
import type { AIModel, ChatSession } from "../types";

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
  models: AIModel[];
  selectedModel: AIModel;
  onSelectModel: (model: AIModel) => void;
  sessions: ChatSession[];
  activeSessionId: string;
  onSelectSession: (id: string) => void;
  onNewChat: () => void;
  onOpenSettings: () => void;
  onOpenVoice: () => void;
  authUser?: { name: string; email: string; picture: string } | null;
  onOpenAuth?: () => void;
  onSignOut?: () => void;
  flowCredits?: number;
}

export default function Sidebar({
  isOpen,
  onClose: _onClose,
  models,
  selectedModel,
  onSelectModel,
  sessions,
  activeSessionId,
  onSelectSession,
  onNewChat,
  onOpenSettings,
  onOpenVoice,
  authUser,
  onOpenAuth,
  onSignOut,
  flowCredits = 50,
}: SidebarProps) {
  const [showModelPicker, setShowModelPicker] = useState(false);
  const displayName = authUser?.name || "Prince Laghari";
  const initials = displayName
    .split(" ")
    .map((n) => n[0])
    .filter(Boolean)
    .join("")
    .slice(0, 2)
    .toUpperCase() || "PL";

  return (
    <aside
      className={`fixed lg:relative inset-y-0 left-0 z-40 w-72 bg-slate-950/80 backdrop-blur-xl border-r border-slate-800/60 flex flex-col transition-transform duration-300 ease-in-out ${
        isOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"
      }`}
    >
      <div className="p-4 border-b border-slate-800/60 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="relative flex items-center justify-center w-10 h-10 rounded-xl bg-gradient-to-tr from-violet-600 via-indigo-500 to-cyan-400 p-0.5 shadow-lg shadow-indigo-500/20">
            <div className="w-full h-full bg-slate-950 rounded-[10px] flex items-center justify-center">
              <Sparkles className="w-5 h-5 text-cyan-400 animate-pulse" />
            </div>
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <h1 className="font-bold text-slate-100 tracking-wide text-base">Ultra AI</h1>
              <span className="text-[10px] font-semibold uppercase tracking-wider px-1.5 py-0.5 rounded-full bg-gradient-to-r from-cyan-500/20 to-blue-500/20 text-cyan-300 border border-cyan-500/30">
                PRO
              </span>
            </div>
            <p className="text-xs text-slate-400">Next-Gen Creative Studio</p>
          </div>
        </div>
      </div>

      <div className="p-3">
        <button
          onClick={onNewChat}
          className="w-full flex items-center justify-center gap-2 py-3 px-4 rounded-xl bg-gradient-to-r from-indigo-600 via-purple-600 to-pink-600 hover:from-indigo-500 hover:via-purple-500 hover:to-pink-500 text-white font-medium text-sm shadow-lg shadow-purple-500/20 transition-all duration-200 active:scale-[0.98] group"
        >
          <Plus className="w-4 h-4 transition-transform group-hover:rotate-90" />
          <span>Naya AI Chat Shuru Karein</span>
        </button>
      </div>

      <div className="px-3 py-2">
        <div className="relative">
          <button
            onClick={() => setShowModelPicker(!showModelPicker)}
            className="w-full flex items-center justify-between p-2.5 rounded-xl bg-slate-900/80 hover:bg-slate-800/80 border border-slate-800 transition-all text-left group"
          >
            <div className="flex items-center gap-2.5">
              <div
                className={`p-2 rounded-lg bg-slate-950 border border-slate-800 ${selectedModel.color}`}
              >
                <BrainCircuit className="w-4 h-4" />
              </div>
              <div>
                <div className="text-xs font-semibold text-slate-200 flex items-center gap-1.5">
                  {selectedModel.name}
                  <span className="text-[9px] px-1.5 py-0.2 rounded bg-slate-800 text-slate-400">
                    {selectedModel.badge}
                  </span>
                </div>
                <div className="text-[11px] text-slate-400 truncate max-w-[130px]">
                  {selectedModel.description}
                </div>
              </div>
            </div>
            <ChevronDown
              className={`w-4 h-4 text-slate-400 transition-transform duration-200 ${
                showModelPicker ? "rotate-180" : ""
              }`}
            />
          </button>

          {showModelPicker && (
            <div className="absolute top-full left-0 right-0 mt-2 p-2 bg-slate-900/95 backdrop-blur-2xl border border-slate-700/80 rounded-2xl shadow-2xl z-50 space-y-1 animate-in fade-in zoom-in-95 duration-150">
              <div className="px-2 py-1 text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                Select Engine Model
              </div>
              {models.map((model) => (
                <button
                  key={model.id}
                  onClick={() => {
                    onSelectModel(model);
                    setShowModelPicker(false);
                  }}
                  className={`w-full flex items-center gap-3 p-2 rounded-xl text-left transition-all ${
                    selectedModel.id === model.id
                      ? "bg-indigo-600/20 border border-indigo-500/40 text-slate-100"
                      : "hover:bg-slate-800/60 text-slate-300"
                  }`}
                >
                  <div
                    className={`p-2 rounded-lg bg-slate-950 border border-slate-800 ${model.color}`}
                  >
                    <Bot className="w-4 h-4" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="text-xs font-medium text-slate-200 flex items-center justify-between">
                      <span>{model.name}</span>
                      <span className="text-[9px] text-cyan-400">{model.speed}</span>
                    </div>
                    <div className="text-[10px] text-slate-400 truncate">
                      {model.description}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="px-3 py-2 space-y-1">
        <button
          onClick={onOpenVoice}
          className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl bg-gradient-to-r from-cyan-500/10 to-blue-500/10 border border-cyan-500/20 hover:border-cyan-500/40 text-cyan-300 text-xs font-medium transition-all group"
        >
          <div className="flex items-center gap-2.5">
            <Zap className="w-4 h-4 text-cyan-400 animate-bounce" />
            <span>Live Ultra Voice AI</span>
          </div>
          <span className="text-[10px] px-1.5 py-0.5 rounded bg-cyan-500/20 text-cyan-200 font-bold">
            LIVE
          </span>
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-2 space-y-1 custom-scrollbar">
        <div className="px-2 py-1 flex items-center justify-between text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
          <span>Recent Conversations</span>
          <MessageSquare className="w-3.5 h-3.5 text-slate-500" />
        </div>

        {sessions.map((session) => {
          const isActive = session.id === activeSessionId;
          return (
            <button
              key={session.id}
              onClick={() => onSelectSession(session.id)}
              className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-xs text-left transition-all group relative ${
                isActive
                  ? "bg-slate-800/80 text-white font-medium border border-slate-700/60 shadow-inner"
                  : "text-slate-400 hover:text-slate-200 hover:bg-slate-900/50"
              }`}
            >
              <MessageSquare
                className={`w-3.5 h-3.5 shrink-0 ${
                  isActive ? "text-cyan-400" : "text-slate-500"
                }`}
              />
              <span className="truncate flex-1">{session.title}</span>
              <span className="text-[10px] text-slate-500 opacity-0 group-hover:opacity-100 transition-opacity">
                {session.date}
              </span>
            </button>
          );
        })}
      </div>

      <div className="p-3 border-t border-slate-800/60">
        <button
          onClick={() => {
            const androidOAuth = (window as any).AndroidOAuth;
            if (androidOAuth && typeof androidOAuth.exitToHome === "function") {
              androidOAuth.exitToHome();
            } else if (window.history.length > 1) {
              window.history.back();
            }
          }}
          className="w-full flex items-center justify-center gap-2 py-2 px-3 rounded-xl bg-slate-900/90 hover:bg-slate-800 border border-slate-800 text-slate-300 hover:text-white text-xs font-medium transition-all"
        >
          <ArrowLeft className="w-3.5 h-3.5 text-cyan-400" />
          <span>PK AI Home Screen</span>
        </button>
      </div>

      {/* FlowMusic Daily Credits Card */}
      <div className="mx-3 mb-2 p-2.5 rounded-xl bg-gradient-to-r from-pink-950/40 via-purple-950/30 to-slate-900/90 border border-pink-500/20 shadow-lg">
        <div className="flex items-center justify-between text-[11px] mb-1.5">
          <span className="text-slate-300 font-medium flex items-center gap-1.5">
            <Music2 className="w-3.5 h-3.5 text-pink-400" />
            Ultra AI 4 Credits
          </span>
          <span className="text-pink-300 font-bold">{flowCredits} / 50</span>
        </div>
        <div className="w-full h-1.5 bg-slate-800/80 rounded-full overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-pink-500 to-cyan-400 rounded-full transition-all duration-300"
            style={{ width: `${Math.min(100, Math.max(0, (flowCredits / 50) * 100))}%` }}
          />
        </div>
        <div className="flex items-center justify-between mt-1.5 text-[9px] text-slate-400">
          <span>Daily Free Refresh</span>
          <span className="text-cyan-400 font-mono">flowmusic.app</span>
        </div>
      </div>

      <div className="p-3 border-t border-slate-800/60 bg-slate-950/80 flex items-center justify-between">
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="relative shrink-0">
            {authUser?.picture ? (
              <img
                src={authUser.picture}
                alt={displayName}
                className="w-9 h-9 rounded-full object-cover border-2 border-indigo-500/60"
              />
            ) : (
              <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-pink-500 via-purple-500 to-indigo-500 p-0.5 shadow-md">
                <div className="w-full h-full bg-slate-900 rounded-full flex items-center justify-center text-xs font-bold text-white">
                  {initials}
                </div>
              </div>
            )}
            <span className={`absolute bottom-0 right-0 w-2.5 h-2.5 ${authUser ? "bg-emerald-500" : "bg-cyan-500"} border-2 border-slate-950 rounded-full`} />
          </div>
          <div className="min-w-0">
            <div className="text-xs font-semibold text-slate-200 truncate">{displayName}</div>
            <div className="text-[10px] text-cyan-400 font-medium truncate">
              {authUser ? "Ultra Chat AI Connected" : "Ultra Chat AI Account"}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-1">
          {authUser ? (
            <button
              onClick={onSignOut}
              className="p-2 rounded-xl text-slate-400 hover:text-red-400 hover:bg-slate-800/80 transition-colors"
              title="Sign Out"
            >
              <LogOut className="w-4 h-4" />
            </button>
          ) : (
            <button
              onClick={onOpenAuth}
              className="px-2 py-1.5 rounded-lg bg-indigo-600/30 hover:bg-indigo-600/50 border border-indigo-500/40 text-[11px] text-indigo-300 font-medium transition-all"
              title="Connect Account"
            >
              Sign In
            </button>
          )}
          <button
            onClick={onOpenSettings}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/80 transition-colors"
            title="Settings"
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>
      </div>
    </aside>
  );
}
