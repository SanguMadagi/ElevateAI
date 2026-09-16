import { useState, useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { testService } from '../services/api';
import { 
  Sparkles, 
  Brain, 
  Award, 
  CheckCircle2, 
  XCircle, 
  BookOpen, 
  AlertTriangle, 
  Loader2, 
  RefreshCw, 
  Check, 
  Clock, 
  ShieldCheck, 
  Layers, 
  ChevronDown, 
  ChevronUp, 
  Lightbulb, 
  Star 
} from 'lucide-react';
import toast from 'react-hot-toast';

const STAGES = [
  { id: 0, label: 'Assessment submitted' },
  { id: 1, label: 'Answers received and verified' },
  { id: 2, label: 'Evaluating your technical responses' },
  { id: 3, label: 'Calculating technical scores' },
  { id: 4, label: 'Preparing personalized recommendations' },
];

const ResultPage = () => {
  const { testId } = useParams();
  const [result, setResult] = useState(null);
  const [evalStatus, setEvalStatus] = useState('PROCESSING'); // PROCESSING | COMPLETED | FAILED
  const [errorMessage, setErrorMessage] = useState('');
  const [networkWarning, setNetworkWarning] = useState('');
  const [isRetrying, setIsRetrying] = useState(false);
  const [currentStageIndex, setCurrentStageIndex] = useState(2); // 0..4
  const [isTransitioningComplete, setIsTransitioningComplete] = useState(false);

  // Accordion Section Visibility
  const [openSections, setOpenSections] = useState({
    mcq: true,
    scenario: true,
    project: true,
    category: true,
  });

  const toggleSection = (section) => {
    setOpenSections((prev) => ({ ...prev, [section]: !prev[section] }));
  };

  const consecutiveErrorsRef = useRef(0);
  const isMountedRef = useRef(true);

  // Progressive presentation stages during AI evaluation
  useEffect(() => {
    if (evalStatus !== 'PROCESSING' || isTransitioningComplete) return;

    const timer = setInterval(() => {
      setCurrentStageIndex((prev) => {
        if (prev < STAGES.length - 1) return prev + 1;
        return prev;
      });
    }, 2800);

    return () => clearInterval(timer);
  }, [evalStatus, isTransitioningComplete]);

  // Status Polling Effect
  useEffect(() => {
    isMountedRef.current = true;
    let pollIntervalId = null;

    const checkEvaluationStatus = async () => {
      try {
        const statusRes = await testService.getEvaluationStatus(testId);
        if (!isMountedRef.current) return;

        consecutiveErrorsRef.current = 0;
        setNetworkWarning('');

        const statusData = statusRes?.data || {};
        const currentStatus = statusData.status?.toUpperCase();

        if (currentStatus === 'COMPLETED') {
          setIsTransitioningComplete(true);
          setCurrentStageIndex(STAGES.length);

          try {
            const resultRes = await testService.getResult(testId);
            if (!isMountedRef.current) return;

            if (resultRes?.data) {
              setResult(resultRes.data);
              setTimeout(() => {
                if (isMountedRef.current) {
                  setEvalStatus('COMPLETED');
                }
              }, 800);
              if (pollIntervalId) clearInterval(pollIntervalId);
            }
          } catch (resErr) {
            console.warn('Result fetch error after completed status:', resErr);
          }
        } else if (currentStatus === 'FAILED') {
          setEvalStatus('FAILED');
          setErrorMessage('We encountered a temporary problem while generating your evaluation.');
          if (pollIntervalId) clearInterval(pollIntervalId);
        } else {
          setEvalStatus('PROCESSING');
        }
      } catch (err) {
        if (!isMountedRef.current) return;

        try {
          const directResult = await testService.getResult(testId);
          if (directResult?.data && isMountedRef.current) {
            setResult(directResult.data);
            setIsTransitioningComplete(true);
            setTimeout(() => {
              if (isMountedRef.current) {
                setEvalStatus('COMPLETED');
              }
            }, 600);
            if (pollIntervalId) clearInterval(pollIntervalId);
            return;
          }
        } catch (resErr) {
          // Expected while still evaluating
        }

        consecutiveErrorsRef.current += 1;
        if (consecutiveErrorsRef.current >= 4) {
          setNetworkWarning('Connection is slower than usual. Still checking for your evaluation...');
        }
      }
    };

    checkEvaluationStatus();
    pollIntervalId = setInterval(checkEvaluationStatus, 2000);

    return () => {
      isMountedRef.current = false;
      if (pollIntervalId) clearInterval(pollIntervalId);
    };
  }, [testId]);

  // Handle Retry Evaluation
  const handleRetry = async () => {
    setIsRetrying(true);
    setNetworkWarning('');
    setErrorMessage('');
    try {
      await testService.submitTest(testId);
      setEvalStatus('PROCESSING');
      setIsTransitioningComplete(false);
      setCurrentStageIndex(2);
      toast.success('Restarted AI evaluation job.');
    } catch (e) {
      toast.error('Could not restart evaluation. Please verify connection.');
      setEvalStatus('FAILED');
      setErrorMessage('We encountered a temporary problem while generating your evaluation.');
    } finally {
      setIsRetrying(false);
    }
  };

  const getRecommendationBadgeColor = (rec) => {
    if (!rec) return 'bg-slate-800 text-slate-400 border-slate-700';
    switch (rec.toUpperCase()) {
      case 'STRONG_HIRE':
        return 'bg-emerald-950/80 text-emerald-300 border-emerald-800/80';
      case 'HIRE':
        return 'bg-green-950/80 text-green-300 border-green-800/80';
      case 'CONSIDER':
        return 'bg-amber-950/80 text-amber-300 border-amber-850/80';
      case 'REJECT':
        return 'bg-rose-950/80 text-rose-300 border-rose-850/80';
      case 'UNAVAILABLE':
        return 'bg-slate-800 text-slate-300 border-slate-700';
      default:
        return 'bg-slate-800 text-slate-400 border-slate-700';
    }
  };

  const getCheatingRiskColor = (risk) => {
    if (!risk) return 'text-slate-400';
    switch (risk.toUpperCase()) {
      case 'LOW':
        return 'text-emerald-400';
      case 'MEDIUM':
        return 'text-amber-400';
      case 'HIGH':
        return 'text-rose-400 font-bold';
      default:
        return 'text-slate-400';
    }
  };

  const AnimatedProgressBar = ({ score, colorClass }) => {
    const [width, setWidth] = useState(0);
    useEffect(() => {
      const t = setTimeout(() => setWidth(score), 100);
      return () => clearTimeout(t);
    }, [score]);

    return (
      <div className="w-full h-3 bg-slate-950 border border-slate-850 rounded-full mt-2 overflow-hidden">
        <div 
          className={`h-full ${colorClass} rounded-full transition-all duration-1000 ease-out`}
          style={{ width: `${width}%` }}
        />
      </div>
    );
  };

  // Group Question Evaluations by Type
  const mcqEvaluations = result?.questionEvaluations?.filter(q => q.questionType === 'MCQ') || [];
  const scenarioEvaluations = result?.questionEvaluations?.filter(q => q.questionType === 'SCENARIO') || [];
  const projectEvaluations = result?.questionEvaluations?.filter(q => q.questionType === 'PROJECT') || [];

  // ─── 1. REAL-WORLD PROCESSING SCREEN ─────────────────────────────────────────
  if (evalStatus === 'PROCESSING' || (!result && evalStatus !== 'FAILED')) {
    return (
      <div 
        className="min-h-screen bg-slate-950 text-white flex flex-col items-center justify-center p-6 select-none"
        aria-live="polite"
        role="status"
      >
        <div className="max-w-md w-full bg-slate-900/70 backdrop-blur-xl border border-slate-800 rounded-3xl p-8 space-y-7 shadow-2xl relative overflow-hidden text-center">
          <div className="absolute -top-24 -right-24 w-52 h-52 bg-violet-600/15 rounded-full blur-3xl -z-10" />
          <div className="absolute -bottom-24 -left-24 w-52 h-52 bg-fuchsia-600/15 rounded-full blur-3xl -z-10" />

          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 bg-emerald-950/60 border border-emerald-800/60 text-emerald-400 text-xs font-black rounded-xl uppercase tracking-wider mx-auto">
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
            <span>Assessment Submitted ✓</span>
          </div>

          <div className="relative w-20 h-20 mx-auto flex items-center justify-center">
            {isTransitioningComplete ? (
              <div className="w-16 h-16 rounded-full bg-emerald-950 border-2 border-emerald-500 text-emerald-400 flex items-center justify-center animate-in zoom-in duration-300">
                <Check className="w-8 h-8 stroke-[3]" />
              </div>
            ) : (
              <>
                <div className="absolute inset-0 rounded-full border-2 border-violet-500/20" />
                <div className="absolute inset-0 rounded-full border-2 border-transparent border-t-violet-500 border-r-fuchsia-500 animate-spin" />
                <div className="w-12 h-12 rounded-full bg-slate-950 border border-slate-800 flex items-center justify-center text-violet-400 shadow-inner">
                  <Brain className="w-6 h-6 animate-pulse" />
                </div>
              </>
            )}
          </div>

          <div className="space-y-2">
            <h2 className="text-2xl font-black text-slate-100 tracking-tight">
              {isTransitioningComplete ? 'Evaluation Complete ✓' : 'AI Evaluation in Progress'}
            </h2>
            <p className="text-slate-400 text-xs leading-relaxed max-w-sm mx-auto font-medium">
              {isTransitioningComplete 
                ? 'Your personalized assessment report is ready.' 
                : 'Your answers have been securely submitted. Our AI pipeline is analyzing your technical responses.'}
            </p>
          </div>

          <div className="space-y-3 text-left bg-slate-950/70 border border-slate-850/90 rounded-2xl p-5 shadow-inner">
            {STAGES.map((stage, idx) => {
              const isCompleted = isTransitioningComplete || idx < currentStageIndex;
              const isCurrent = !isTransitioningComplete && idx === currentStageIndex;

              return (
                <div key={stage.id} className="flex items-center gap-3">
                  <div className="shrink-0 flex items-center justify-center">
                    {isCompleted ? (
                      <div className="w-5 h-5 rounded-full bg-emerald-950 border border-emerald-500/80 text-emerald-400 flex items-center justify-center text-[10px] font-bold">
                        <Check className="w-3 h-3 stroke-[3]" />
                      </div>
                    ) : isCurrent ? (
                      <div className="w-5 h-5 rounded-full bg-violet-950 border border-violet-500 flex items-center justify-center">
                        <Loader2 className="w-3 h-3 text-violet-400 animate-spin" />
                      </div>
                    ) : (
                      <div className="w-5 h-5 rounded-full bg-slate-900 border border-slate-800 flex items-center justify-center">
                        <div className="w-1.5 h-1.5 rounded-full bg-slate-700" />
                      </div>
                    )}
                  </div>
                  <span className={`text-xs tracking-wide transition-colors ${
                    isCompleted 
                      ? 'text-slate-300 font-semibold' 
                      : isCurrent 
                        ? 'text-violet-300 font-bold' 
                        : 'text-slate-600 font-medium'
                  }`}>
                    {stage.label}
                  </span>
                </div>
              );
            })}
          </div>

          {networkWarning && (
            <div className="bg-amber-950/30 border border-amber-800/40 rounded-xl p-3 flex items-center gap-2.5 text-left animate-in fade-in duration-300">
              <Clock className="w-4 h-4 text-amber-400 shrink-0" />
              <span className="text-[11px] font-bold text-amber-300 leading-snug">{networkWarning}</span>
            </div>
          )}

          <div className="text-center pt-1">
            <p className="text-[11px] text-slate-500 font-medium leading-relaxed">
              This usually takes a short while. You can keep this page open while we prepare your results.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // ─── 2. EVALUATION FAILED SCREEN ─────────────────────────────────────────────
  if (evalStatus === 'FAILED' && !result) {
    return (
      <div className="min-h-screen bg-slate-950 text-white flex flex-col items-center justify-center p-6 select-none">
        <div className="max-w-md w-full bg-slate-900/60 border border-slate-800 rounded-3xl p-8 space-y-6 shadow-2xl text-center">
          <div className="w-16 h-16 rounded-3xl bg-rose-950/60 border border-rose-800/60 flex items-center justify-center mx-auto text-rose-400">
            <AlertTriangle className="w-8 h-8" />
          </div>

          <div className="space-y-2">
            <h2 className="text-2xl font-black text-slate-100">We couldn&apos;t complete your AI evaluation</h2>
            <p className="text-slate-400 text-sm leading-relaxed">
              Your answers were successfully submitted and are safe. We encountered a temporary problem while generating your evaluation.
            </p>
          </div>

          <div className="flex flex-col sm:flex-row gap-3 pt-2">
            <button
              onClick={handleRetry}
              disabled={isRetrying}
              className="flex-1 py-3.5 bg-gradient-to-r from-violet-600 to-fuchsia-600 hover:opacity-95 text-white font-black text-sm rounded-2xl uppercase tracking-wider transition-all shadow-md cursor-pointer disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {isRetrying ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Retrying...</span>
                </>
              ) : (
                <>
                  <RefreshCw className="w-4 h-4" />
                  <span>Try Evaluation Again</span>
                </>
              )}
            </button>
            <Link
              to="/dashboard"
              className="flex-1 py-3.5 bg-slate-850 hover:bg-slate-800 border border-slate-800 text-slate-300 font-bold text-sm rounded-2xl uppercase tracking-wider transition-all flex items-center justify-center"
            >
              Back to Dashboard
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // ─── 3. COMPLETED RESULT SCORECARD SCREEN ───────────────────────────────────
  return (
    <div className="min-h-screen bg-slate-950 text-white py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-4xl mx-auto space-y-8">
        
        {/* Core Result Summary Header Card */}
        <div className="bg-slate-900/40 backdrop-blur-2xl border border-slate-800 rounded-3xl p-8 relative overflow-hidden shadow-2xl">
          <div className="absolute top-0 right-0 w-64 h-64 bg-violet-600/10 rounded-full filter blur-3xl -z-10" />
          <div className="absolute bottom-0 left-0 w-64 h-64 bg-fuchsia-600/10 rounded-full filter blur-3xl -z-10" />

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6 pb-6 border-b border-slate-850">
            <div>
              <span className="inline-flex items-center gap-1.5 px-3 py-1 bg-violet-950/40 border border-violet-900/40 text-violet-400 text-xs font-black rounded-lg uppercase tracking-wider mb-3">
                <Award className="w-3.5 h-3.5" /> Assessment Result
              </span>
              <h1 className="text-3xl font-black text-slate-100">{result.candidateName}</h1>
              <p className="text-slate-400 mt-1 font-medium">Evaluation for target role: <span className="text-slate-200 font-bold">{result.targetRole}</span></p>
            </div>

            <div className="text-left sm:text-right flex flex-col sm:items-end justify-center">
              <span className={`px-4 py-2 border text-sm font-black rounded-2xl uppercase tracking-wider ${getRecommendationBadgeColor(result.hiringRecommendation)}`}>
                {result.hiringRecommendation?.replace('_', ' ')}
              </span>
              {result.hiringExplanation && (
                <p className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mt-2 max-w-xs">{result.hiringExplanation}</p>
              )}
            </div>
          </div>

          {result.evaluationStatus === 'PARTIAL_AI_FAILURE' && result.evaluationWarning && (
            <div className="mt-6 w-full bg-amber-950/30 border border-amber-800/50 rounded-2xl px-5 py-4 text-sm text-amber-200">
              {result.evaluationWarning}
            </div>
          )}

          <div className="pt-8 flex flex-col sm:flex-row items-center gap-10">
            {/* Score Ring */}
            <div className="relative w-44 h-44 flex items-center justify-center shrink-0">
              <svg className="w-full h-full transform -rotate-90">
                <circle cx="88" cy="88" r="76" stroke="rgba(30, 41, 59, 0.5)" strokeWidth="12" fill="transparent" />
                <circle 
                  cx="88" 
                  cy="88" 
                  r="76" 
                  stroke="url(#violet-grad)" 
                  strokeWidth="12" 
                  fill="transparent" 
                  strokeDasharray={477.5} 
                  strokeDashoffset={477.5 * (1 - Math.min(100, Math.max(0, result.finalScore)) / 100)} 
                  className="transition-all duration-1000" 
                />
                <defs>
                  <linearGradient id="violet-grad" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="#8b5cf6" />
                    <stop offset="100%" stopColor="#d946ef" />
                  </linearGradient>
                </defs>
              </svg>
              <div className="absolute flex flex-col items-center">
                <span className="text-4xl font-black text-white">{Math.round(result.finalScore)}%</span>
                <span className="text-[9px] font-bold text-slate-500 uppercase tracking-widest mt-1">Overall Score</span>
              </div>
            </div>

            {/* AI Hiring Verdict Description */}
            <div className="space-y-3 flex-1 min-w-0">
              <h3 className="text-lg font-bold text-slate-300">AI Hiring Verdict</h3>
              <p className="text-slate-400 text-sm leading-relaxed font-medium">
                {result.technicalFeedback || "Assessment evaluation is successfully completed."}
              </p>
            </div>
          </div>
        </div>

        {/* Section Scores Breakdown */}
        <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-8 space-y-6 shadow-xl">
          <h3 className="text-xl font-bold border-b border-slate-850 pb-3 flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-violet-400" /> Section Scores Breakdown
          </h3>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="bg-slate-950/40 p-5 border border-slate-850 rounded-2xl space-y-2">
              <div className="flex justify-between items-center text-sm font-bold">
                <span className="text-slate-400">MCQ (40%)</span>
                <span className="text-slate-200">{Math.round(result.mcqScore)} / 100</span>
              </div>
              <AnimatedProgressBar score={result.mcqScore} colorClass="bg-blue-500" />
            </div>

            <div className="bg-slate-950/40 p-5 border border-slate-850 rounded-2xl space-y-2">
              <div className="flex justify-between items-center text-sm font-bold">
                <span className="text-slate-400">Scenario (30%)</span>
                <span className="text-slate-200">{Math.round(result.scenarioScore)} / 100</span>
              </div>
              <AnimatedProgressBar score={result.scenarioScore} colorClass="bg-amber-500" />
            </div>

            <div className="bg-slate-950/40 p-5 border border-slate-850 rounded-2xl space-y-2">
              <div className="flex justify-between items-center text-sm font-bold">
                <span className="text-slate-400">Project (30%)</span>
                <span className="text-slate-200">{Math.round(result.projectScore)} / 100</span>
              </div>
              <AnimatedProgressBar score={result.projectScore} colorClass="bg-fuchsia-500" />
            </div>
          </div>
        </div>

        {/* ─── 4. QUESTION-BY-QUESTION DETAILED EVALUATION ─────────────────────────── */}
        
        {/* Section A: MCQ Detailed Review */}
        {mcqEvaluations.length > 0 && (
          <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-8 space-y-6 shadow-xl">
            <div 
              onClick={() => toggleSection('mcq')}
              className="flex items-center justify-between cursor-pointer border-b border-slate-850 pb-3 select-none"
            >
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-xl bg-blue-500/20 border border-blue-500/30 flex items-center justify-center text-blue-400 font-black text-xs">
                  40%
                </div>
                <div>
                  <h3 className="text-xl font-bold text-slate-100 flex items-center gap-2">
                    Multiple Choice Review ({mcqEvaluations.length} Questions)
                  </h3>
                  <p className="text-xs text-slate-400 font-medium">Deterministic verification with concept explanations</p>
                </div>
              </div>
              <button className="text-slate-400 hover:text-slate-200">
                {openSections.mcq ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
              </button>
            </div>

            {openSections.mcq && (
              <div className="space-y-4 pt-2">
                {mcqEvaluations.map((qe, idx) => {
                  const isQeCorrect = qe.isCorrect === true || qe.correct === true || qe.score >= 100 || (
                    Boolean(qe.candidateAnswer && qe.correctAnswer) &&
                    qe.candidateAnswer.trim().toLowerCase() === qe.correctAnswer.trim().toLowerCase()
                  );
                  return (
                    <div key={qe.questionId || idx} className="bg-slate-950/60 border border-slate-850 rounded-2xl p-5 space-y-4">
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-850/60 pb-3">
                        <div className="flex items-center gap-2">
                          <span className="px-2.5 py-1 bg-slate-900 border border-slate-850 text-slate-300 font-bold text-xs rounded-lg">
                            Question {idx + 1}
                          </span>
                          <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                            {qe.category || 'General'}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          {isQeCorrect ? (
                            <span className="inline-flex items-center gap-1.5 px-3 py-1 bg-emerald-950/60 border border-emerald-800/60 text-emerald-400 text-xs font-bold rounded-lg">
                              <CheckCircle2 className="w-3.5 h-3.5" /> Correct (100%)
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 px-3 py-1 bg-rose-950/60 border border-rose-800/60 text-rose-400 text-xs font-bold rounded-lg">
                              <XCircle className="w-3.5 h-3.5" /> Incorrect (0%)
                            </span>
                          )}
                        </div>
                      </div>

                      <h4 className="font-bold text-slate-200 text-sm leading-relaxed">{qe.question}</h4>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                        <div className={`p-3 rounded-xl border ${isQeCorrect ? 'bg-emerald-950/30 border-emerald-800/40 text-emerald-300' : 'bg-rose-950/30 border-rose-800/40 text-rose-300'}`}>
                          <span className="font-black uppercase tracking-wider block mb-1">Your Answer:</span>
                          <span className="font-bold">{qe.candidateAnswer || '(No answer provided)'}</span>
                        </div>
                        <div className="p-3 bg-slate-900/60 border border-slate-800 rounded-xl text-slate-300">
                          <span className="font-black uppercase tracking-wider text-violet-400 block mb-1">Correct Answer:</span>
                          <span className="font-bold text-emerald-400">{qe.correctAnswer}</span>
                        </div>
                      </div>

                      {qe.explanation && (
                        <div className="bg-slate-900/40 border border-slate-850/80 rounded-xl p-3.5 text-xs text-slate-300 flex items-start gap-2.5">
                          <Lightbulb className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                          <div>
                            <span className="font-bold text-amber-300 block mb-0.5">Explanation:</span>
                            <span className="leading-relaxed text-slate-400">{qe.explanation}</span>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Section B: Scenario Detailed Review */}
        {scenarioEvaluations.length > 0 && (
          <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-8 space-y-6 shadow-xl">
            <div 
              onClick={() => toggleSection('scenario')}
              className="flex items-center justify-between cursor-pointer border-b border-slate-850 pb-3 select-none"
            >
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-xl bg-amber-500/20 border border-amber-500/30 flex items-center justify-center text-amber-400 font-black text-xs">
                  30%
                </div>
                <div>
                  <h3 className="text-xl font-bold text-slate-100 flex items-center gap-2">
                    Scenario Engineering Review ({scenarioEvaluations.length} Questions)
                  </h3>
                  <p className="text-xs text-slate-400 font-medium">Real-world production incident and architecture evaluation</p>
                </div>
              </div>
              <button className="text-slate-400 hover:text-slate-200">
                {openSections.scenario ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
              </button>
            </div>

            {openSections.scenario && (
              <div className="space-y-6 pt-2">
                {scenarioEvaluations.map((qe, idx) => (
                  <div key={qe.questionId || idx} className="bg-slate-950/60 border border-slate-850 rounded-2xl p-6 space-y-4">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-850/60 pb-3">
                      <div className="flex items-center gap-2">
                        <span className="px-2.5 py-1 bg-violet-950/60 border border-violet-850 text-violet-400 font-bold text-xs rounded-lg">
                          Scenario {idx + 1}
                        </span>
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          {qe.category || 'System Design'}
                        </span>
                      </div>
                      <div>
                        <span className={`inline-flex items-center gap-1.5 px-3 py-1 border text-xs font-bold rounded-lg ${
                          qe.score >= 80 
                            ? 'bg-emerald-950/60 border-emerald-800/60 text-emerald-400' 
                            : qe.score >= 50 
                              ? 'bg-amber-950/60 border-amber-850/60 text-amber-400' 
                              : 'bg-rose-950/60 border-rose-800/60 text-rose-400'
                        }`}>
                          Score: {Math.round(qe.score)}/100
                        </span>
                      </div>
                    </div>

                    <h4 className="font-bold text-slate-200 text-base leading-relaxed">{qe.question}</h4>

                    <div className="space-y-2">
                      <span className="text-xs font-black text-slate-500 uppercase tracking-wider block">Your Solution:</span>
                      <div className="p-4 bg-slate-900 border border-slate-850 rounded-2xl text-xs text-slate-300 font-mono whitespace-pre-wrap leading-relaxed">
                        {qe.candidateAnswer && qe.candidateAnswer.trim() ? qe.candidateAnswer : <span className="text-slate-500 italic">No solution provided</span>}
                      </div>
                    </div>

                    {qe.expectedPoints && qe.expectedPoints.length > 0 && (
                      <div className="space-y-1.5 pt-1">
                        <span className="text-xs font-bold text-slate-400 uppercase tracking-wider block">Expected Key Points:</span>
                        <div className="flex flex-wrap gap-2">
                          {qe.expectedPoints.map((pt, i) => (
                            <span key={i} className="px-2.5 py-1 bg-slate-900 border border-slate-800 text-slate-300 text-xs font-semibold rounded-lg">
                              ✓ {pt}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {qe.recommendedAnswer && (
                      <div className="p-4 bg-slate-900/60 border border-slate-800 rounded-2xl text-xs space-y-1.5">
                        <div className="flex items-center gap-1.5 text-emerald-400 font-bold uppercase tracking-wider">
                          <Star className="w-3.5 h-3.5 text-emerald-400" />
                          <span>Recommended Approach:</span>
                        </div>
                        <p className="text-slate-300 leading-relaxed font-medium">{qe.recommendedAnswer}</p>
                      </div>
                    )}

                    {qe.missingPoints && qe.missingPoints.length > 0 && (
                      <div className="p-3 bg-amber-950/20 border border-amber-800/30 rounded-xl text-xs text-amber-300 space-y-1">
                        <span className="font-bold uppercase tracking-wider block">Missing / Unaddressed Aspects:</span>
                        <ul className="list-disc list-inside space-y-0.5 text-slate-400">
                          {qe.missingPoints.map((mp, i) => (
                            <li key={i}>{mp}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Section C: Project Detailed Review */}
        {projectEvaluations.length > 0 && (
          <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-8 space-y-6 shadow-xl">
            <div 
              onClick={() => toggleSection('project')}
              className="flex items-center justify-between cursor-pointer border-b border-slate-850 pb-3 select-none"
            >
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-xl bg-fuchsia-500/20 border border-fuchsia-500/30 flex items-center justify-center text-fuchsia-400 font-black text-xs">
                  30%
                </div>
                <div>
                  <h3 className="text-xl font-bold text-slate-100 flex items-center gap-2">
                    Project Deep-Dive Review ({projectEvaluations.length} Questions)
                  </h3>
                  <p className="text-xs text-slate-400 font-medium">Component ownership, architecture decisions, and scaling trade-offs</p>
                </div>
              </div>
              <button className="text-slate-400 hover:text-slate-200">
                {openSections.project ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
              </button>
            </div>

            {openSections.project && (
              <div className="space-y-6 pt-2">
                {projectEvaluations.map((qe, idx) => (
                  <div key={qe.questionId || idx} className="bg-slate-950/60 border border-slate-850 rounded-2xl p-6 space-y-4">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-850/60 pb-3">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="px-2.5 py-1 bg-fuchsia-950/60 border border-fuchsia-850 text-fuchsia-400 font-bold text-xs rounded-lg">
                          Project Q{idx + 1}
                        </span>
                        {qe.projectReference && (
                          <span className="px-2.5 py-1 bg-slate-900 border border-slate-800 text-slate-300 font-bold text-xs rounded-lg">
                            Ref: {qe.projectReference}
                          </span>
                        )}
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          {qe.category || 'Architecture'}
                        </span>
                      </div>
                      <div>
                        <span className={`inline-flex items-center gap-1.5 px-3 py-1 border text-xs font-bold rounded-lg ${
                          qe.score >= 80 
                            ? 'bg-emerald-950/60 border-emerald-800/60 text-emerald-400' 
                            : qe.score >= 50 
                              ? 'bg-amber-950/60 border-amber-850/60 text-amber-400' 
                              : 'bg-rose-950/60 border-rose-800/60 text-rose-400'
                        }`}>
                          Score: {Math.round(qe.score)}/100
                        </span>
                      </div>
                    </div>

                    <h4 className="font-bold text-slate-200 text-base leading-relaxed">{qe.question}</h4>

                    <div className="space-y-2">
                      <span className="text-xs font-black text-slate-500 uppercase tracking-wider block">Your Explanation:</span>
                      <div className="p-4 bg-slate-900 border border-slate-850 rounded-2xl text-xs text-slate-300 font-mono whitespace-pre-wrap leading-relaxed">
                        {qe.candidateAnswer && qe.candidateAnswer.trim() ? qe.candidateAnswer : <span className="text-slate-500 italic">No explanation provided</span>}
                      </div>
                    </div>

                    {qe.expectedPoints && qe.expectedPoints.length > 0 && (
                      <div className="space-y-1.5 pt-1">
                        <span className="text-xs font-bold text-slate-400 uppercase tracking-wider block">Expected Technical Points:</span>
                        <div className="flex flex-wrap gap-2">
                          {qe.expectedPoints.map((pt, i) => (
                            <span key={i} className="px-2.5 py-1 bg-slate-900 border border-slate-800 text-slate-300 text-xs font-semibold rounded-lg">
                              ✓ {pt}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {qe.recommendedAnswer && (
                      <div className="p-4 bg-slate-900/60 border border-slate-800 rounded-2xl text-xs space-y-1.5">
                        <div className="flex items-center gap-1.5 text-emerald-400 font-bold uppercase tracking-wider">
                          <Star className="w-3.5 h-3.5 text-emerald-400" />
                          <span>Recommended Explanation:</span>
                        </div>
                        <p className="text-slate-300 leading-relaxed font-medium">{qe.recommendedAnswer}</p>
                      </div>
                    )}

                    {qe.missingPoints && qe.missingPoints.length > 0 && (
                      <div className="p-3 bg-amber-950/20 border border-amber-800/30 rounded-xl text-xs text-amber-300 space-y-1">
                        <span className="font-bold uppercase tracking-wider block">Missing Implementation Details:</span>
                        <ul className="list-disc list-inside space-y-0.5 text-slate-400">
                          {qe.missingPoints.map((mp, i) => (
                            <li key={i}>{mp}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Category Scores Breakdown */}
        {result.categoryScores && Object.keys(result.categoryScores).length > 0 && (
          <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-8 space-y-4 shadow-xl">
            <h3 className="text-lg font-bold border-b border-slate-850 pb-3 flex items-center gap-2">
              <Layers className="w-5 h-5 text-violet-400" /> Concept & Category Breakdown
            </h3>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              {Object.entries(result.categoryScores).map(([cat, score]) => (
                <div key={cat} className="bg-slate-950/60 p-3.5 border border-slate-850 rounded-xl flex items-center justify-between">
                  <span className="text-xs font-bold text-slate-400 truncate mr-2">{cat}</span>
                  <span className="text-xs font-black text-violet-400">{Math.round(score)}%</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Strengths & Weak Areas */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-8 space-y-4 shadow-xl">
            <h3 className="text-lg font-bold flex items-center gap-2 text-emerald-400">
              <CheckCircle2 className="w-5 h-5 text-emerald-500" /> Strengths
            </h3>
            <ul className="space-y-3 font-semibold text-sm text-slate-300">
              {result.strengths && result.strengths.length > 0 ? (
                result.strengths.map((s, i) => (
                  <li key={i} className="bg-slate-950/40 p-3.5 border border-slate-850 rounded-2xl leading-relaxed">
                    • {s}
                  </li>
                ))
              ) : (
                <li className="text-slate-500 italic">No specific strengths mapped.</li>
              )}
            </ul>
          </div>

          <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-8 space-y-4 shadow-xl">
            <h3 className="text-lg font-bold flex items-center gap-2 text-rose-400">
              <XCircle className="w-5 h-5 text-rose-500" /> Weak Areas
            </h3>
            <ul className="space-y-3 font-semibold text-sm text-slate-300">
              {result.weakAreas && result.weakAreas.length > 0 ? (
                result.weakAreas.map((w, i) => (
                  <li key={i} className="bg-slate-950/40 p-3.5 border border-slate-850 rounded-2xl leading-relaxed">
                    • {w}
                  </li>
                ))
              ) : (
                <li className="text-slate-500 italic">No critical weak areas mapped.</li>
              )}
            </ul>
          </div>
        </div>

        {/* Learning Recommendations */}
        <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-8 space-y-4 shadow-xl">
          <h3 className="text-xl font-bold border-b border-slate-850 pb-3 flex items-center gap-2">
            <BookOpen className="w-5 h-5 text-violet-400" /> Learning Recommendations
          </h3>
          <ul className="space-y-3 font-semibold text-sm text-slate-300">
            {result.learningRecommendations && result.learningRecommendations.length > 0 ? (
              result.learningRecommendations.map((r, i) => (
                <li key={i} className="bg-slate-950/40 p-4 border border-slate-850 rounded-2xl leading-relaxed flex items-start gap-2.5">
                  <span className="text-violet-400 shrink-0 mt-0.5">•</span>
                  <span>{r}</span>
                </li>
              ))
            ) : (
              <li className="text-slate-500 italic">No specific study targets suggested.</li>
            )}
          </ul>
        </div>

        {/* Integrity Report */}
        <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-8 space-y-4 shadow-xl">
          <h3 className="text-xl font-bold border-b border-slate-850 pb-3 flex items-center gap-2 text-rose-400">
            <ShieldCheck className="w-5 h-5 text-violet-400" /> Security & Integrity Report
          </h3>

          <div className="flex items-center gap-6 bg-slate-950/40 p-5 border border-slate-850 rounded-2xl text-sm font-bold">
            <div>
              <span className="text-slate-500">Risk Level:</span>{' '}
              <span className={getCheatingRiskColor(result.cheatingRisk)}>{result.cheatingRisk || 'LOW'}</span>
            </div>
            <div className="w-px h-5 bg-slate-800" />
            <div>
              <span className="text-slate-500">Total Violations:</span>{' '}
              <span className="text-slate-200">{result.totalViolations || 0}</span>
            </div>
          </div>

          <div className="space-y-2">
            {result.violations && result.violations.length > 0 ? (
              result.violations.map((v, i) => (
                <div key={i} className="p-3.5 bg-slate-950/20 border border-slate-850 rounded-2xl text-xs font-mono text-slate-400 leading-relaxed">
                  [{v.timestamp?.substring(11, 19) || 'LOG'}] {v.type} - <span className="text-rose-500 font-bold">{v.severity}</span> - {v.description}
                </div>
              ))
            ) : (
              <div className="text-slate-500 text-xs font-medium italic p-2">
                No compliance issues or violations recorded during the assessment window.
              </div>
            )}
          </div>
        </div>

        {/* Dashboard Link button */}
        <div className="flex justify-center pt-4">
          <Link 
            to="/dashboard"
            className="px-10 py-4 bg-slate-850 hover:bg-slate-850/80 border border-slate-800 text-slate-200 font-black text-sm rounded-2xl uppercase tracking-wider transition-all cursor-pointer"
          >
            Return to Dashboard
          </Link>
        </div>

      </div>
    </div>
  );
};

export default ResultPage;
