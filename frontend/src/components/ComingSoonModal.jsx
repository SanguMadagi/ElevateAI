import React from 'react';
import { X, Code2, Sparkles, CheckCircle2 } from 'lucide-react';

const ComingSoonModal = ({ isOpen, onClose, featureName = 'Coding Labs' }) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/85 backdrop-blur-md p-4 animate-in fade-in duration-200">
      <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 md:p-8 max-w-lg w-full flex flex-col shadow-2xl relative text-white">
        
        {/* Close button */}
        <button
          onClick={onClose}
          className="absolute top-5 right-5 p-2 text-slate-400 hover:text-white bg-slate-800/60 hover:bg-slate-800 rounded-xl transition-all cursor-pointer"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Content */}
        <div className="flex flex-col items-center text-center pt-2 pb-2">
          <div className="w-16 h-16 bg-violet-600/15 border border-violet-500/30 rounded-2xl flex items-center justify-center text-violet-400 mb-5 shadow-lg shadow-violet-500/10">
            <Code2 className="w-8 h-8" />
          </div>

          <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-violet-500/10 border border-violet-500/20 text-violet-400 rounded-full text-xs font-bold uppercase tracking-wider mb-3">
            <Sparkles className="w-3.5 h-3.5" /> In Development
          </div>

          <h2 className="text-2xl font-black text-white mb-2">
            {featureName} Coming Soon
          </h2>

          <p className="text-slate-400 text-sm leading-relaxed max-w-sm mb-6">
            We are currently building {featureName} for real-time DSA, SQL, Debugging, and Backend coding practice with interactive execution and AI review.
          </p>

          {/* Feature Highlights */}
          <div className="w-full bg-slate-950/60 border border-slate-800/80 rounded-2xl p-4 text-left space-y-2.5 mb-6 text-xs text-slate-300">
            <div className="flex items-center gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-violet-400 shrink-0" />
              <span>Interactive Code Editor with syntax highlighting</span>
            </div>
            <div className="flex items-center gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-violet-400 shrink-0" />
              <span>Multi-language support (Java, Python, C++, SQL)</span>
            </div>
            <div className="flex items-center gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-violet-400 shrink-0" />
              <span>Automated test runner and real-time AI code review</span>
            </div>
          </div>

          <button
            onClick={onClose}
            className="w-full py-3 bg-violet-600 hover:bg-violet-500 text-white font-bold rounded-xl transition-all shadow-lg shadow-violet-600/20 cursor-pointer"
          >
            Got It
          </button>
        </div>
      </div>
    </div>
  );
};

export default ComingSoonModal;

