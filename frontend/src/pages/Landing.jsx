import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { 
  Brain, Sparkles, Bot, Folder, Play, Code2, 
  ChevronRight, ArrowRight, CheckCircle2, 
  FileText, Star, Zap
} from 'lucide-react';

const Landing = () => {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState('mock-interview');

  const featureTabs = [
    {
      id: 'mock-interview',
      label: 'AI Mock Interview',
      icon: Bot,
      badge: 'Conversational',
      title: 'Practice Dynamic, Conversational Technical Interviews',
      description: 'Engage in natural, multi-turn technical dialogues with an adaptive AI interviewer that listens, evaluates your reasoning, and asks challenging follow-up questions tailored to your responses.',
      highlights: [
        'Adaptive follow-ups based on the depth of your answers',
        'Turn-by-turn performance scoring and constructive guidance',
        'Comprehensive final evaluation report highlighting strengths and blind spots'
      ],
      preview: {
        type: 'interview',
        interviewer: 'Senior Staff Engineer',
        question: 'How would you mitigate cache stampede when 100,000 concurrent requests request an expired cache key?',
        answer: 'I would use a distributed mutex or single-flight mechanism combined with probabilistic early expiration (XFetch) so background workers refresh the cache before complete expiry.',
        feedback: 'Excellent depth. Correctly addressed locking and preemptive cache warming.',
        score: '94% Technical Depth'
      }
    },
    {
      id: 'assessments',
      label: 'AI Assessments',
      icon: Play,
      badge: 'Multi-Round',
      title: 'Personalized Multi-Round Technical Evaluations',
      description: 'Comprehensive assessments combining real-world architectural scenarios, role-specific MCQs, and deep technical probing designed around your target role and seniority level.',
      highlights: [
        'Real-world system design and architectural trade-off scenarios',
        'Voice input support for hands-free natural speaking practice',
        'Automatic objective scoring and category-by-category performance breakdowns'
      ],
      preview: {
        type: 'assessment',
        role: 'Senior Backend Engineer',
        question: 'Evaluate the trade-offs between Redis Sentinel vs Redis Cluster for a high-write fintech transaction ledger.',
        metrics: [
          { label: 'System Design', value: '92%' },
          { label: 'Data Consistency', value: '88%' },
          { label: 'Latency Awareness', value: '95%' }
        ],
        verdict: 'Ready for Staff/Senior technical evaluation'
      }
    },
    {
      id: 'career-agent',
      label: 'Personal Career Agent',
      icon: Brain,
      badge: '24/7 AI Mentor',
      title: 'An Intelligent Career Agent That Knows Your Background',
      description: 'Your personal AI mentor connects to your actual assessment scores, resume experience, and interview history to deliver personalized coaching, targeted study roadmaps, and honest feedback.',
      highlights: [
        'Answers questions based on your verified performance and resume',
        'Pinpoints exact knowledge gaps holding back your interview readiness',
        'Recommends customized practice topics and architectural concepts to review'
      ],
      preview: {
        type: 'agent',
        userQuery: 'What are my biggest blind spots for upcoming Senior Java interviews?',
        agentReply: 'Based on your last 3 assessment sessions, your core Java and database design are solid (92%+). However, you lost points on distributed transaction rollbacks and event-driven idempotency. I recommend reviewing the Saga pattern and outbox pattern.',
        action: 'Recommended 2 targeted scenario practices'
      }
    },
    {
      id: 'workspace',
      label: 'Career Workspace',
      icon: Folder,
      badge: 'Artifact Studio',
      title: 'Organized Career Artifacts & Custom Question Banks',
      description: 'Automatically compiles tailored Markdown preparation files, weakness trackers, and custom question banks based on your unique resume and practice history.',
      highlights: [
        'Resume architecture breakdowns and strength syntheses',
        'Targeted technical interview questions customized to your past projects',
        'Exportable preparation documents with built-in Markdown viewer'
      ],
      preview: {
        type: 'workspace',
        files: [
          { name: 'Resume-Analysis.md', tag: 'Architecture Summary', status: 'Generated' },
          { name: 'Weak-Topics.md', tag: 'Identified Gaps', status: 'Ready' },
          { name: 'Resume-Questions.md', tag: 'Custom Question Bank', status: 'Tailored' },
          { name: 'Skill-Progress.md', tag: 'Readiness Index: 88%', status: 'Updated' }
        ]
      }
    },
    {
      id: 'coding-labs',
      label: 'Coding Labs',
      icon: Code2,
      badge: 'In Development',
      title: 'Hands-On Coding & Algorithm Sandbox',
      description: 'An interactive in-browser development environment for practicing Data Structures, Algorithms, SQL queries, and backend bug fixing with automated evaluation and AI code reviews.',
      highlights: [
        'Multi-language code execution with syntax highlighting',
        'Curated problem tracks: Algorithms, SQL queries, and Bug Debugging',
        'Real-time automated test verification and intelligent feedback'
      ],
      preview: {
        type: 'coding',
        title: 'Interactive Code Execution Sandbox',
        status: 'Coming Soon',
        features: ['Algorithmic Challenges', 'SQL Query Optimizer', 'Backend Bug Fixing', 'AI Code Review']
      }
    }
  ];

  const currentTab = featureTabs.find(tab => tab.id === activeTab) || featureTabs[0];

  return (
    <div className="min-h-screen bg-slate-950 text-white selection:bg-violet-500 selection:text-white flex flex-col relative overflow-hidden">
      
      {/* Background Decorative Glows */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[1000px] h-[500px] bg-gradient-to-b from-violet-600/20 via-indigo-600/10 to-transparent rounded-full blur-3xl pointer-events-none -z-10" />
      <div className="absolute top-[35%] right-[-15%] w-[600px] h-[600px] bg-fuchsia-600/10 rounded-full blur-3xl pointer-events-none -z-10" />
      <div className="absolute bottom-[20%] left-[-15%] w-[600px] h-[600px] bg-violet-600/10 rounded-full blur-3xl pointer-events-none -z-10" />

      {/* Header / Navbar */}
      <nav className="sticky top-0 z-50 bg-slate-950/80 backdrop-blur-xl border-b border-slate-850/80 px-4 sm:px-8 py-4 transition-all">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          
          {/* Logo */}
          <Link to="/" className="flex items-center gap-3 group focus:outline-none">
            <div className="w-10 h-10 bg-gradient-to-tr from-violet-600 to-indigo-600 rounded-2xl flex items-center justify-center shadow-lg shadow-violet-600/25 transition-transform group-hover:scale-105 border border-violet-500/30">
              <Brain className="w-5 h-5 text-white" />
            </div>
            <div>
              <span className="text-xl font-black tracking-tight bg-gradient-to-r from-white via-slate-100 to-slate-300 bg-clip-text text-transparent">
                Elevate<span className="text-violet-400">AI</span>
              </span>
              <span className="hidden sm:block text-[10px] uppercase font-bold tracking-widest text-slate-400 -mt-1">
                Interview Platform
              </span>
            </div>
          </Link>

          {/* Nav Links */}
          <div className="hidden md:flex items-center gap-8 text-sm font-semibold text-slate-300">
            <a href="#features" className="hover:text-white transition-colors">Features</a>
            <a href="#preview" className="hover:text-white transition-colors">Interactive Demo</a>
            <a href="#workflow" className="hover:text-white transition-colors">How It Works</a>
            <a href="#reviews" className="hover:text-white transition-colors">Reviews</a>
          </div>

          {/* Auth Actions */}
          <div className="flex items-center gap-4">
            {user ? (
              <Link 
                to="/dashboard" 
                className="px-5 py-2.5 bg-violet-600 hover:bg-violet-500 text-white rounded-xl text-sm font-bold transition-all shadow-lg shadow-violet-600/25 flex items-center gap-2"
              >
                Go to Dashboard <ArrowRight className="w-4 h-4" />
              </Link>
            ) : (
              <>
                <Link 
                  to="/login" 
                  className="px-4 py-2 text-sm font-bold text-slate-300 hover:text-white transition-colors"
                >
                  Sign In
                </Link>
                <Link 
                  to="/signup" 
                  className="px-5 py-2.5 bg-violet-600 hover:bg-violet-500 text-white rounded-xl text-sm font-bold transition-all shadow-lg shadow-violet-600/25 flex items-center gap-1.5"
                >
                  Get Started <ChevronRight className="w-4 h-4" />
                </Link>
              </>
            )}
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="relative pt-24 pb-20 px-4 sm:px-6 lg:px-8 max-w-6xl mx-auto text-center flex flex-col items-center">
        
        <div className="inline-flex items-center gap-2 px-4 py-1.5 bg-violet-600/10 border border-violet-500/25 text-violet-300 rounded-full text-xs font-bold uppercase tracking-wider mb-6 shadow-sm">
          <Zap className="w-3.5 h-3.5 text-violet-400" /> The New Standard in Technical Interview Prep
        </div>

        <h1 className="text-4xl sm:text-6xl lg:text-7xl font-black tracking-tight leading-[1.1] mb-6">
          Master Every <br />
          <span className="bg-gradient-to-r from-violet-400 via-fuchsia-300 to-indigo-400 bg-clip-text text-transparent">
            Technical Interview
          </span>
        </h1>

        <p className="max-w-3xl text-slate-400 text-base sm:text-lg lg:text-xl font-medium leading-relaxed mb-10">
          Stop guessing how you will perform. Practice real-time conversational interviews, tackle adaptive architectural scenarios, and eliminate technical blind spots with AI designed for engineering excellence.
        </p>

        {/* Hero CTA Group */}
        <div className="flex flex-col sm:flex-row items-center gap-4 w-full sm:w-auto justify-center mb-16">
          <Link 
            to={user ? "/dashboard" : "/signup"}
            className="w-full sm:w-auto px-8 py-4 bg-gradient-to-r from-violet-600 via-indigo-600 to-fuchsia-600 hover:from-violet-500 hover:to-indigo-500 text-white rounded-2xl font-black text-base transition-all shadow-xl shadow-violet-600/30 hover:scale-105 flex items-center justify-center gap-2 cursor-pointer"
          >
            Start Free Assessment <ArrowRight className="w-5 h-5" />
          </Link>
          <a 
            href="#preview"
            className="w-full sm:w-auto px-8 py-4 bg-slate-900/90 hover:bg-slate-850 border border-slate-800 text-slate-200 rounded-2xl font-bold text-base transition-all hover:border-slate-700 flex items-center justify-center gap-2 cursor-pointer"
          >
            See Live Demo <Sparkles className="w-4 h-4 text-violet-400" />
          </a>
        </div>

        {/* Hero Live Interface Simulation Card */}
        <div className="w-full max-w-4xl mx-auto bg-slate-900/90 border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-2xl shadow-violet-950/40 relative text-left">
          
          {/* Mock Browser/Window Header */}
          <div className="flex items-center justify-between pb-5 mb-6 border-b border-slate-800">
            <div className="flex items-center gap-3">
              <div className="flex gap-1.5">
                <div className="w-3 h-3 rounded-full bg-rose-500/80" />
                <div className="w-3 h-3 rounded-full bg-amber-500/80" />
                <div className="w-3 h-3 rounded-full bg-emerald-500/80" />
              </div>
              <span className="text-xs text-slate-400 font-mono hidden sm:inline">elevate-ai // live-interview-session</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-ping" />
              <span className="text-xs font-bold text-emerald-400">AI Interviewer Active</span>
            </div>
          </div>

          {/* Dialogue Simulator */}
          <div className="space-y-4">
            {/* AI Turn */}
            <div className="flex items-start gap-3.5">
              <div className="w-9 h-9 rounded-xl bg-violet-600/20 border border-violet-500/30 flex items-center justify-center text-violet-400 shrink-0">
                <Bot className="w-5 h-5" />
              </div>
              <div className="bg-slate-950/70 border border-slate-800/90 rounded-2xl rounded-tl-none p-4 max-w-2xl">
                <p className="text-xs text-violet-400 font-bold mb-1">AI Senior Technical Interviewer</p>
                <p className="text-sm text-slate-200 leading-relaxed">
                  "I noticed your resume highlights a distributed microservices platform. How did you handle distributed transaction rollback across services without creating tight database coupling?"
                </p>
              </div>
            </div>

            {/* Candidate Turn */}
            <div className="flex items-start gap-3.5 justify-end">
              <div className="bg-gradient-to-r from-violet-600/15 to-indigo-600/15 border border-violet-500/30 rounded-2xl rounded-tr-none p-4 max-w-2xl text-right">
                <p className="text-xs text-slate-400 font-bold mb-1">Your Live Response</p>
                <p className="text-sm text-slate-200 leading-relaxed">
                  "We implemented an asynchronous Saga pattern with an event-driven orchestrator. Each service published compensating transactions to undo local state upon upstream failure, avoiding two-phase commit overhead."
                </p>
              </div>
              <div className="w-9 h-9 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shrink-0">
                <Brain className="w-5 h-5" />
              </div>
            </div>

            {/* AI Evaluation Pill */}
            <div className="bg-emerald-500/10 border border-emerald-500/25 rounded-2xl p-3.5 flex items-center justify-between text-xs text-emerald-300">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                <span><strong>Instant Evaluation:</strong> Strong architectural clarity. Accurately balanced 2PC vs Saga trade-offs.</span>
              </div>
              <span className="font-black px-2.5 py-1 bg-emerald-500/20 rounded-lg shrink-0 ml-2">Score: 95%</span>
            </div>
          </div>
        </div>

      </section>

      {/* Trust & Metrics Ribbon */}
      <section className="border-y border-slate-900 bg-slate-950/60 py-10 px-4 sm:px-6 lg:px-8">
        <div className="max-w-7xl mx-auto grid grid-cols-2 md:grid-cols-4 gap-6 text-center">
          <div>
            <p className="text-3xl sm:text-4xl font-black bg-gradient-to-r from-violet-400 to-indigo-400 bg-clip-text text-transparent">94%</p>
            <p className="text-xs sm:text-sm text-slate-400 font-medium mt-1">Higher Candidate Confidence</p>
          </div>
          <div>
            <p className="text-3xl sm:text-4xl font-black bg-gradient-to-r from-fuchsia-400 to-pink-400 bg-clip-text text-transparent">Adaptive</p>
            <p className="text-xs sm:text-sm text-slate-400 font-medium mt-1">Real-time Technical Probing</p>
          </div>
          <div>
            <p className="text-3xl sm:text-4xl font-black bg-gradient-to-r from-indigo-400 to-cyan-400 bg-clip-text text-transparent">100%</p>
            <p className="text-xs sm:text-sm text-slate-400 font-medium mt-1">Tailored to Target Role</p>
          </div>
          <div>
            <p className="text-3xl sm:text-4xl font-black bg-gradient-to-r from-amber-400 to-orange-400 bg-clip-text text-transparent">Instant</p>
            <p className="text-xs sm:text-sm text-slate-400 font-medium mt-1">Multi-Criteria Scorecards</p>
          </div>
        </div>
      </section>

      {/* Interactive Feature Demo Showcase */}
      <section id="features" className="py-24 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto w-full">
        <div className="text-center max-w-3xl mx-auto mb-14">
          <div className="inline-flex items-center gap-1.5 px-3.5 py-1 bg-violet-600/10 border border-violet-500/20 text-violet-400 rounded-full text-xs font-bold uppercase tracking-wider mb-3">
            <Sparkles className="w-3.5 h-3.5" /> Interactive Platform Walkthrough
          </div>
          <h2 className="text-3xl sm:text-5xl font-black tracking-tight mb-4">
            Everything You Need To Get Hired
          </h2>
          <p className="text-slate-400 text-base sm:text-lg">
            Switch between the core modules below to see how each feature accelerates your preparation.
          </p>
        </div>

        {/* Feature Tab Selector */}
        <div className="flex flex-wrap items-center justify-center gap-2 mb-10">
          {featureTabs.map((tab) => {
            const Icon = tab.icon;
            const isSelected = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center gap-2.5 px-5 py-3 rounded-2xl text-xs sm:text-sm font-bold transition-all cursor-pointer ${
                  isSelected 
                    ? 'bg-violet-600 text-white shadow-lg shadow-violet-600/25 scale-105' 
                    : 'bg-slate-900/80 hover:bg-slate-900 text-slate-400 hover:text-slate-200 border border-slate-850'
                }`}
              >
                <Icon className="w-4 h-4" />
                <span>{tab.label}</span>
                {tab.badge && (
                  <span className={`text-[10px] px-2 py-0.5 rounded-full uppercase font-black tracking-wider ${
                    isSelected ? 'bg-white/20 text-white' : 'bg-slate-800 text-slate-400'
                  }`}>
                    {tab.badge}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* Active Feature Spotlight Box */}
        <div id="preview" className="bg-slate-900/70 border border-slate-800 rounded-3xl p-8 sm:p-12 shadow-2xl grid grid-cols-1 lg:grid-cols-12 gap-10 items-center">
          
          {/* Left Details */}
          <div className="lg:col-span-6 space-y-6">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-violet-600/20 to-indigo-600/20 border border-violet-500/30 flex items-center justify-center text-violet-400 shadow-md">
              <currentTab.icon className="w-7 h-7" />
            </div>
            <h3 className="text-2xl sm:text-3xl font-black text-white leading-snug">
              {currentTab.title}
            </h3>
            <p className="text-slate-400 text-sm sm:text-base leading-relaxed">
              {currentTab.description}
            </p>
            <ul className="space-y-3 pt-2">
              {currentTab.highlights.map((item, idx) => (
                <li key={idx} className="flex items-start gap-3 text-xs sm:text-sm text-slate-300">
                  <CheckCircle2 className="w-4 h-4 text-violet-400 shrink-0 mt-0.5" />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
            <div className="pt-4">
              <Link 
                to={user ? "/dashboard" : "/signup"}
                className="inline-flex items-center gap-2 text-sm font-bold text-violet-400 hover:text-violet-300 transition-colors group"
              >
                Experience {currentTab.label} in action <ChevronRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </Link>
            </div>
          </div>

          {/* Right Live Simulation Preview */}
          <div className="lg:col-span-6">
            <div className="bg-slate-950 border border-slate-800 rounded-2xl p-6 shadow-xl relative overflow-hidden">
              
              {/* Preview 1: Mock Interview */}
              {currentTab.preview.type === 'interview' && (
                <div className="space-y-4 text-xs">
                  <div className="flex items-center justify-between pb-3 border-b border-slate-850">
                    <span className="font-bold text-slate-300">Mock Session: {currentTab.preview.interviewer}</span>
                    <span className="px-2 py-0.5 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded-md font-bold">Turn 2 of 4</span>
                  </div>
                  <div className="bg-slate-900/80 p-3.5 rounded-xl border border-slate-850">
                    <p className="text-violet-400 font-bold mb-1">Question</p>
                    <p className="text-slate-300">{currentTab.preview.question}</p>
                  </div>
                  <div className="bg-violet-950/30 p-3.5 rounded-xl border border-violet-900/40">
                    <p className="text-slate-400 font-bold mb-1">Candidate Answer</p>
                    <p className="text-slate-200">{currentTab.preview.answer}</p>
                  </div>
                  <div className="p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-xl flex items-center justify-between text-emerald-300">
                    <span>{currentTab.preview.feedback}</span>
                    <span className="font-bold ml-2">{currentTab.preview.score}</span>
                  </div>
                </div>
              )}

              {/* Preview 2: Assessments */}
              {currentTab.preview.type === 'assessment' && (
                <div className="space-y-4 text-xs">
                  <div className="flex items-center justify-between pb-3 border-b border-slate-850">
                    <span className="font-bold text-slate-300">Role: {currentTab.preview.role}</span>
                    <span className="px-2 py-0.5 bg-violet-500/10 text-violet-400 border border-violet-500/20 rounded-md font-bold">Scenario Question</span>
                  </div>
                  <p className="text-slate-300 bg-slate-900/80 p-3.5 rounded-xl border border-slate-850">
                    {currentTab.preview.question}
                  </p>
                  <div className="grid grid-cols-3 gap-2">
                    {currentTab.preview.metrics.map((m, i) => (
                      <div key={i} className="bg-slate-900/90 p-2.5 rounded-xl border border-slate-850 text-center">
                        <p className="text-[10px] text-slate-400 uppercase font-semibold">{m.label}</p>
                        <p className="text-sm font-black text-violet-400 mt-0.5">{m.value}</p>
                      </div>
                    ))}
                  </div>
                  <div className="p-3 bg-violet-600/10 border border-violet-500/20 rounded-xl text-violet-300 font-bold text-center">
                    {currentTab.preview.verdict}
                  </div>
                </div>
              )}

              {/* Preview 3: Career Agent */}
              {currentTab.preview.type === 'agent' && (
                <div className="space-y-4 text-xs">
                  <div className="flex items-center justify-between pb-3 border-b border-slate-850">
                    <span className="font-bold text-slate-300">Personal Career Agent</span>
                    <span className="px-2 py-0.5 bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 rounded-md font-bold">Grounded Insights</span>
                  </div>
                  <div className="bg-slate-900/80 p-3.5 rounded-xl border border-slate-850">
                    <p className="text-indigo-400 font-bold mb-1">Your Question</p>
                    <p className="text-slate-300">"{currentTab.preview.userQuery}"</p>
                  </div>
                  <div className="bg-indigo-950/30 p-3.5 rounded-xl border border-indigo-900/40">
                    <p className="text-slate-400 font-bold mb-1">Agent Analysis</p>
                    <p className="text-slate-200 leading-relaxed">{currentTab.preview.agentReply}</p>
                  </div>
                  <div className="p-2.5 bg-indigo-500/10 border border-indigo-500/20 rounded-xl text-indigo-300 text-center font-bold">
                    {currentTab.preview.action}
                  </div>
                </div>
              )}

              {/* Preview 4: Workspace */}
              {currentTab.preview.type === 'workspace' && (
                <div className="space-y-3 text-xs">
                  <div className="flex items-center justify-between pb-3 border-b border-slate-850">
                    <span className="font-bold text-slate-300">Generated Career Workspace</span>
                    <span className="px-2 py-0.5 bg-amber-500/10 text-amber-400 border border-amber-500/20 rounded-md font-bold">Artifacts Ready</span>
                  </div>
                  {currentTab.preview.files.map((file, i) => (
                    <div key={i} className="flex items-center justify-between p-3 bg-slate-900/80 border border-slate-850 rounded-xl">
                      <div className="flex items-center gap-2">
                        <FileText className="w-4 h-4 text-amber-400" />
                        <div>
                          <p className="font-bold text-white">{file.name}</p>
                          <p className="text-[10px] text-slate-400">{file.tag}</p>
                        </div>
                      </div>
                      <span className="px-2 py-0.5 bg-amber-500/10 text-amber-300 text-[10px] font-bold rounded">
                        {file.status}
                      </span>
                    </div>
                  ))}
                </div>
              )}

              {/* Preview 5: Coding Labs */}
              {currentTab.preview.type === 'coding' && (
                <div className="space-y-4 text-xs text-center py-4">
                  <div className="w-12 h-12 bg-violet-600/20 rounded-2xl flex items-center justify-center text-violet-400 mx-auto mb-2">
                    <Code2 className="w-6 h-6" />
                  </div>
                  <h4 className="text-base font-black text-white">{currentTab.preview.title}</h4>
                  <p className="text-slate-400 max-w-sm mx-auto">
                    Real-time DSA, SQL, Bug Debugging, and multi-language compiler with automated tests.
                  </p>
                  <div className="grid grid-cols-2 gap-2 text-left pt-2">
                    {currentTab.preview.features.map((f, i) => (
                      <div key={i} className="p-2.5 bg-slate-900/80 border border-slate-850 rounded-xl flex items-center gap-2 text-slate-300">
                        <CheckCircle2 className="w-3.5 h-3.5 text-violet-400 shrink-0" />
                        <span>{f}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

            </div>
          </div>

        </div>
      </section>

      {/* How It Works 3-Step Workflow */}
      <section id="workflow" className="py-20 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto w-full border-t border-slate-900">
        <div className="text-center max-w-2xl mx-auto mb-16">
          <h2 className="text-3xl sm:text-4xl font-black mb-3">
            Three Steps to Interview Readiness
          </h2>
          <p className="text-slate-400 text-base">
            From uploading your resume to mastering tough technical questions.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          
          <div className="bg-slate-900/40 border border-slate-850/80 rounded-3xl p-8 text-left relative group hover:border-violet-500/40 transition-colors">
            <span className="text-4xl font-black text-slate-800 group-hover:text-violet-500/30 transition-colors">01</span>
            <h4 className="text-lg font-bold text-white mt-4 mb-2">Upload Resume & Target Role</h4>
            <p className="text-slate-400 text-sm leading-relaxed">
              We extract your technical stack, seniority, and primary projects to calibrate question difficulty to your career aspirations.
            </p>
          </div>

          <div className="bg-slate-900/40 border border-slate-850/80 rounded-3xl p-8 text-left relative group hover:border-fuchsia-500/40 transition-colors">
            <span className="text-4xl font-black text-slate-800 group-hover:text-fuchsia-500/30 transition-colors">02</span>
            <h4 className="text-lg font-bold text-white mt-4 mb-2">Practice Realistic Interviews</h4>
            <p className="text-slate-400 text-sm leading-relaxed">
              Engage in conversational technical interviews and timed scenario evaluations with dynamic follow-ups that challenge your architecture choices.
            </p>
          </div>

          <div className="bg-slate-900/40 border border-slate-850/80 rounded-3xl p-8 text-left relative group hover:border-indigo-500/40 transition-colors">
            <span className="text-4xl font-black text-slate-800 group-hover:text-indigo-500/30 transition-colors">03</span>
            <h4 className="text-lg font-bold text-white mt-4 mb-2">Target Blind Spots & Artifacts</h4>
            <p className="text-slate-400 text-sm leading-relaxed">
              Consult your personal career assistant for tailored study plans and download compiled markdown preparation cheat sheets.
            </p>
          </div>

        </div>
      </section>

      {/* Candidate Testimonials */}
      <section id="reviews" className="py-20 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto w-full border-t border-slate-900">
        <div className="text-center max-w-2xl mx-auto mb-16">
          <h2 className="text-3xl sm:text-4xl font-black mb-3">
            What Candidates Say
          </h2>
          <p className="text-slate-400 text-base">
            Engineers using ElevateAI to prepare for top technical interviews.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-slate-900/50 border border-slate-850 rounded-2xl p-6 text-left">
            <div className="flex gap-1 text-amber-400 mb-4">
              {[...Array(5)].map((_, i) => <Star key={i} className="w-4 h-4 fill-current" />)}
            </div>
            <p className="text-slate-300 text-sm leading-relaxed mb-6">
              "The conversational follow-up questions felt exactly like my Principal Engineer interview at a tier-1 company. It caught gaps in my caching strategy that I would have missed."
            </p>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-violet-600/20 border border-violet-500/30 flex items-center justify-center text-violet-400 font-bold text-sm">
                AR
              </div>
              <div>
                <p className="text-sm font-bold text-white">Ananya R.</p>
                <p className="text-xs text-slate-400">Senior Backend Engineer</p>
              </div>
            </div>
          </div>

          <div className="bg-slate-900/50 border border-slate-850 rounded-2xl p-6 text-left">
            <div className="flex gap-1 text-amber-400 mb-4">
              {[...Array(5)].map((_, i) => <Star key={i} className="w-4 h-4 fill-current" />)}
            </div>
            <p className="text-slate-300 text-sm leading-relaxed mb-6">
              "Instead of generic questions, the mock interviewer grilled me specifically on my own resume projects. The instant scoring and feedback scorecard made preparation 10x faster."
            </p>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-fuchsia-600/20 border border-fuchsia-500/30 flex items-center justify-center text-fuchsia-400 font-bold text-sm">
                DK
              </div>
              <div>
                <p className="text-sm font-bold text-white">David K.</p>
                <p className="text-xs text-slate-400">Full Stack Architect</p>
              </div>
            </div>
          </div>

          <div className="bg-slate-900/50 border border-slate-850 rounded-2xl p-6 text-left">
            <div className="flex gap-1 text-amber-400 mb-4">
              {[...Array(5)].map((_, i) => <Star key={i} className="w-4 h-4 fill-current" />)}
            </div>
            <p className="text-slate-300 text-sm leading-relaxed mb-6">
              "The Career Workspace compiled custom question banks and weakness notes tailored to me. Having the Personal Career Agent analyze my test results gave me clear study directions."
            </p>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 font-bold text-sm">
                SM
              </div>
              <div>
                <p className="text-sm font-bold text-white">Siddharth M.</p>
                <p className="text-xs text-slate-400">Software Engineer II</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* High-Impact Bottom CTA */}
      <section className="py-20 px-4 sm:px-6 lg:px-8 max-w-5xl mx-auto w-full text-center">
        <div className="bg-gradient-to-r from-violet-600/20 via-indigo-600/20 to-fuchsia-600/20 border border-violet-500/30 rounded-3xl p-10 sm:p-14 shadow-2xl relative overflow-hidden">
          <h2 className="text-3xl sm:text-5xl font-black mb-4 tracking-tight">
            Stop Guessing. <br />
            Start Interviewing Like a Pro.
          </h2>
          <p className="text-slate-300 text-sm sm:text-base max-w-xl mx-auto mb-8 leading-relaxed">
            Join engineers mastering high-stakes technical interviews with tailored AI simulations and objective feedback.
          </p>
          <Link 
            to={user ? "/dashboard" : "/signup"}
            className="inline-flex items-center gap-2 px-8 py-4 bg-gradient-to-r from-violet-600 to-indigo-600 hover:from-violet-500 hover:to-indigo-500 text-white font-black rounded-2xl transition-all shadow-xl shadow-violet-600/30 hover:scale-105"
          >
            Start Free Assessment <ArrowRight className="w-5 h-5" />
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="mt-auto border-t border-slate-900 py-8 px-4 sm:px-8 bg-slate-950/90 text-center text-xs text-slate-400">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 bg-gradient-to-tr from-violet-600 to-indigo-600 rounded-lg flex items-center justify-center border border-violet-500/30">
              <Brain className="w-4 h-4 text-white" />
            </div>
            <span className="font-bold text-slate-300">
              Elevate<span className="text-violet-400">AI</span> Platform
            </span>
          </div>
          <p>&copy; 2026 ElevateAI — AI Interview & Career Preparation Platform. All rights reserved.</p>
        </div>
      </footer>

    </div>
  );
};

export default Landing;
