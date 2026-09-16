import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { interviewService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import {
  Mic, MicOff, Video, VideoOff, Volume2, VolumeX, RefreshCw, Send,
  CheckCircle2, AlertCircle, ShieldCheck, Award, Clock, ChevronRight,
  LogOut, Sparkles, Brain, Bot, User, MessageSquare, BookOpen, Lightbulb,
  Check, ArrowRight, CornerDownLeft, Volume1, Square
} from 'lucide-react';
import toast from 'react-hot-toast';

export default function InterviewRoomPage() {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [isTranscribing, setIsTranscribing] = useState(false);
  const [candidateAnswer, setCandidateAnswer] = useState('');
  const [interimTranscript, setInterimTranscript] = useState('');
  const [isListening, setIsListening] = useState(false);
  const [recordingSeconds, setRecordingSeconds] = useState(0);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [aiVoiceMuted, setAiVoiceMuted] = useState(false);
  const [cameraActive, setCameraActive] = useState(false);
  const [micActive, setMicActive] = useState(false);
  const [selectedVoice, setSelectedVoice] = useState(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [lastTurnFeedback, setLastTurnFeedback] = useState(null);

  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const recognitionRef = useRef(null);
  const speechRecognitionBlockedRef = useRef(false);
  const mediaRecorderRef = useRef(null);
  const audioChunksRef = useRef([]);
  const isListeningRef = useRef(false);
  const recordingTimerRef = useRef(null);
  const speechUtteranceRef = useRef(null);
  const canvasRef = useRef(null);
  const animationFrameRef = useRef(null);
  const audioContextRef = useRef(null);
  const analyserRef = useRef(null);
  const [candidateAudioLevel, setCandidateAudioLevel] = useState(0);

  // ─── 1. FETCH INITIAL SESSION ──────────────────────────────────────────
  useEffect(() => {
    let mounted = true;
    async function loadSession() {
      try {
        setLoading(true);
        const res = await interviewService.getSession(sessionId);
        if (mounted) {
          setSession(res.data);
          if (res.data?.currentQuestion && res.data.status !== 'COMPLETED') {
            setTimeout(() => {
              speakText(res.data.currentQuestion);
            }, 800);
          }
        }
      } catch (err) {
        console.error('Failed to load interview session', err);
        toast.error('Could not load interview session. Redirecting...');
        navigate('/profile');
      } finally {
        if (mounted) setLoading(false);
      }
    }
    loadSession();
    return () => {
      mounted = false;
    };
  }, [sessionId, navigate]);

  // ─── 2. TIMER ──────────────────────────────────────────────────────────
  useEffect(() => {
    if (session?.status === 'COMPLETED' || session?.status === 'EXITED') return;
    const interval = setInterval(() => {
      setElapsedSeconds(prev => prev + 1);
    }, 1000);
    return () => clearInterval(interval);
  }, [session?.status]);

  const formatTimer = (secs) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  // ─── 3. WEBCAM & MICROPHONE STREAM (WITH RESILIENT FALLBACK) ───────────
  useEffect(() => {
    let active = true;

    async function initMedia() {
      try {
        let stream;
        try {
          stream = await navigator.mediaDevices.getUserMedia({
            video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' },
            audio: { echoCancellation: true, noiseSuppression: true }
          });
          if (active) setCameraActive(true);
        } catch (videoErr) {
          console.warn('Video + Audio failed, falling back to audio only:', videoErr);
          try {
            stream = await navigator.mediaDevices.getUserMedia({
              audio: { echoCancellation: true, noiseSuppression: true }
            });
          } catch (audioErr) {
            console.warn('Audio-only getUserMedia also failed:', audioErr);
          }
          if (active) setCameraActive(false);
        }

        if (!active) {
          if (stream) stream.getTracks().forEach(t => t.stop());
          return;
        }

        if (stream) {
          streamRef.current = stream;

          if (videoRef.current && stream.getVideoTracks().length > 0) {
            videoRef.current.srcObject = stream;
            videoRef.current.play().catch(() => {});
          }

          if (stream.getAudioTracks().length > 0) {
            setMicActive(true);
            try {
              const AudioContext = window.AudioContext || window.webkitAudioContext;
              if (AudioContext) {
                const audioCtx = new AudioContext();
                audioContextRef.current = audioCtx;
                const source = audioCtx.createMediaStreamSource(stream);
                const analyser = audioCtx.createAnalyser();
                analyser.fftSize = 64;
                source.connect(analyser);
                analyserRef.current = analyser;

                const dataArray = new Uint8Array(analyser.frequencyBinCount);
                const checkVolume = () => {
                  if (!active) return;
                  analyser.getByteFrequencyData(dataArray);
                  let sum = 0;
                  for (let i = 0; i < dataArray.length; i++) {
                    sum += dataArray[i];
                  }
                  const avg = sum / dataArray.length;
                  setCandidateAudioLevel(Math.min(100, Math.round((avg / 128) * 100)));
                  requestAnimationFrame(checkVolume);
                };
                checkVolume();
              }
            } catch (e) {
              console.warn('Web Audio API not supported for VU meter', e);
            }
          }
        }
      } catch (err) {
        console.warn('Device permission warning:', err);
      }
    }

    initMedia();

    return () => {
      active = false;
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(t => t.stop());
      }
      if (audioContextRef.current && audioContextRef.current.state !== 'closed') {
        audioContextRef.current.close().catch(() => {});
      }
    };
  }, []);

  const toggleCamera = async () => {
    if (streamRef.current && streamRef.current.getVideoTracks().length > 0) {
      const videoTrack = streamRef.current.getVideoTracks()[0];
      videoTrack.enabled = !videoTrack.enabled;
      setCameraActive(videoTrack.enabled);
    } else {
      try {
        const videoStream = await navigator.mediaDevices.getUserMedia({
          video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' }
        });
        if (videoRef.current) {
          videoRef.current.srcObject = videoStream;
          videoRef.current.play().catch(() => {});
        }
        if (streamRef.current) {
          videoStream.getVideoTracks().forEach(t => streamRef.current.addTrack(t));
        } else {
          streamRef.current = videoStream;
        }
        setCameraActive(true);
      } catch (e) {
        toast.error('Unable to access webcam. Please check browser camera permissions.', { id: 'cam-perm-err' });
        setCameraActive(false);
      }
    }
  };

  const toggleMic = async () => {
    if (streamRef.current && streamRef.current.getAudioTracks().length > 0) {
      const audioTrack = streamRef.current.getAudioTracks()[0];
      audioTrack.enabled = !audioTrack.enabled;
      setMicActive(audioTrack.enabled);
    } else {
      try {
        const audioStream = await navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: true }
        });
        if (streamRef.current) {
          audioStream.getAudioTracks().forEach(t => streamRef.current.addTrack(t));
        } else {
          streamRef.current = audioStream;
        }
        setMicActive(true);
      } catch (e) {
        toast.error('Unable to access microphone. Please check browser microphone permissions.', { id: 'mic-perm-err' });
        setMicActive(false);
      }
    }
  };

  // ─── 4. BROWSER SPEECH SYNTHESIS (BOT VOICE - 100% FREE) ───────────────
  useEffect(() => {
    const loadVoices = () => {
      if (typeof window === 'undefined' || !window.speechSynthesis) return;
      const voices = window.speechSynthesis.getVoices();
      if (!voices || voices.length === 0) return;

      const preferred = voices.find(v =>
        (v.name.includes('Natural') || v.name.includes('Neural') || v.name.includes('Google') || v.name.includes('Samantha') || v.name.includes('Guy') || v.name.includes('Jenny')) &&
        v.lang.startsWith('en')
      ) || voices.find(v => v.lang.startsWith('en-US')) || voices.find(v => v.lang.startsWith('en')) || voices[0];

      setSelectedVoice(preferred);
    };

    loadVoices();
    if (typeof window !== 'undefined' && window.speechSynthesis) {
      window.speechSynthesis.onvoiceschanged = loadVoices;
    }

    return () => {
      if (typeof window !== 'undefined' && window.speechSynthesis) {
        window.speechSynthesis.cancel();
      }
    };
  }, []);

  const speakText = useCallback((text) => {
    if (typeof window === 'undefined' || !window.speechSynthesis || aiVoiceMuted || !text) return;
    try {
      window.speechSynthesis.cancel();
      const cleanText = text.replace(/[*#`_]/g, '');
      const utterance = new SpeechSynthesisUtterance(cleanText);
      speechUtteranceRef.current = utterance;
      if (selectedVoice) {
        utterance.voice = selectedVoice;
      }
      utterance.rate = 1.0;
      utterance.pitch = 1.0;

      utterance.onstart = () => setIsSpeaking(true);
      utterance.onend = () => setIsSpeaking(false);
      utterance.onerror = () => setIsSpeaking(false);

      window.speechSynthesis.speak(utterance);
    } catch (e) {
      console.warn('Speech synthesis error', e);
      setIsSpeaking(false);
    }
  }, [aiVoiceMuted, selectedVoice]);

  const stopSpeaking = useCallback(() => {
    if (typeof window !== 'undefined' && window.speechSynthesis) {
      window.speechSynthesis.cancel();
      setIsSpeaking(false);
    }
  }, []);

  // ─── 5. HYBRID CANDIDATE VOICE ENGINE (ON-DEMAND MIC + STT + MediaRecorder + AI Transcribe) ───
  const startListening = useCallback(async () => {
    stopSpeaking();

    // Ensure we have a live microphone stream
    let audioStream = streamRef.current;
    let liveTracks = audioStream?.getAudioTracks()?.filter(t => t.readyState === 'live');

    if (!liveTracks || liveTracks.length === 0) {
      try {
        const freshAudio = await navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: true }
        });
        if (!streamRef.current) {
          streamRef.current = freshAudio;
        } else {
          freshAudio.getAudioTracks().forEach(t => streamRef.current.addTrack(t));
        }
        audioStream = streamRef.current;
        liveTracks = audioStream.getAudioTracks().filter(t => t.readyState === 'live');
        setMicActive(true);
      } catch (err) {
        console.error('Could not get microphone on demand', err);
        toast.error('Please click the microphone icon in your browser address bar to allow mic access.', { id: 'mic-perm-error' });
        return;
      }
    }

    if (!liveTracks || liveTracks.length === 0) {
      toast.error('No microphone found. Please connect a microphone or type your answer.', { id: 'no-mic-error' });
      return;
    }

    setIsListening(true);
    isListeningRef.current = true;
    setInterimTranscript('');
    setRecordingSeconds(0);

    // A. Start native MediaRecorder with an AUDIO-ONLY stream
    audioChunksRef.current = [];
    try {
      const recordingStream = new MediaStream([liveTracks[0]]);
      let mimeType = 'audio/webm';
      if (typeof MediaRecorder !== 'undefined') {
        if (MediaRecorder.isTypeSupported('audio/webm;codecs=opus')) {
          mimeType = 'audio/webm;codecs=opus';
        } else if (MediaRecorder.isTypeSupported('audio/webm')) {
          mimeType = 'audio/webm';
        } else if (MediaRecorder.isTypeSupported('audio/mp4')) {
          mimeType = 'audio/mp4';
        }
      }
      const recorder = new MediaRecorder(recordingStream, { mimeType });
      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          audioChunksRef.current.push(e.data);
        }
      };
      recorder.start(250);
      mediaRecorderRef.current = recorder;
    } catch (err) {
      console.error('MediaRecorder start error:', err);
    }

    // B. Attempt Web Speech Recognition (for live captions on Chrome/Edge)
    if (!speechRecognitionBlockedRef.current) {
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (SpeechRecognition) {
        try {
          if (recognitionRef.current) {
            try { recognitionRef.current.abort(); } catch (e) {}
          }
          const recognition = new SpeechRecognition();
          recognition.continuous = true;
          recognition.interimResults = true;
          recognition.lang = 'en-US';

          recognition.onresult = (event) => {
            let interim = '';
            let finalStr = '';
            for (let i = event.resultIndex; i < event.results.length; i++) {
              const transcript = event.results[i][0].transcript;
              if (event.results[i].isFinal) {
                finalStr += transcript + ' ';
              } else {
                interim += transcript;
              }
            }
            if (finalStr) {
              setCandidateAnswer(prev => (prev ? prev.trim() + ' ' + finalStr.trim() : finalStr.trim()));
            }
            setInterimTranscript(interim);
          };

          recognition.onerror = (err) => {
            console.warn('SpeechRecognition error:', err.error);
            if (err.error === 'network' || err.error === 'service-not-allowed') {
              speechRecognitionBlockedRef.current = true;
              try { recognition.abort(); } catch (e) {}
              toast('Audio recording active! AI will transcribe your answer when finished.', {
                id: 'voice-stt-notice',
                icon: '🎙️',
                duration: 4000
              });
            } else if (err.error === 'not-allowed') {
              speechRecognitionBlockedRef.current = true;
              toast.error('Microphone permission denied for speech recognition.', { id: 'mic-denied' });
            }
          };

          recognition.onend = () => {
            if (isListeningRef.current && !speechRecognitionBlockedRef.current) {
              try {
                recognition.start();
              } catch (e) {}
            }
          };

          recognition.start();
          recognitionRef.current = recognition;
        } catch (err) {
          console.warn('Could not start SpeechRecognition:', err);
        }
      }
    }

    // C. Recording duration counter
    if (recordingTimerRef.current) clearInterval(recordingTimerRef.current);
    recordingTimerRef.current = setInterval(() => {
      setRecordingSeconds(prev => prev + 1);
    }, 1000);

    toast.success('Microphone active! Speak your answer now.', { id: 'mic-active' });
  }, [stopSpeaking]);

  const doSubmitAnswer = async (textToSubmit) => {
    if (submitting || !session?.id) return;
    const finalAnswer = (textToSubmit || candidateAnswer).trim();
    if (!finalAnswer) {
      toast.error('Please speak or type your technical answer before submitting.', { id: 'empty-ans' });
      return;
    }

    stopSpeaking();
    setSubmitting(true);

    try {
      const res = await interviewService.submitAnswer(session.id, finalAnswer);
      const updatedSession = res.data;
      setSession(updatedSession);
      setCandidateAnswer('');
      setInterimTranscript('');

      if (updatedSession.history && updatedSession.history.length > 0) {
        const lastTurn = updatedSession.history[updatedSession.history.length - 1];
        setLastTurnFeedback(lastTurn);
      }

      if (updatedSession.status === 'COMPLETED') {
        toast.success('Interview completed! Evaluation report generated.', { id: 'interview-done' });
        setTimeout(() => {
          speakText('Thank you for completing your technical interview. Your comprehensive evaluation report is ready.');
        }, 500);
      } else if (updatedSession.currentQuestion) {
        toast.success(`Question ${updatedSession.questionCount} ready!`, { id: 'next-q' });
        setTimeout(() => {
          speakText(updatedSession.currentQuestion);
        }, 600);
      }
    } catch (err) {
      console.error('Submit answer error', err);
      const message = typeof err.response?.data === 'string'
        ? err.response.data
        : err.response?.data?.error || 'Unable to evaluate answer. Please try again.';
      toast.error(message, { id: 'submit-err' });
    } finally {
      setSubmitting(false);
    }
  };

  const stopListening = useCallback(async (autoSubmitAfter = false) => {
    setIsListening(false);
    isListeningRef.current = false;
    setInterimTranscript('');
    if (recordingTimerRef.current) {
      clearInterval(recordingTimerRef.current);
      recordingTimerRef.current = null;
    }

    // Stop SpeechRecognition
    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch (e) {}
    }

    // Stop MediaRecorder and transcribe if needed
    const recorder = mediaRecorderRef.current;
    if (recorder && recorder.state !== 'inactive') {
      return new Promise((resolve) => {
        recorder.onstop = async () => {
          const chunks = audioChunksRef.current;
          let currentAnswer = candidateAnswer.trim();

          // If speech recognition didn't produce text, transcribe the recorded audio with AI
          if (!currentAnswer && chunks.length > 0) {
            const cleanMime = (recorder.mimeType || 'audio/webm').split(';')[0];
            const blob = new Blob(chunks, { type: cleanMime });
            if (blob.size > 300) {
              try {
                setIsTranscribing(true);
                toast.loading('AI is transcribing your spoken response...', { id: 'transcribe-loading' });
                const reader = new FileReader();
                reader.readAsDataURL(blob);
                reader.onloadend = async () => {
                  try {
                    const base64Data = reader.result;
                    const res = await interviewService.transcribeAudio(base64Data, cleanMime);
                    toast.dismiss('transcribe-loading');
                    const transcribed = res.data?.transcript || '';
                    if (transcribed.trim()) {
                      setCandidateAnswer(transcribed.trim());
                      currentAnswer = transcribed.trim();
                      toast.success('Voice transcribed successfully!', { id: 'transcribe-loading' });
                      if (autoSubmitAfter) {
                        doSubmitAnswer(transcribed.trim());
                      }
                    } else {
                      toast('No clear words detected. You can type your answer or try speaking again.', { id: 'transcribe-loading', icon: 'ℹ️' });
                    }
                  } catch (err) {
                    toast.dismiss('transcribe-loading');
                    console.error('Audio transcription error', err);
                    toast.error('Could not transcribe audio. Please type your answer.', { id: 'transcribe-loading' });
                  } finally {
                    setIsTranscribing(false);
                    resolve(currentAnswer);
                  }
                };
                return;
              } catch (e) {
                toast.dismiss('transcribe-loading');
                setIsTranscribing(false);
              }
            }
          }

          if (autoSubmitAfter && currentAnswer) {
            doSubmitAnswer(currentAnswer);
          }
          resolve(currentAnswer);
        };

        try {
          recorder.stop();
        } catch (e) {
          resolve('');
        }
      });
    } else {
      if (autoSubmitAfter && candidateAnswer.trim()) {
        doSubmitAnswer(candidateAnswer.trim());
      }
    }
  }, [candidateAnswer]);

  const toggleListening = () => {
    if (isListening) {
      stopListening(false);
    } else {
      startListening();
    }
  };

  // ─── 6. ANIMATED AI VOICE VISUALIZER ORB ─────────────────────────────────
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let angle = 0;

    const render = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const centerX = canvas.width / 2;
      const centerY = canvas.height / 2;

      const baseRadius = 55;
      const pulse = isSpeaking ? Math.sin(angle * 4) * 8 + Math.cos(angle * 6) * 4 : Math.sin(angle) * 3;
      const radius = baseRadius + pulse;

      const gradOuter = ctx.createRadialGradient(centerX, centerY, radius * 0.7, centerX, centerY, radius * 1.6);
      gradOuter.addColorStop(0, isSpeaking ? 'rgba(168, 85, 247, 0.45)' : 'rgba(99, 102, 241, 0.25)');
      gradOuter.addColorStop(1, 'rgba(15, 23, 42, 0)');
      ctx.fillStyle = gradOuter;
      ctx.beginPath();
      ctx.arc(centerX, centerY, radius * 1.6, 0, Math.PI * 2);
      ctx.fill();

      if (isSpeaking) {
        for (let w = 1; w <= 3; w++) {
          ctx.beginPath();
          ctx.arc(centerX, centerY, radius + (w * 14) + Math.sin(angle * 5 + w) * 6, 0, Math.PI * 2);
          ctx.strokeStyle = `rgba(192, 132, 252, ${0.4 / w})`;
          ctx.lineWidth = 2;
          ctx.stroke();
        }
      }

      const gradCore = ctx.createRadialGradient(
        centerX - radius * 0.3,
        centerY - radius * 0.3,
        radius * 0.1,
        centerX,
        centerY,
        radius
      );
      if (isSpeaking) {
        gradCore.addColorStop(0, '#f472b6');
        gradCore.addColorStop(0.5, '#a855f7');
        gradCore.addColorStop(1, '#4f46e5');
      } else {
        gradCore.addColorStop(0, '#818cf8');
        gradCore.addColorStop(0.5, '#6366f1');
        gradCore.addColorStop(1, '#312e81');
      }

      ctx.fillStyle = gradCore;
      ctx.beginPath();
      ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
      ctx.fill();

      if (isSpeaking) {
        const bars = 7;
        const barWidth = 4;
        const totalW = bars * (barWidth + 4);
        const startX = centerX - totalW / 2;

        ctx.fillStyle = '#ffffff';
        for (let i = 0; i < bars; i++) {
          const h = 12 + Math.abs(Math.sin(angle * 6 + i * 0.8)) * 26;
          ctx.fillRect(startX + i * (barWidth + 4), centerY - h / 2, barWidth, h);
        }
      } else {
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.arc(centerX - 8, centerY, 3, 0, Math.PI * 2);
        ctx.arc(centerX + 8, centerY, 3, 0, Math.PI * 2);
        ctx.fill();
      }

      angle += 0.04;
      animationFrameRef.current = requestAnimationFrame(render);
    };

    render();

    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, [isSpeaking]);

  const handleSubmitAnswer = () => {
    if (isListening) {
      stopListening(true);
    } else {
      doSubmitAnswer(candidateAnswer);
    }
  };

  const handleExit = async () => {
    if (window.confirm('Are you sure you want to leave the interview room? This interview session will be marked as exited in between.')) {
      stopSpeaking();
      if (isListening) stopListening(false);
      try {
        if (session?.id && session.status !== 'COMPLETED') {
          await interviewService.exitInterview(session.id);
        }
      } catch (e) {
        console.warn('Exit interview error:', e);
      }
      navigate('/profile');
    }
  };

  // ─── 7. FINAL REPORT SCREEN ──────────────────────────────────────────────
  if (session?.status === 'COMPLETED') {
    const report = session.finalReport || {};
    const overallScore = session.overallScore != null 
      ? Math.round(session.overallScore) 
      : (report.overallScore != null ? Math.round(report.overallScore) : 0);
    const strongAreas = report.strongAreas || report.strengths || (
      overallScore >= 60
        ? ['Foundational technical comprehension', 'Structured technical communication']
        : ['Interview participation & commitment to practice']
    );
    const weakAreas = report.weakAreas || report.weaknesses || (
      overallScore >= 60
        ? ['Deep architectural trade-offs', 'Edge case and failure scenario handling']
        : ['Core technical knowledge and fundamentals', 'Directly answering technical interview questions']
    );
    const conceptsToImprove = report.conceptsToImprove || [
      `${session.topic || 'Core'} Fundamentals & Architecture`,
      'System Design & Engineering Trade-offs'
    ];

    return (
      <div className="min-h-screen bg-slate-950 text-white flex flex-col p-4 md:p-8">
        <header className="flex items-center justify-between max-w-5xl mx-auto w-full pb-6 border-b border-slate-800">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-fuchsia-600/20 border border-fuchsia-500/30 rounded-2xl flex items-center justify-center text-fuchsia-400">
              <Award className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-black text-white">Technical Interview Report</h2>
              <p className="text-xs text-slate-400">Topic: {session.topic} • AI Evaluation Complete</p>
            </div>
          </div>
          <button
            onClick={() => navigate('/profile')}
            className="px-5 py-2.5 bg-gradient-to-r from-violet-600 to-indigo-600 hover:opacity-95 text-white text-xs font-bold rounded-xl transition-all shadow-lg flex items-center gap-2 cursor-pointer"
          >
            Save & Return to Career Dashboard <ChevronRight className="w-4 h-4" />
          </button>
        </header>

        <main className="max-w-5xl mx-auto w-full flex-1 py-8 space-y-6 overflow-y-auto">
          {/* Top Score Banner */}
          <div className="bg-gradient-to-r from-slate-900 via-indigo-950/40 to-slate-900 border border-indigo-500/20 rounded-3xl p-6 md:p-8 flex flex-col md:flex-row items-center justify-between gap-6 shadow-2xl">
            <div className="space-y-2 text-center md:text-left">
              <span className="px-3 py-1 bg-emerald-500/10 border border-emerald-500/20 rounded-full text-emerald-400 text-xs font-black uppercase tracking-wider">
                Interview Completed
              </span>
              <h3 className="text-2xl md:text-3xl font-black text-white">
                Technical Interview Readiness: {overallScore >= 80 ? 'Job Ready' : overallScore >= 65 ? 'Proficient' : 'Needs Practice'}
              </h3>
              <p className="text-slate-400 text-xs md:text-sm max-w-xl">
                {report.performanceSummary || 'Comprehensive evaluation of technical depth, clarity, and system trade-offs.'}
              </p>
            </div>

            <div className="relative flex items-center justify-center">
              <div className="w-32 h-32 rounded-full border-4 border-slate-800 flex flex-col items-center justify-center bg-slate-950 shadow-inner">
                <span className="text-4xl font-black text-white">{overallScore}%</span>
                <span className="text-[10px] font-bold text-violet-400 uppercase tracking-widest">Score</span>
              </div>
            </div>
          </div>

          {/* Breakdown Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-slate-900/80 border border-emerald-500/20 rounded-2xl p-5 space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-wider text-emerald-400 flex items-center gap-2">
                <ShieldCheck className="w-4 h-4" /> Verified Strong Areas
              </h4>
              <ul className="space-y-2">
                {strongAreas.map((s, idx) => (
                  <li key={idx} className="text-xs text-emerald-200/90 flex items-start gap-2">
                    <span className="w-4 h-4 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center text-[10px] shrink-0 mt-0.5">✓</span>
                    <span>{s}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="bg-slate-900/80 border border-amber-500/20 rounded-2xl p-5 space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-wider text-amber-400 flex items-center gap-2">
                <AlertCircle className="w-4 h-4" /> Weak Areas & Focus Concepts
              </h4>
              <ul className="space-y-2">
                {weakAreas.map((w, idx) => (
                  <li key={idx} className="text-xs text-amber-200/90 flex items-start gap-2">
                    <span className="w-4 h-4 rounded-full bg-amber-500/20 text-amber-400 flex items-center justify-center text-[10px] shrink-0 mt-0.5">!</span>
                    <span>{w}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>

          {/* Concepts to Review */}
          {conceptsToImprove && conceptsToImprove.length > 0 && (
            <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-wider text-violet-400 flex items-center gap-2">
                <BookOpen className="w-4 h-4" /> Recommended Topics to Review
              </h4>
              <div className="flex flex-wrap gap-2">
                {conceptsToImprove.map((c, idx) => (
                  <span key={idx} className="px-3 py-1.5 bg-violet-950/40 border border-violet-800/40 rounded-xl text-xs text-violet-200 font-semibold">
                    {c}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Turn-by-Turn Transcript History */}
          <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 space-y-4">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-300 flex items-center gap-2">
              <MessageSquare className="w-4 h-4 text-fuchsia-400" /> Full Interview Transcript & Feedback
            </h4>
            <div className="space-y-3">
              {session.history?.map((turn, idx) => {
                const turnScore = Math.round(turn.score != null ? turn.score : 0);
                const scoreBadgeClass = turnScore >= 70
                  ? "text-emerald-400 bg-emerald-500/10 border-emerald-500/20"
                  : turnScore >= 40
                  ? "text-amber-400 bg-amber-500/10 border-amber-500/20"
                  : "text-rose-400 bg-rose-500/10 border-rose-500/20";

                return (
                  <div key={idx} className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-2">
                    <div className="flex items-center justify-between text-xs font-bold gap-2">
                      <span className="text-violet-400">Round {idx + 1}: {turn.question}</span>
                      <span className={`px-2 py-0.5 rounded-md border shrink-0 ${scoreBadgeClass}`}>
                        Score: {turnScore}%
                      </span>
                    </div>
                    <p className="text-xs text-slate-300 italic pl-3 border-l-2 border-slate-700">
                      "{turn.candidateAnswer}"
                    </p>
                    {turn.evaluationFeedback && (
                      <p className="text-[11px] text-slate-400 pl-3">
                        <span className="text-fuchsia-400 font-bold">Interviewer Note:</span> {turn.evaluationFeedback}
                      </p>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </main>
      </div>
    );
  }

  // ─── 8. LIVE INTERVIEW ROOM STAGE ────────────────────────────────────────
  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col justify-between overflow-hidden">
      
      {/* TOP BAR */}
      <header className="border-b border-slate-850 bg-slate-900/70 backdrop-blur-md px-6 py-3 flex items-center justify-between z-20">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-fuchsia-600 to-indigo-600 flex items-center justify-center text-white shadow-md shadow-fuchsia-500/20">
            <Bot className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-extrabold text-sm tracking-wide text-white">AI Bot Interview Studio</h1>
              <span className="flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 bg-rose-500/10 border border-rose-500/20 text-rose-400 rounded-full">
                <span className="w-1.5 h-1.5 rounded-full bg-rose-500 animate-ping" /> LIVE
              </span>
            </div>
            <p className="text-[10px] text-slate-400">
              Topic: <span className="text-violet-400 font-semibold">{session?.topic || 'Technical Interview'}</span>
            </p>
          </div>
        </div>

        {/* Progress & Timer */}
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-300 bg-slate-950/60 px-3 py-1.5 rounded-xl border border-slate-800">
            <Clock className="w-3.5 h-3.5 text-violet-400" />
            <span>{formatTimer(elapsedSeconds)}</span>
          </div>

          <div className="flex items-center gap-2 text-xs font-semibold text-slate-300 bg-slate-950/60 px-3 py-1.5 rounded-xl border border-slate-800">
            <Sparkles className="w-3.5 h-3.5 text-fuchsia-400" />
            <span>Round {session?.questionCount || 1} of {session?.maxQuestions || 4}</span>
          </div>

          <button
            onClick={handleExit}
            className="p-2 hover:bg-slate-800 text-slate-400 hover:text-rose-400 rounded-xl transition-all cursor-pointer"
            title="Leave Room"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* MAIN DUAL-STAGE VIDEO/BOT VIEWPORT */}
      <main className="flex-1 max-w-6xl mx-auto w-full p-4 md:p-6 grid grid-cols-1 md:grid-cols-2 gap-4 items-stretch overflow-hidden">
        
        {/* CANDIDATE STAGE (LEFT) */}
        <div className="relative bg-slate-900 border border-slate-800 rounded-3xl overflow-hidden flex flex-col justify-between p-4 shadow-xl">
          {/* Candidate Top Label */}
          <div className="flex items-center justify-between z-10">
            <div className="flex items-center gap-2 bg-slate-950/70 backdrop-blur-md px-3 py-1.5 rounded-xl border border-slate-800">
              <User className="w-3.5 h-3.5 text-violet-400" />
              <span className="text-xs font-bold text-white">{user?.fullName || 'Candidate'}</span>
              <span className="text-[10px] text-slate-400">({session?.targetRole || 'Developer'})</span>
            </div>

            {/* Mic VU Indicator */}
            <div className="flex items-center gap-1.5 bg-slate-950/70 backdrop-blur-md px-3 py-1.5 rounded-xl border border-slate-800">
              <Mic className={`w-3.5 h-3.5 ${candidateAudioLevel > 15 ? 'text-emerald-400 animate-pulse' : 'text-slate-500'}`} />
              <div className="w-12 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-emerald-500 to-teal-400 transition-all duration-75"
                  style={{ width: `${Math.min(100, candidateAudioLevel * 2)}%` }}
                />
              </div>
            </div>
          </div>

          {/* Candidate Video Feed */}
          <div className="absolute inset-0 flex items-center justify-center bg-slate-950">
            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted
              className={`w-full h-full object-cover transform -scale-x-100 ${!cameraActive ? 'hidden' : ''}`}
            />
            {!cameraActive && (
              <div className="flex flex-col items-center justify-center text-slate-500 space-y-2">
                <div className="w-16 h-16 rounded-full bg-slate-800/80 flex items-center justify-center text-slate-400 text-xl font-bold">
                  {user?.fullName?.charAt(0) || 'C'}
                </div>
                <p className="text-xs font-semibold">Camera is Off (Audio Only)</p>
              </div>
            )}
          </div>

          {/* Candidate Bottom Media Controls */}
          <div className="flex items-center justify-center gap-3 z-10 mt-auto pt-4">
            <button
              onClick={toggleMic}
              className={`p-3 rounded-2xl transition-all cursor-pointer border ${
                micActive
                  ? 'bg-slate-950/80 border-slate-800 text-white hover:bg-slate-800'
                  : 'bg-rose-500/20 border-rose-500/40 text-rose-400'
              }`}
              title={micActive ? 'Mute Microphone' : 'Unmute Microphone'}
            >
              {micActive ? <Mic className="w-4 h-4" /> : <MicOff className="w-4 h-4" />}
            </button>

            <button
              onClick={toggleCamera}
              className={`p-3 rounded-2xl transition-all cursor-pointer border ${
                cameraActive
                  ? 'bg-slate-950/80 border-slate-800 text-white hover:bg-slate-800'
                  : 'bg-rose-500/20 border-rose-500/40 text-rose-400'
              }`}
              title={cameraActive ? 'Turn Off Camera' : 'Turn On Camera'}
            >
              {cameraActive ? <Video className="w-4 h-4" /> : <VideoOff className="w-4 h-4" />}
            </button>
          </div>
        </div>

        {/* AI INTERVIEWER STAGE (RIGHT) */}
        <div className="relative bg-gradient-to-b from-slate-900 to-slate-950 border border-slate-800 rounded-3xl overflow-hidden flex flex-col justify-between p-4 shadow-xl">
          {/* AI Header */}
          <div className="flex items-center justify-between z-10">
            <div className="flex items-center gap-2 bg-slate-950/70 backdrop-blur-md px-3 py-1.5 rounded-xl border border-slate-800">
              <Bot className="w-4 h-4 text-fuchsia-400" />
              <div>
                <p className="text-xs font-bold text-white">Alex</p>
                <p className="text-[10px] text-violet-400 font-bold uppercase tracking-wider">Lead Technical Interviewer</p>
              </div>
            </div>

            {/* AI Audio Mute Toggle */}
            <button
              onClick={() => {
                if (isSpeaking) stopSpeaking();
                setAiVoiceMuted(!aiVoiceMuted);
              }}
              className="p-2 hover:bg-slate-800 rounded-xl text-slate-400 hover:text-white transition-colors cursor-pointer"
              title={aiVoiceMuted ? 'Unmute AI Voice' : 'Mute AI Voice'}
            >
              {aiVoiceMuted ? <VolumeX className="w-4 h-4 text-rose-400" /> : <Volume2 className="w-4 h-4 text-emerald-400" />}
            </button>
          </div>

          {/* Central Voice Orb Canvas */}
          <div className="flex-1 flex flex-col items-center justify-center my-4 relative">
            <canvas
              ref={canvasRef}
              width={260}
              height={200}
              className="w-60 h-44 cursor-pointer"
              onClick={() => session?.currentQuestion && speakText(session.currentQuestion)}
              title="Click to repeat question audio"
            />

            {/* State Indicator Badge */}
            <div className="mt-2">
              {submitting ? (
                <span className="px-3 py-1 bg-amber-500/10 border border-amber-500/20 rounded-full text-amber-400 text-xs font-bold flex items-center gap-1.5 animate-pulse">
                  <Sparkles className="w-3.5 h-3.5" /> Evaluating your answer...
                </span>
              ) : isTranscribing ? (
                <span className="px-3 py-1 bg-fuchsia-500/20 border border-fuchsia-500/30 rounded-full text-fuchsia-300 text-xs font-bold flex items-center gap-1.5 animate-pulse">
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" /> Transcribing audio with AI...
                </span>
              ) : isSpeaking ? (
                <span className="px-3 py-1 bg-violet-500/20 border border-violet-500/30 rounded-full text-violet-300 text-xs font-bold flex items-center gap-1.5">
                  <Volume2 className="w-3.5 h-3.5 animate-pulse" /> Speaking question...
                </span>
              ) : isListening ? (
                <span className="px-3 py-1 bg-emerald-500/20 border border-emerald-500/30 rounded-full text-emerald-300 text-xs font-bold flex items-center gap-1.5 animate-pulse">
                  <Mic className="w-3.5 h-3.5" /> Listening to your answer ({formatTimer(recordingSeconds)})...
                </span>
              ) : (
                <span className="px-3 py-1 bg-slate-800/50 border border-slate-700/50 rounded-full text-slate-400 text-xs font-medium flex items-center gap-1.5">
                  Ready for your answer
                </span>
              )}
            </div>
          </div>

          {/* Quick Repeat Audio Button */}
          <div className="flex items-center justify-center">
            <button
              onClick={() => session?.currentQuestion && speakText(session.currentQuestion)}
              className="text-xs font-bold text-violet-400 hover:text-violet-300 flex items-center gap-1.5 px-3 py-1.5 rounded-xl hover:bg-violet-950/40 border border-transparent hover:border-violet-800/30 transition-all cursor-pointer"
            >
              <RefreshCw className="w-3.5 h-3.5" /> Repeat Question Audio
            </button>
          </div>
        </div>
      </main>

      {/* BOTTOM DIALOGUE & SPEECH INPUT DOCK */}
      <footer className="border-t border-slate-850 bg-slate-900/90 backdrop-blur-md p-4 md:p-6 z-20 space-y-4 max-w-5xl mx-auto w-full">
        
        {/* CURRENT QUESTION DISPLAY */}
        <div className="bg-slate-950/70 border border-slate-800/80 rounded-2xl p-4 md:p-5 shadow-lg relative">
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-black uppercase tracking-widest text-violet-400">Current Question</span>
                {lastTurnFeedback?.score != null && (
                  <span className="text-[10px] font-bold px-2 py-0.5 bg-emerald-950/60 border border-emerald-800/40 text-emerald-400 rounded-md">
                    Previous Turn Score: {Math.round(lastTurnFeedback.score)}%
                  </span>
                )}
              </div>
              <p className="text-sm md:text-base font-bold text-white leading-relaxed">
                {session?.currentQuestion || 'Loading interview question...'}
              </p>
            </div>
          </div>
        </div>

        {/* CANDIDATE SPEECH TRANSCRIPT / ANSWER BOX */}
        <div className="space-y-3">
          <div className="relative">
            <textarea
              rows={3}
              value={candidateAnswer + (interimTranscript ? ' ' + interimTranscript : '')}
              onChange={(e) => setCandidateAnswer(e.target.value)}
              placeholder={
                isListening
                  ? "🔴 Recording your voice... Speak your answer clearly (or click 'Stop Speaking' when done)..."
                  : isTranscribing
                  ? "✨ Transcribing your speech with AI..."
                  : "Speak your answer out loud (or type it here)..."
              }
              disabled={submitting || isTranscribing}
              className="w-full px-4 py-3 bg-slate-950 border border-slate-800 rounded-2xl text-white text-sm focus:border-violet-500 focus:outline-none resize-none leading-relaxed"
            />

            {/* Live Listening Badge */}
            {isListening && (
              <div className="absolute top-3 right-3 flex items-center gap-1.5 px-2.5 py-1 bg-emerald-500/20 border border-emerald-500/30 rounded-full text-emerald-400 text-[11px] font-bold animate-pulse pointer-events-none">
                <span className="w-2 h-2 rounded-full bg-emerald-400" />
                Recording ({formatTimer(recordingSeconds)})
              </div>
            )}
            {isTranscribing && (
              <div className="absolute top-3 right-3 flex items-center gap-1.5 px-2.5 py-1 bg-fuchsia-500/20 border border-fuchsia-500/30 rounded-full text-fuchsia-300 text-[11px] font-bold animate-pulse pointer-events-none">
                <RefreshCw className="w-3 h-3 animate-spin" /> AI Transcribing...
              </div>
            )}
          </div>

          {/* Action Buttons Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={toggleListening}
                disabled={submitting || isTranscribing}
                className={`px-5 py-2.5 rounded-xl font-bold text-xs flex items-center gap-2 transition-all cursor-pointer shadow-lg ${
                  isListening
                    ? 'bg-rose-600 hover:bg-rose-500 text-white shadow-rose-600/30 animate-pulse'
                    : isTranscribing
                    ? 'bg-amber-600 text-white shadow-amber-600/20'
                    : 'bg-gradient-to-r from-emerald-600 to-teal-600 hover:opacity-95 text-white shadow-emerald-600/20'
                }`}
              >
                {isListening ? (
                  <>
                    <Square className="w-3.5 h-3.5 fill-white" /> Stop Speaking ({formatTimer(recordingSeconds)})
                  </>
                ) : isTranscribing ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" /> Transcribing Audio...
                  </>
                ) : (
                  <>
                    <Mic className="w-4 h-4" /> 🎙️ Speak Answer
                  </>
                )}
              </button>

              {candidateAnswer && (
                <button
                  type="button"
                  onClick={() => { setCandidateAnswer(''); setInterimTranscript(''); }}
                  disabled={submitting || isTranscribing || isListening}
                  className="px-3 py-2 text-slate-500 hover:text-slate-300 text-xs font-bold transition-colors cursor-pointer"
                >
                  Clear
                </button>
              )}
            </div>

            {/* Submit Turn Button */}
            <button
              type="button"
              onClick={handleSubmitAnswer}
              disabled={submitting || isTranscribing || (!isListening && !candidateAnswer.trim())}
              className="px-6 py-2.5 bg-gradient-to-r from-violet-600 to-indigo-600 hover:opacity-95 disabled:opacity-40 disabled:cursor-not-allowed text-white rounded-xl font-black text-xs transition-all shadow-xl shadow-violet-600/20 flex items-center gap-2 cursor-pointer ml-auto"
            >
              {submitting ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" /> Evaluating...
                </>
              ) : isTranscribing ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" /> Transcribing...
                </>
              ) : (
                <>
                  <Send className="w-3.5 h-3.5" /> Done Speaking & Submit
                </>
              )}
            </button>
          </div>
        </div>

      </footer>

    </div>
  );
}
