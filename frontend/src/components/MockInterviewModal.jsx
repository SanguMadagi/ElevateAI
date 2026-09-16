import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { interviewService } from '../services/api';
import { 
  X, Bot, Sparkles, Video, Mic, Volume2, ArrowRight, 
  CheckCircle2, RefreshCw, Zap
} from 'lucide-react';
import toast from 'react-hot-toast';

const PRESET_TOPICS = [
  'Java Backend Engineering',
  'Spring Boot & Microservices',
  'System Design & Architecture',
  'SQL & Database Optimization',
  'Full Stack Web Development',
  'Data Structures & Algorithms'
];

const MockInterviewModal = ({ isOpen, onClose, onComplete }) => {
  const navigate = useNavigate();
  const [topic, setTopic] = useState('Java Backend Engineering');
  const [customTopic, setCustomTopic] = useState('');
  const [loading, setLoading] = useState(false);
  const [speechSupported, setSpeechSupported] = useState(true);
  const [recognitionSupported, setRecognitionSupported] = useState(true);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const hasSpeech = 'speechSynthesis' in window;
      const hasRec = 'SpeechRecognition' in window || 'webkitSpeechRecognition' in window;
      setSpeechSupported(hasSpeech);
      setRecognitionSupported(hasRec);
    }
  }, []);

  if (!isOpen) return null;

  const handleLaunchRoom = async () => {
    const finalTopic = customTopic.trim() ? customTopic.trim() : topic;
    setLoading(true);
    try {
      const res = await interviewService.startInterview(finalTopic);
      toast.success('Interview Room ready! Connecting AI Interviewer...');
      if (onComplete) onComplete(res.data);
      onClose();
      navigate('/interview/room/' + res.data.id);
    } catch (err) {
      console.error('Failed to start interview', err);
      const message = typeof err.response?.data === 'string'
        ? err.response.data
        : err.response?.data?.error || 'Unable to launch interview room. Please try again.';
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/85 backdrop-blur-md p-4 animate-in fade-in duration-200">
      <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 md:p-8 max-w-2xl w-full flex flex-col shadow-2xl relative text-white">
        
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-6">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 bg-gradient-to-br from-fuchsia-600 to-indigo-600 rounded-2xl flex items-center justify-center text-white shadow-lg shadow-fuchsia-500/20">
              <Bot className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-xl font-black text-white">AI Bot Interview Studio</h3>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-extrabold uppercase tracking-wider bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  100% Free
                </span>
              </div>
              <p className="text-xs text-slate-400">Live Voice Bot • Audio Orb Visualizer • Adaptive Technical Questions</p>
            </div>
          </div>
          <button 
            onClick={onClose} 
            aria-label="Close modal"
            className="text-slate-400 hover:text-white p-2 rounded-xl hover:bg-slate-800 transition-all cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body Content */}
        <div className="space-y-6">
          
          {/* Topic Selection */}
          <div className="space-y-3">
            <label className="text-xs font-bold uppercase tracking-wider text-slate-300 flex items-center gap-1.5">
              <Sparkles className="w-3.5 h-3.5 text-fuchsia-400" /> Select Interview Domain
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
              {PRESET_TOPICS.map((t) => {
                const isSelected = topic === t && !customTopic.trim();
                return (
                  <button
                    key={t}
                    type="button"
                    onClick={() => { setTopic(t); setCustomTopic(''); }}
                    className={`p-3 rounded-2xl text-left text-xs font-semibold transition-all border cursor-pointer ${
                      isSelected 
                        ? 'bg-fuchsia-600/15 border-fuchsia-500 text-white shadow-sm shadow-fuchsia-500/20' 
                        : 'bg-slate-950/60 border-slate-800 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                    }`}
                  >
                    {t}
                  </button>
                );
              })}
            </div>
            <input
              type="text"
              value={customTopic}
              onChange={(e) => setCustomTopic(e.target.value)}
              placeholder="Or enter custom role/topic (e.g. Cloud DevOps, Distributed Systems)..."
              className="w-full px-4 py-2.5 bg-slate-950 border border-slate-800 rounded-2xl text-xs text-white placeholder-slate-500 focus:border-fuchsia-500 focus:outline-none transition-all"
            />
          </div>

          {/* Features / Pre-flight Checks Grid */}
          <div className="grid grid-cols-3 gap-2.5 bg-slate-950/80 p-4 rounded-2xl border border-slate-800/80">
            <div className="flex items-start gap-2.5">
              <div className="w-7 h-7 rounded-xl bg-violet-500/10 border border-violet-500/20 flex items-center justify-center text-violet-400 shrink-0 mt-0.5">
                <Volume2 className="w-3.5 h-3.5" />
              </div>
              <div>
                <p className="text-[11px] font-bold text-slate-200">AI Voice (TTS)</p>
                <p className="text-[10px] text-slate-400">
                  {speechSupported ? 'Native Web Voice' : 'Text-only Mode'}
                </p>
              </div>
            </div>

            <div className="flex items-start gap-2.5">
              <div className="w-7 h-7 rounded-xl bg-fuchsia-500/10 border border-fuchsia-500/20 flex items-center justify-center text-fuchsia-400 shrink-0 mt-0.5">
                <Mic className="w-3.5 h-3.5" />
              </div>
              <div>
                <p className="text-[11px] font-bold text-slate-200">Voice Recognition</p>
                <p className="text-[10px] text-slate-400">
                  {recognitionSupported ? 'Browser Dictation' : 'Keyboard Typing'}
                </p>
              </div>
            </div>

            <div className="flex items-start gap-2.5">
              <div className="w-7 h-7 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 shrink-0 mt-0.5">
                <Video className="w-3.5 h-3.5" />
              </div>
              <div>
                <p className="text-[11px] font-bold text-slate-200">Webcam Stream</p>
                <p className="text-[10px] text-slate-400">Client-side Only</p>
              </div>
            </div>
          </div>

          {/* Highlights */}
          <div className="space-y-2 text-xs text-slate-400">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              <span>4 dynamic rounds: core theory, architectural trade-offs, edge-cases, and practical scenario.</span>
            </div>
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              <span>Full interview report updates your Career Dashboard readiness and strengths immediately.</span>
            </div>
          </div>

          {/* Launch Button */}
          <div className="pt-2">
            <button
              onClick={handleLaunchRoom}
              disabled={loading}
              className="w-full py-4 bg-gradient-to-r from-fuchsia-600 via-indigo-600 to-violet-600 hover:opacity-95 text-white font-extrabold rounded-2xl transition-all shadow-xl shadow-fuchsia-600/25 flex items-center justify-center gap-2.5 cursor-pointer disabled:opacity-50 text-sm"
            >
              {loading ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" />
                  Connecting AI Interviewer...
                </>
              ) : (
                <>
                  <Zap className="w-4 h-4 fill-white" />
                  Enter Live AI Interview Room
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </div>

        </div>

      </div>
    </div>
  );
};

export default MockInterviewModal;
