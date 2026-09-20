import { X, Moon, Globe, Bell, Shield, Trash2 } from "lucide-react";

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function SettingsModal({ isOpen, onClose }: SettingsModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl w-full max-w-lg max-h-[80vh] overflow-hidden flex flex-col">
        <div className="flex items-center justify-between p-4 border-b border-slate-800">
          <h2 className="text-lg font-semibold text-slate-100">Settings</h2>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-4 custom-scrollbar">
          <div className="p-4 rounded-xl bg-slate-950/50 border border-slate-800">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Moon className="w-5 h-5 text-indigo-400" />
                <div>
                  <div className="text-sm font-medium text-slate-200">Dark Mode</div>
                  <div className="text-xs text-slate-400">Always enabled for best experience</div>
                </div>
              </div>
              <div className="w-10 h-6 rounded-full bg-indigo-500/40 border border-indigo-500/60 relative">
                <div className="absolute right-1 top-1 w-4 h-4 rounded-full bg-indigo-400" />
              </div>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-950/50 border border-slate-800">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Globe className="w-5 h-5 text-cyan-400" />
                <div>
                  <div className="text-sm font-medium text-slate-200">Language</div>
                  <div className="text-xs text-slate-400">English (US)</div>
                </div>
              </div>
              <span className="text-xs text-cyan-400 font-medium">Change</span>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-950/50 border border-slate-800">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Bell className="w-5 h-5 text-amber-400" />
                <div>
                  <div className="text-sm font-medium text-slate-200">Notifications</div>
                  <div className="text-xs text-slate-400">Push alerts for updates</div>
                </div>
              </div>
              <div className="w-10 h-6 rounded-full bg-emerald-500/40 border border-emerald-500/60 relative">
                <div className="absolute right-1 top-1 w-4 h-4 rounded-full bg-emerald-400" />
              </div>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-950/50 border border-slate-800">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Shield className="w-5 h-5 text-emerald-400" />
                <div>
                  <div className="text-sm font-medium text-slate-200">Privacy</div>
                  <div className="text-xs text-slate-400">Data & security settings</div>
                </div>
              </div>
              <span className="text-xs text-cyan-400 font-medium">Manage</span>
            </div>
          </div>

          <div className="pt-2">
            <button className="w-full flex items-center justify-center gap-2 px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 hover:bg-red-500/20 text-sm font-medium transition-all">
              <Trash2 className="w-4 h-4" />
              Clear All Conversations
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
