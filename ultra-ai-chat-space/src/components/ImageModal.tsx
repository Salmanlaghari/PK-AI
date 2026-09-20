import { X, Download } from "lucide-react";

interface ImageModalProps {
  isOpen: boolean;
  onClose: () => void;
  imageUrl: string;
  alt?: string;
}

export default function ImageModal({ isOpen, onClose, imageUrl, alt }: ImageModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
      <div className="relative max-w-5xl max-h-[90vh] w-full flex items-center justify-center">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 rounded-xl bg-slate-900/80 text-slate-400 hover:text-white hover:bg-slate-800/80 border border-slate-700 transition-all z-10"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex flex-col items-center gap-3">
          <img
            src={imageUrl}
            alt={alt || "AI Generated Image"}
            className="max-w-full max-h-[80vh] rounded-2xl shadow-2xl object-contain"
          />
          <div className="flex gap-2">
            <a
              href={imageUrl}
              download
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900/80 text-slate-300 hover:text-white border border-slate-700 text-sm font-medium transition-all"
            >
              <Download className="w-4 h-4" />
              Download
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}
