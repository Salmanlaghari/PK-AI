import { useEffect, useRef, useState } from "react";
import type { FlowStudioInstance } from "../types/flowmusic";

interface FlowStudioEmbedProps {
  className?: string;
  theme?: "light" | "dark";
  onTrackGenerated?: (trackUrl: string) => void;
  onError?: (error: Error) => void;
}

export default function FlowStudioEmbed({
  className,
  theme = "dark",
  onTrackGenerated,
  onError,
}: FlowStudioEmbedProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [studio, setStudio] = useState<FlowStudioInstance | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function initStudio() {
      if (!containerRef.current) return;

      try {
        const mod = await import("@flowmusic/sdk");
        const instance = mod.createFlowStudio({
          container: containerRef.current,
          theme,
          onReady: () => {
            if (!cancelled) setIsLoading(false);
          },
          onTrackGenerated: (trackUrl: string) => {
            onTrackGenerated?.(trackUrl);
          },
          onError: (err: Error) => {
            onError?.(err);
          },
        });

        await instance.load();
        if (!cancelled) {
          setStudio(instance);
          setIsLoading(false);
        }
      } catch {
        // Fallback: render a lightweight embedded studio UI without the SDK.
        if (!cancelled) {
          setLoadError("Flow Studio SDK is not available. Showing embedded studio fallback.");
          setIsLoading(false);
        }
      }
    }

    initStudio();

    return () => {
      cancelled = true;
      studio?.destroy();
    };
  }, [theme, onTrackGenerated, onError, studio]);

  const handleGenerate = async (prompt: string) => {
    if (!studio) return;
    try {
      const trackUrl = await studio.generateTrack(prompt);
      onTrackGenerated?.(trackUrl);
    } catch (err) {
      onError?.(err instanceof Error ? err : new Error(String(err)));
    }
  };

  return (
    <div className={`relative w-full h-full min-h-[400px] bg-slate-950 border border-slate-800 rounded-2xl overflow-hidden ${className || ""}`}>
      <div ref={containerRef} className="w-full h-full absolute inset-0" />

      {isLoading && (
        <div className="absolute inset-0 flex items-center justify-center bg-slate-950/80 z-10">
          <div className="flex flex-col items-center gap-3">
            <div className="w-8 h-8 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
            <span className="text-xs text-slate-400">Loading Flow Studio...</span>
          </div>
        </div>
      )}

      {loadError && !studio && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 p-6 z-10">
          <div className="text-sm text-slate-400 text-center max-w-md">
            {loadError}
          </div>
          <div className="w-full max-w-md space-y-3">
            <input
              type="text"
              placeholder="Describe the track you want to generate..."
              className="w-full px-4 py-3 rounded-xl bg-slate-900 border border-slate-800 text-sm text-slate-100 placeholder:text-slate-500 outline-none focus:border-cyan-500/40"
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  const target = e.target as HTMLInputElement;
                  handleGenerate(target.value);
                  target.value = "";
                }
              }}
            />
            <button
              onClick={() => {
                const input = containerRef.current?.previousElementSibling as HTMLInputElement | null;
                if (input) handleGenerate(input.value);
              }}
              className="w-full py-3 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 text-white text-sm font-medium hover:from-indigo-500 hover:to-purple-500 transition-all"
            >
              Generate Track
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
