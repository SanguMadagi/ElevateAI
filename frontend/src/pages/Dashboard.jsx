import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { profileService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { 
  Play, Bot, Brain, Folder, Code2,
  Sparkles, AlertTriangle, ChevronRight, Zap
} from 'lucide-react';
import toast from 'react-hot-toast';
import MockInterviewModal from '../components/MockInterviewModal';
import CareerWorkspaceModal from '../components/CareerWorkspaceModal';
import ComingSoonModal from '../components/ComingSoonModal';

const Dashboard = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);

  // Modal States
  const [interviewModalOpen, setInterviewModalOpen] = useState(false);
  const [workspaceModalOpen, setWorkspaceModalOpen] = useState(false);
  const [comingSoonModalOpen, setComingSoonModalOpen] = useState(false);

  const fetchData = async () => {
    try {
      const profileRes = await profileService.getProfile().catch(() => ({ data: null }));
      setProfile(profileRes.data);
    } catch (err) {
      console.error('Dashboard data error', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const isProfileComplete = () => {
    return profile && profile.name && profile.targetRole && profile.skills && profile.skills.length > 0;
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 text-white flex flex-col items-center justify-center">
        <Sparkles className="w-12 h-12 text-violet-500 animate-pulse mb-4" />
        <p className="text-slate-400 font-medium animate-pulse">Loading AI Career Dashboard...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-white py-10 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto space-y-12">
        
        {/* Profile Incomplete Banner */}
        {!isProfileComplete() && (
          <div className="bg-gradient-to-r from-amber-500/20 to-orange-500/20 border border-amber-500/30 rounded-3xl p-6 flex flex-col sm:flex-row items-center justify-between gap-4 shadow-xl">
            <div className="flex items-center gap-4 text-center sm:text-left">
              <div className="w-12 h-12 bg-amber-500/10 rounded-2xl flex items-center justify-center text-amber-500 shrink-0 mx-auto sm:mx-0">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-lg font-black text-amber-200">Complete your profile before taking assessments</h3>
                <p className="text-slate-400 text-sm mt-0.5">Add your target role, experience level, and upload your resume for accurate AI evaluation.</p>
              </div>
            </div>
            <Link 
              to="/profile" 
              className="px-6 py-3 bg-amber-500 hover:bg-amber-400 text-slate-950 rounded-2xl font-black text-sm transition-all whitespace-nowrap"
            >
              Go to Profile
            </Link>
          </div>
        )}

        {/* Dashboard Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 pb-6 border-b border-slate-900">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 bg-violet-600/10 border border-violet-500/20 text-violet-400 rounded-full text-xs font-bold uppercase tracking-wider mb-2">
              <Sparkles className="w-3.5 h-3.5" /> AI Career Preparation Platform
            </div>
            <h1 className="text-4xl font-black bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent">
              Welcome back, {user?.firstName || (user?.name ? user.name.split(' ')[0] : 'Candidate')}!
            </h1>
            <p className="text-slate-400 mt-1 font-medium text-lg">
              Targeting: <span className="text-violet-400 font-bold">{profile?.targetRole || 'Not set yet'}</span>
            </p>
          </div>
        </div>

        {/* ================================================== */}
        {/* 6 CORE FEATURES GRID — LOCKED VISION */}
        {/* 4 CORE CAREER FEATURES */}
        {/* ================================================== */}
        <div>
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-2xl font-black tracking-tight">Core Career Features</h2>
              <p className="text-slate-400 text-sm">Select an AI workflow to elevate your interview readiness</p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            
            {/* Feature 1: AI Assessments */}
            <div 
              onClick={() => isProfileComplete() ? navigate('/assessment/start') : navigate('/profile')}
              className="group bg-slate-900/60 border border-slate-850 hover:border-violet-500/50 rounded-3xl p-6 transition-all duration-300 hover:shadow-2xl hover:shadow-violet-600/10 cursor-pointer flex flex-col justify-between"
            >
              <div>
                <div className="w-14 h-14 bg-violet-600/10 border border-violet-500/20 rounded-2xl flex items-center justify-center text-violet-400 mb-5 group-hover:scale-110 transition-transform">
                  <Play className="w-7 h-7 fill-current" />
                </div>
                <h3 className="text-xl font-bold text-white mb-2 flex items-center gap-2">
                  🎯 AI Assessments
                </h3>
                <p className="text-slate-400 text-sm leading-relaxed">
                  Real-world scenario MCQs, technical questions, speech recognition, and proctoring UI with instant detailed evaluation.
                </p>
              </div>
              <div className="mt-6 flex items-center text-xs font-bold text-violet-400 group-hover:translate-x-1 transition-transform">
                Launch Assessment <ChevronRight className="w-4 h-4 ml-1" />
              </div>
            </div>

            {/* Feature 2: AI Mock Interview */}
            <div 
              onClick={() => setInterviewModalOpen(true)}
              className="group bg-slate-900/60 border border-slate-850 hover:border-fuchsia-500/50 rounded-3xl p-6 transition-all duration-300 hover:shadow-2xl hover:shadow-fuchsia-600/10 cursor-pointer flex flex-col justify-between"
            >
              <div>
                <div className="w-14 h-14 bg-fuchsia-600/10 border border-fuchsia-500/20 rounded-2xl flex items-center justify-center text-fuchsia-400 mb-5 group-hover:scale-110 transition-transform">
                  <Bot className="w-7 h-7" />
                </div>
                <h3 className="text-xl font-bold text-white mb-2 flex items-center gap-2">
                  🤖 AI Mock Interview
                </h3>
                <p className="text-slate-400 text-sm leading-relaxed">
                  Dynamic conversational mock interviewer asking tailored technical follow-up questions with memory persistence.
                </p>
              </div>
              <div className="mt-6 flex items-center text-xs font-bold text-fuchsia-400 group-hover:translate-x-1 transition-transform">
                Start Mock Interview <ChevronRight className="w-4 h-4 ml-1" />
              </div>
            </div>

            {/* Feature 3: Personal Career Agent */}
            <div 
              onClick={() => navigate('/career-agent')}
              className="group bg-slate-900/60 border border-slate-850 hover:border-indigo-500/50 rounded-3xl p-6 transition-all duration-300 hover:shadow-2xl hover:shadow-indigo-600/10 cursor-pointer flex flex-col justify-between"
            >
              <div>
                <div className="w-14 h-14 bg-indigo-600/10 border border-indigo-500/20 rounded-2xl flex items-center justify-center text-indigo-400 mb-5 group-hover:scale-110 transition-transform">
                  <Brain className="w-7 h-7" />
                </div>
                <h3 className="text-xl font-bold text-white mb-2 flex items-center gap-2">
                  🧠 Personal Career Agent
                </h3>
                <p className="text-slate-400 text-sm leading-relaxed">
                  Full-page conversational AI assistant inspecting your stored assessment results, resume, and skill scores.
                </p>
              </div>
              <div className="mt-6 flex items-center text-xs font-bold text-indigo-400 group-hover:translate-x-1 transition-transform">
                Open Career Chat <ChevronRight className="w-4 h-4 ml-1" />
              </div>
            </div>

          </div>
        </div>

        {/* AI CAREER WORKSPACE */}
        <div
          onClick={() => setWorkspaceModalOpen(true)}
          className="group bg-gradient-to-br from-slate-900 to-slate-950 border border-slate-850 hover:border-amber-500/50 rounded-3xl p-8 shadow-xl cursor-pointer transition-all duration-300"
        >
          <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
            <div className="flex items-start gap-4">
              <div className="w-14 h-14 bg-amber-600/10 border border-amber-500/20 rounded-2xl flex items-center justify-center text-amber-400 shrink-0">
                <Folder className="w-7 h-7" />
              </div>
              <div>
                <h2 className="text-2xl font-black text-white">AI Career Workspace</h2>
                <p className="text-slate-400 text-sm mt-1 max-w-2xl">
                  Compile your verified profile, assessment history, resume projects, and preparation notes into an isolated workspace.
                </p>
              </div>
            </div>
            <span className="text-sm font-bold text-amber-400 group-hover:translate-x-1 transition-transform whitespace-nowrap">
              Open Workspace <ChevronRight className="w-4 h-4 inline ml-1" />
            </span>
          </div>
        </div>

        {/* CODING LABS */}
        <div className="bg-gradient-to-br from-slate-900 to-slate-950 border border-slate-850 rounded-3xl p-8 shadow-xl relative overflow-hidden">
          <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6 relative z-10">
            <div>
              <div className="inline-flex items-center gap-2 px-3 py-1 bg-violet-600/10 border border-violet-500/20 text-violet-400 rounded-full text-xs font-bold uppercase tracking-wider mb-3">
                <Code2 className="w-3.5 h-3.5" /> Practical Labs
              </div>
              <h2 className="text-2xl font-black">💻 Coding Labs</h2>
              <p className="text-slate-400 text-sm mt-1 max-w-xl">
                DSA | SQL | Debugging | Backend coding practice environment
              </p>
            </div>

            <button
              onClick={() => setComingSoonModalOpen(true)}
              className="px-8 py-3.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-white font-bold rounded-2xl transition-all shadow-md cursor-pointer flex items-center gap-2"
            >
              <Zap className="w-4 h-4 text-violet-400" /> Open Coding Labs
            </button>
          </div>
        </div>

        {/* Modals */}
        <MockInterviewModal 
          isOpen={interviewModalOpen} 
          onClose={() => { setInterviewModalOpen(false); fetchData(); }} 
          onComplete={() => fetchData()} 
        />
        <CareerWorkspaceModal isOpen={workspaceModalOpen} onClose={() => setWorkspaceModalOpen(false)} />
        <ComingSoonModal isOpen={comingSoonModalOpen} onClose={() => setComingSoonModalOpen(false)} featureName="Coding Labs" />

      </div>
    </div>
  );
};

export default Dashboard;
