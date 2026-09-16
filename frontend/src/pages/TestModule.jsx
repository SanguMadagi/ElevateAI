import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { testService } from '../services/api';
import { descriptorDistance, getFaceDescriptors, loadFaceModels, FACE_MATCH_THRESHOLD } from '../utils/faceRecognition';
import { Clock, AlertCircle, ListOrdered, FileText, ChevronRight, ChevronLeft, Loader2, Mic, Camera, Eye, UserRound, ShieldCheck, CheckCircle2, Sparkles } from 'lucide-react';
import toast from 'react-hot-toast';

const TestModule = () => {
  const { testId } = useParams();
  const navigate = useNavigate();

  // Primary States
  const [testData, setTestData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [currentRound, setCurrentRound] = useState('MCQ'); // MCQ | SCENARIO | PROJECT
  const [currentIndex, setCurrentIndex] = useState(0);

  const [answers, setAnswers] = useState({}); // questionId -> text answer or radio answer
  const [skippedQuestions, setSkippedQuestions] = useState({}); // questionId -> true
  const [timeLeft, setTimeLeft] = useState(60 * 60); // 60 minutes in seconds
  const [violations, setViolations] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showExitModal, setShowExitModal] = useState(false);
  const [showSubmitModal, setShowSubmitModal] = useState(false);
  const [isExiting, setIsExiting] = useState(false);

  // Fullscreen States
  const [showFullscreenOverlay, setShowFullscreenOverlay] = useState(false);
  const [showFullscreenWarningModal, setShowFullscreenWarningModal] = useState(false);

  const isSubmittingRef = useRef(isSubmitting);
  isSubmittingRef.current = isSubmitting;
  const isExitingRef = useRef(isExiting);
  isExitingRef.current = isExiting;

  // Voice States
  const [isListening, setIsListening] = useState(false);
  const [isTranscribing, setIsTranscribing] = useState(false);
  const [voiceStatus, setVoiceStatus] = useState('IDLE'); // IDLE | LISTENING | TRANSCRIBING | STOPPED | ERROR
  const recognitionRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const audioChunksRef = useRef([]);
  const audioStreamRef = useRef(null);
  const webSpeechCapturedRef = useRef(false);
  const activeQuestionIdRef = useRef(null);

  const [cameraStream, setCameraStream] = useState(null);
  const [cameraStatus, setCameraStatus] = useState('Starting');
  const [faceStatus, setFaceStatus] = useState('Checking');
  const [identityStatus, setIdentityStatus] = useState('Enrolled');
  const [identityMismatchSince, setIdentityMismatchSince] = useState(null);
  const [attentionStatus, setAttentionStatus] = useState('Normal');
  const [cameraWarning, setCameraWarning] = useState('');
  const videoRef = useRef(null);
  const cameraStreamRef = useRef(null);
  const noFaceSinceRef = useRef(null);
  const faceSampleRef = useRef({ noFace: 0, multiple: 0, mismatch: 0, processing: false });

  const hasVoiceSupport = typeof window !== 'undefined' && (
    !!(window.SpeechRecognition || window.webkitSpeechRecognition) ||
    !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
  );

  const enterFullscreen = async () => {
    try {
      if (document.documentElement.requestFullscreen) {
        await document.documentElement.requestFullscreen();
        setShowFullscreenOverlay(false);
        setShowFullscreenWarningModal(false);
      } else {
        setShowFullscreenOverlay(true);
      }
    } catch (err) {
      console.warn('Fullscreen request failed:', err);
      setShowFullscreenOverlay(true);
    }
  };

  // Navigation helpers (Declared early to prevent ReferenceError in hooks)
  const getRoundQuestions = () => {
    if (!testData) return [];
    if (currentRound === 'MCQ') return testData.mcqQuestions || [];
    if (currentRound === 'SCENARIO') return testData.scenarioQuestions || [];
    if (currentRound === 'PROJECT') return testData.projectQuestions || [];
    return [];
  };

  const questions = getRoundQuestions();
  const currentQuestion = questions[currentIndex];
  const isFinalQuestion = currentRound === 'PROJECT' && questions.length > 0 && currentIndex === (questions.length - 1);

  const timeLeftRef = useRef(timeLeft);
  useEffect(() => {
    timeLeftRef.current = timeLeft;
  }, [timeLeft]);

  // Load test data on mount
  useEffect(() => {
    const fetchTestData = async () => {
      try {
        const { data } = await testService.getTestSession(testId);
        setTestData(data);
        setIdentityStatus(data.identityVerificationStatus === 'ENROLLED' ? 'Enrolled' : 'Unavailable');

        // Prepopulate saved answers
        const initialAnswers = {};
        if (data.mcqAnswers) Object.assign(initialAnswers, data.mcqAnswers);
        if (data.scenarioAnswers) Object.assign(initialAnswers, data.scenarioAnswers);
        if (data.projectAnswers) Object.assign(initialAnswers, data.projectAnswers);
        setAnswers(initialAnswers);

        // Prepopulate skipped questions
        const initialSkipped = {};
        if (data.answers) {
          Object.keys(data.answers).forEach((qid) => {
            if (data.answers[qid].status === 'SKIPPED') {
              initialSkipped[qid] = true;
            }
          });
        }
        setSkippedQuestions(initialSkipped);

        // Restore state from localStorage if it exists
        const saved = localStorage.getItem(`test_${testId}_state`);
        if (saved) {
          try {
            const state = JSON.parse(saved);
            if (state.answers) setAnswers(prev => ({ ...prev, ...state.answers }));
            if (state.skippedQuestions) setSkippedQuestions(prev => ({ ...prev, ...state.skippedQuestions }));
            if (state.timeLeft) setTimeLeft(state.timeLeft);
            if (state.currentRound) setCurrentRound(state.currentRound);
            if (state.currentIndex !== undefined) setCurrentIndex(state.currentIndex);
          } catch (e) {
            console.error('Error recovering saved state', e);
          }
        }

        // Verify if we are already in fullscreen (entered via user gesture on Start page)
        if (!document.fullscreenElement) {
          setShowFullscreenOverlay(true);
        } else {
          setShowFullscreenOverlay(false);
        }
      } catch (err) {
        console.error(err);
        toast.error('Failed to load assessment session details.');
        navigate('/dashboard');
      } finally {
        setLoading(false);
      }
    };
    fetchTestData();
  }, [testId, navigate]);

  useEffect(() => {
    if (!testData || !navigator.mediaDevices?.getUserMedia) {
      setCameraStatus('Unavailable');
      setCameraWarning('Your browser does not provide camera access. Please use a supported browser to continue.');
      return undefined;
    }

    let stream;
    let faceTimer;
    let stopped = false;
    const startCamera = async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false });
        if (stopped) {
          stream.getTracks().forEach(track => track.stop());
          return;
        }
        cameraStreamRef.current = stream;
        setCameraStream(stream);
        stream.getVideoTracks()[0]?.addEventListener('ended', () => {
          setCameraStatus('Unavailable');
          setCameraWarning('Your camera is currently disabled or unavailable. Please enable your camera to continue.');
          testService.recordIdentityCheck(testId, 'CAMERA_DISCONNECTED', 0).catch(() => {});
        });
        try {
          await loadFaceModels();
          faceTimer = window.setInterval(async () => {
            if (!videoRef.current || videoRef.current.readyState < 2) return;
            if (faceSampleRef.current.processing) return;
            faceSampleRef.current.processing = true;
            try {
              const faces = await getFaceDescriptors(videoRef.current, 3);
              if (faces.length === 1) {
                const enrolled = JSON.parse(sessionStorage.getItem(`assessment_identity_${testId}`) || 'null');
                const distance = enrolled ? descriptorDistance(enrolled, Array.from(faces[0].descriptor)) : Number.POSITIVE_INFINITY;
                const matches = distance <= FACE_MATCH_THRESHOLD;
                faceSampleRef.current.noFace = 0;
                faceSampleRef.current.multiple = 0;
                faceSampleRef.current.mismatch = matches ? 0 : faceSampleRef.current.mismatch + 1;
                setFaceStatus('Detected');
                setIdentityStatus(matches ? 'Verified' : faceSampleRef.current.mismatch >= 2 ? 'Mismatch' : 'Checking');
                setIdentityMismatchSince(previous => matches ? null : (previous || Date.now()));
                noFaceSinceRef.current = null;
              } else if (faces.length > 1) {
                faceSampleRef.current.noFace = 0;
                faceSampleRef.current.multiple += 1;
                setFaceStatus(faceSampleRef.current.multiple >= 2 ? 'Multiple faces' : 'Detected');
                setIdentityStatus(faceSampleRef.current.multiple >= 2 ? 'Unavailable' : 'Checking');
                setIdentityMismatchSince(null);
                noFaceSinceRef.current = null;
              } else {
                faceSampleRef.current.multiple = 0;
                faceSampleRef.current.noFace += 1;
                if (!noFaceSinceRef.current) noFaceSinceRef.current = Date.now();
                setFaceStatus(faceSampleRef.current.noFace >= 2 ? 'Not detected' : 'Detected');
                setIdentityStatus(faceSampleRef.current.noFace >= 2 ? 'Unavailable' : 'Verified');
              }
            } catch (error) {
              setFaceStatus('Unavailable');
            } finally {
              faceSampleRef.current.processing = false;
            }
          }, 3000);
        } catch (error) {
          setFaceStatus('Browser unsupported');
          setIdentityStatus('Unavailable');
        }
      } catch (error) {
        setCameraStatus(error.name === 'NotAllowedError' ? 'Permission denied' : 'Unavailable');
        setCameraWarning('Camera access is required. Please allow camera permission and retry the assessment.');
      }
    };
    startCamera();
    return () => {
      stopped = true;
      if (faceTimer) window.clearInterval(faceTimer);
      stream?.getTracks().forEach(track => track.stop());
      setCameraStream(null);
    };
  }, [testData, testId]);

  useEffect(() => {
    if (!cameraStream || !videoRef.current) return undefined;
    const video = videoRef.current;
    const stream = cameraStream;
    video.srcObject = stream;
    const startPlayback = async () => {
      try {
        await video.play();
        const track = stream.getVideoTracks()[0];
        const working = track?.readyState === 'live' && video.videoWidth > 0 && video.videoHeight > 0;
        console.debug('Camera stream:', stream);
        console.debug('Video tracks:', stream.getVideoTracks());
        console.debug('Video readyState:', video.readyState);
        console.debug('Video paused:', video.paused);
        console.debug('Video dimensions:', video.videoWidth, video.videoHeight);
        if (!working) throw new Error('Camera video has no rendered dimensions');
        setCameraStatus('Active');
        setCameraWarning('');
      } catch (error) {
        setCameraStatus('Unavailable');
        setCameraWarning('Camera is permitted but the live preview could not start. Please retry.');
      }
    };
    if (video.readyState >= 1) startPlayback();
    else video.addEventListener('loadedmetadata', startPlayback, { once: true });
    return () => video.removeEventListener('loadedmetadata', startPlayback);
  }, [cameraStream]);

  useEffect(() => {
    if (!testData || !cameraStream || faceStatus === 'Checking' || faceStatus === 'Browser unsupported' || faceStatus === 'Unavailable' || identityStatus === 'Checking') return undefined;
    const status = faceStatus === 'Detected' ? (identityStatus === 'Verified' ? 'IDENTITY_VERIFIED' : 'IDENTITY_MISMATCH') : faceStatus === 'Multiple faces' ? 'MULTIPLE_FACES' : 'NO_FACE';
    testService.recordIdentityCheck(testId, status, faceStatus === 'Detected' ? 1 : faceStatus === 'Multiple faces' ? 2 : 0).catch(() => {});
    const interval = window.setInterval(() => {
      testService.recordIdentityCheck(testId, status, faceStatus === 'Detected' ? 1 : faceStatus === 'Multiple faces' ? 2 : 0).catch(() => {});
    }, 10000);
    return () => window.clearInterval(interval);
  }, [testData, cameraStream, faceStatus, testId]);

  useEffect(() => {
    const handleAttention = () => setAttentionStatus(document.hidden ? 'Please refocus' : 'Normal');
    document.addEventListener('visibilitychange', handleAttention);
    return () => document.removeEventListener('visibilitychange', handleAttention);
  }, []);

  useEffect(() => {
    if (faceStatus === 'Not detected') {
      toast.error('No face detected. Please remain visible in front of the camera.', { id: 'face-warning' });
    }
    if (faceStatus === 'Multiple faces') {
      toast.error('Multiple faces detected. Only the registered candidate may remain in view.', { id: 'face-warning' });
    }
    if (identityStatus === 'Mismatch' && identityMismatchSince && Date.now() - identityMismatchSince > 6000) {
      toast.error('Identity could not be verified. Please ensure the registered candidate is taking the assessment.', { id: 'identity-warning' });
    }
  }, [faceStatus, identityStatus, identityMismatchSince]);

  // Countdown timer logic
  useEffect(() => {
    const interval = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          handleAutoSubmit();
          return 0;
        }

        // Save progress locally every 30 seconds
        if (prev % 30 === 0) {
          saveStateToLocalStorage(prev - 1);
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [testData]);

  const saveStateToLocalStorage = (currentSecondsLeft) => {
    const state = {
      answers,
      skippedQuestions,
      timeLeft: currentSecondsLeft,
      currentRound,
      currentIndex
    };
    localStorage.setItem(`test_${testId}_state`, JSON.stringify(state));
  };

  // Auto-submit when time expires
  const handleAutoSubmit = async () => {
    toast.error('Time expired! Submitting your assessment...', { duration: 5000 });
    try {
      await testService.submitTest(testId);
      localStorage.removeItem(`test_${testId}_state`);
      navigate(`/result/${testId}`);
    } catch (err) {
      console.error(err);
      navigate(`/result/${testId}`);
    }
  };

  // Anti-Cheat Monitoring
  useEffect(() => {
    const recordViolation = async (type, severity, description) => {
      const v = { type, severity, description, timestamp: new Date().toISOString() };
      setViolations((prev) => [...prev, v]);
      toast.error(`Security Warning: ${description}`, {
        style: {
          background: '#7f1d1d',
          color: '#ffffff',
          border: '1px solid #ef4444'
        }
      });
      try {
        await testService.submitViolation(testId, v);
      } catch (err) {
        console.error('Failed to report security violation', err);
      }
    };

    const onVisibilityChange = () => {
      if (document.hidden) {
        recordViolation('TAB_SWITCH', 'HIGH', 'Candidate switched browser tab');
      }
    };

    const onCopy = (e) => {
      e.preventDefault();
      recordViolation('COPY_PASTE', 'MEDIUM', 'Candidate attempted to copy content');
    };

    const onPaste = (e) => {
      e.preventDefault();
      recordViolation('COPY_PASTE', 'MEDIUM', 'Candidate attempted to paste content');
    };

    const onContextMenu = (e) => {
      e.preventDefault();
      recordViolation('RIGHT_CLICK', 'LOW', 'Right-click menu attempted');
    };

    const onFullscreenChange = () => {
      if (!document.fullscreenElement) {
        if (!isSubmittingRef.current && !isExitingRef.current) {
          setShowFullscreenWarningModal(true);
          recordViolation('FULLSCREEN_EXIT', 'HIGH', 'Candidate exited fullscreen mode');
        }
      }
    };

    document.addEventListener('visibilitychange', onVisibilityChange);
    document.addEventListener('copy', onCopy);
    document.addEventListener('paste', onPaste);
    document.addEventListener('contextmenu', onContextMenu);
    document.addEventListener('fullscreenchange', onFullscreenChange);

    return () => {
      document.removeEventListener('visibilitychange', onVisibilityChange);
      document.removeEventListener('copy', onCopy);
      document.removeEventListener('paste', onPaste);
      document.removeEventListener('contextmenu', onContextMenu);
      document.removeEventListener('fullscreenchange', onFullscreenChange);
    };
  }, [testId]);

  // Save text/option answer API call
  const saveAnswerToApi = async (questionId, questionType, val, status = 'ANSWERED') => {
    try {
      await testService.saveAnswer(testId, {
        questionId,
        questionType,
        answer: val,
        status,
        timeSpentSeconds: 10 // approximate
      });
    } catch (err) {
      console.error('Failed to sync answer with server:', err);
    }
  };

  // MCQ Selection handler
  const handleSelectMcq = (questionId, option) => {
    setSkippedQuestions((prev) => {
      const updated = { ...prev };
      delete updated[questionId];
      return updated;
    });
    setAnswers((prev) => {
      const updated = { ...prev, [questionId]: option };
      saveAnswerToApi(questionId, 'MCQ', option, 'ANSWERED');
      return updated;
    });
  };

  // Scenario & Project blur handler (auto-saves)
  const handleTextareaBlur = (questionId, questionType, val) => {
    saveAnswerToApi(questionId, questionType, val, 'ANSWERED');
  };

  const handleTextareaChange = (questionId, val) => {
    setSkippedQuestions((prev) => {
      const updated = { ...prev };
      delete updated[questionId];
      return updated;
    });
    setAnswers((prev) => ({ ...prev, [questionId]: val }));
  };

  // Voice Speech Recognition Logic (Dual-Engine: Real-time WebSpeech + Gemini AI Audio Fallback)
  const currentQuestionRef = useRef(currentQuestion);
  const currentRoundRef = useRef(currentRound);

  // Keep refs up-to-date to prevent stale closures in event listeners
  useEffect(() => {
    currentQuestionRef.current = currentQuestion;
  }, [currentQuestion]);

  useEffect(() => {
    currentRoundRef.current = currentRound;
  }, [currentRound]);

  // Helper to safely stop all active voice capture streams
  const stopVoiceCapture = () => {
    if (recognitionRef.current) {
      try {
        recognitionRef.current.onstart = null;
        recognitionRef.current.onresult = null;
        recognitionRef.current.onerror = null;
        recognitionRef.current.onend = null;
        recognitionRef.current.stop();
      } catch (e) {
        // ignore
      }
      recognitionRef.current = null;
    }

    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      try {
        mediaRecorderRef.current.stop();
      } catch (e) {
        // ignore
      }
    }

    if (audioStreamRef.current) {
      try {
        audioStreamRef.current.getTracks().forEach((track) => track.stop());
      } catch (e) {
        // ignore
      }
      audioStreamRef.current = null;
    }

    setIsListening(false);
    setVoiceStatus('STOPPED');
  };

  // Clean up listening when question changes
  useEffect(() => {
    stopVoiceCapture();
  }, [currentIndex, currentRound]);

  // Clean up on unmount
  useEffect(() => {
    return () => {
      stopVoiceCapture();
    };
  }, []);

  const toggleListening = async () => {
    if (isTranscribing) {
      return;
    }

    // If currently recording/listening, user wants to STOP and process
    if (isListening) {
      setIsListening(false);
      setVoiceStatus('STOPPED');

      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop();
        } catch (e) {
          // ignore
        }
      }

      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
        try {
          mediaRecorderRef.current.stop();
        } catch (e) {
          console.warn('Error stopping mediaRecorder:', e);
        }
      }
      return;
    }

    // Otherwise, START voice recording
    if (!navigator.mediaDevices?.getUserMedia) {
      toast.error('Voice input is not supported in this browser. Please use a modern browser.');
      return;
    }

    const qId = currentQuestion?.id;
    const qRound = currentRound;
    if (!qId) return;

    activeQuestionIdRef.current = qId;
    webSpeechCapturedRef.current = false;
    audioChunksRef.current = [];

    try {
      // 1. Acquire mic stream
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      audioStreamRef.current = stream;

      // 2. Setup MediaRecorder with best supported mimeType
      const mimeTypes = [
        'audio/webm;codecs=opus',
        'audio/webm',
        'audio/ogg;codecs=opus',
        'audio/ogg',
        'audio/mp4',
        'audio/aac',
      ];
      let selectedMime = '';
      if (typeof MediaRecorder !== 'undefined') {
        for (const type of mimeTypes) {
          if (MediaRecorder.isTypeSupported(type)) {
            selectedMime = type;
            break;
          }
        }
      }

      const recorder = selectedMime
        ? new MediaRecorder(stream, { mimeType: selectedMime })
        : new MediaRecorder(stream);

      recorder.ondataavailable = (event) => {
        if (event.data && event.data.size > 0) {
          audioChunksRef.current.push(event.data);
        }
      };

      recorder.onstop = async () => {
        // Release hardware mic track
        stream.getTracks().forEach((track) => track.stop());
        audioStreamRef.current = null;

        // If WebSpeech already captured text, user is all set
        if (webSpeechCapturedRef.current) {
          toast.success('Voice transcribed successfully!', { id: 'voice-toast' });
          return;
        }

        // Fallback: Transcribe via backend Gemini AI endpoint
        const recordedBlob = new Blob(audioChunksRef.current, {
          type: recorder.mimeType || 'audio/webm',
        });

        if (recordedBlob.size > 1500 && activeQuestionIdRef.current) {
          const targetQId = activeQuestionIdRef.current;
          try {
            setIsTranscribing(true);
            setVoiceStatus('TRANSCRIBING');
            toast.loading('AI is transcribing your speech...', { id: 'voice-transcribe-toast' });

            const { data } = await testService.transcribeAudio(recordedBlob);
            const transcript = data?.transcript?.trim();

            if (transcript) {
              setAnswers((prev) => {
                const currentText = prev[targetQId] || '';
                const space = currentText && !currentText.endsWith(' ') ? ' ' : '';
                const updatedText = currentText + space + transcript;

                saveAnswerToApi(targetQId, qRound, updatedText, 'ANSWERED');
                return { ...prev, [targetQId]: updatedText };
              });

              setSkippedQuestions((prev) => {
                const updated = { ...prev };
                delete updated[targetQId];
                return updated;
              });

              toast.success('Voice transcribed by AI!', { id: 'voice-transcribe-toast' });
            } else {
              toast.dismiss('voice-transcribe-toast');
              toast('No speech detected. You can speak again or type your answer.', {
                icon: '🎙️',
                id: 'voice-no-speech',
              });
            }
          } catch (err) {
            console.error('Failed to transcribe audio via AI:', err);
            toast.error('Could not transcribe audio. Please type your answer or retry.', {
              id: 'voice-transcribe-toast',
            });
          } finally {
            setIsTranscribing(false);
            setVoiceStatus('IDLE');
          }
        } else if (recordedBlob.size <= 1500) {
          toast('Recording was too short. Please speak clearly into your mic.', {
            icon: '🎙️',
            id: 'voice-too-short',
          });
          setVoiceStatus('IDLE');
        }
      };

      recorder.start(250);
      mediaRecorderRef.current = recorder;

      setIsListening(true);
      setVoiceStatus('LISTENING');

      // 3. Start real-time Web Speech in parallel if available
      const SpeechRecClass = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (SpeechRecClass) {
        try {
          const rec = new SpeechRecClass();
          rec.continuous = true;
          rec.interimResults = true;
          rec.lang = navigator.language || 'en-US';

          rec.onresult = (event) => {
            let finalTranscript = '';
            for (let i = event.resultIndex; i < event.results.length; ++i) {
              if (event.results[i].isFinal) {
                finalTranscript += event.results[i][0].transcript;
              }
            }

            if (finalTranscript && activeQuestionIdRef.current) {
              webSpeechCapturedRef.current = true;
              const targetQId = activeQuestionIdRef.current;

              setAnswers((prev) => {
                const currentText = prev[targetQId] || '';
                const space = currentText && !currentText.endsWith(' ') ? ' ' : '';
                const updatedText = currentText + space + finalTranscript;

                saveAnswerToApi(targetQId, qRound, updatedText, 'ANSWERED');
                return { ...prev, [targetQId]: updatedText };
              });

              setSkippedQuestions((prev) => {
                const updated = { ...prev };
                delete updated[targetQId];
                return updated;
              });
            }
          };

          rec.onerror = (event) => {
            console.warn('WebSpeech event error:', event.error);
            // In Brave or privacy browsers, network error is expected; MediaRecorder handles it smoothly
            if (event.error === 'not-allowed' || event.error === 'permission-denied') {
              toast.error('Microphone permission denied. Please check browser settings.', { id: 'voice-perm-err' });
              stopVoiceCapture();
            }
          };

          rec.start();
          recognitionRef.current = rec;
        } catch (speechErr) {
          console.info('WebSpeech not activated, recording via MediaRecorder for AI transcription:', speechErr);
        }
      }

      toast.success('Recording active. Speak into your mic! (Click to finish)', { id: 'voice-toast' });
    } catch (err) {
      console.error('Error starting voice recording:', err);
      setIsListening(false);
      setVoiceStatus('ERROR');

      if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
        toast.error('Microphone permission denied. Please allow microphone access in your browser settings.', {
          id: 'voice-perm-err',
        });
      } else if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
        toast.error('No microphone detected on your device.', { id: 'voice-no-mic-err' });
      } else {
        toast.error('Could not access microphone: ' + (err.message || 'Please check mic settings.'));
      }
    }
  };

  // Exit assessment flow
  const handleExitAssessment = async () => {
    setIsExiting(true);
    try {
      // First save the current question progress if there's any active text / answer
      if (currentQuestion) {
        const questionId = currentQuestion.id;
        const questionType = currentRound;
        const answer = answers[questionId] || '';
        
        await testService.saveAnswer(testId, {
          questionId,
          questionType,
          answer,
          timeSpentSeconds: 10,
          status: isQuestionAnswered(questionId, questionType) ? 'ANSWERED' : 'SKIPPED'
        });
      }

      await testService.exitTest(testId);
      localStorage.removeItem(`test_${testId}_state`);
      // Exit fullscreen before navigate
      if (document.fullscreenElement && document.exitFullscreen) {
        document.exitFullscreen().catch(() => {});
      }
      toast.success('Assessment exited successfully.');
      navigate('/dashboard');
    } catch (err) {
      console.error(err);
      toast.error('Failed to exit assessment cleanly.');
    } finally {
      setIsExiting(false);
      setShowExitModal(false);
    }
  };

  // Skip Question flow
  const handleSkipQuestion = async () => {
    if (!currentQuestion) return;
    const qid = currentQuestion.id;
    const qType = currentRound;
    
    try {
      // Send skip state to backend
      await testService.saveAnswer(testId, {
        questionId: qid,
        questionType: qType,
        answer: answers[qid] || '',
        status: 'SKIPPED',
        timeSpentSeconds: 10 // approximate
      });
      
      // Update local states
      setSkippedQuestions((prev) => ({ ...prev, [qid]: true }));
      
      toast.success('Question marked as skipped.');
      
      // Navigate correctly to the next question
      handleNext();
    } catch (err) {
      console.error(err);
      toast.error('Failed to skip question.');
    }
  };

  // Question count helpers
  const getTotalQuestionCount = () => {
    if (!testData) return 10;
    return (testData.mcqQuestions?.length || 0) +
           (testData.scenarioQuestions?.length || 0) +
           (testData.projectQuestions?.length || 0);
  };

  const getAnsweredCount = () => {
    return Object.keys(answers).filter(k => answers[k] && String(answers[k]).trim().length > 0).length;
  };

  // Submit assessment flow
  const openSubmitConfirmation = () => {
    if (isSubmittingRef.current) return;
    setShowSubmitModal(true);
  };

  const handleConfirmSubmit = async () => {
    if (isSubmittingRef.current) return;
    setIsSubmitting(true);
    setShowSubmitModal(false);

    try {
      // Flush current question answer if any
      if (currentQuestion) {
        const questionId = currentQuestion.id;
        const questionType = currentRound;
        const answer = answers[questionId] || '';
        await testService.saveAnswer(testId, {
          questionId,
          questionType,
          answer,
          timeSpentSeconds: 10,
          status: isQuestionAnswered(questionId, questionType) ? 'ANSWERED' : 'SKIPPED'
        }).catch(() => {});
      }

      await testService.submitTest(testId);
      localStorage.removeItem(`test_${testId}_state`);
      // Exit fullscreen before navigate
      if (document.fullscreenElement && document.exitFullscreen) {
        document.exitFullscreen().catch(() => {});
      }
      toast.success('Assessment submitted! AI evaluation in progress.');
      navigate(`/result/${testId}`);
    } catch (err) {
      console.error('Submit error:', err);
      // If error indicates already submitted or in progress, navigate to results anyway
      if (err?.response?.status === 202 || err?.response?.status === 400) {
        localStorage.removeItem(`test_${testId}_state`);
        navigate(`/result/${testId}`);
        return;
      }
      toast.error('Submission failed. Please verify your connection and try again.');
      setIsSubmitting(false);
    }
  };

  // Navigation helpers

  const handleNext = () => {
    if (currentIndex < questions.length - 1) {
      setCurrentIndex((prev) => prev + 1);
    } else {
      // Transition to next round
      if (currentRound === 'MCQ') {
        setCurrentRound('SCENARIO');
        setCurrentIndex(0);
      } else if (currentRound === 'SCENARIO') {
        setCurrentRound('PROJECT');
        setCurrentIndex(0);
      }
    }
  };

  const handlePrev = () => {
    if (currentIndex > 0) {
      setCurrentIndex((prev) => prev - 1);
    } else {
      // Transition to previous round
      if (currentRound === 'SCENARIO') {
        setCurrentRound('MCQ');
        setCurrentIndex(testData.mcqQuestions.length - 1);
      } else if (currentRound === 'PROJECT') {
        setCurrentRound('SCENARIO');
        setCurrentIndex(testData.scenarioQuestions.length - 1);
      }
    }
  };

  // Check if question is answered
  const isQuestionAnswered = (qid, type) => {
    return !!answers[qid];
  };

  // Formatting remaining time
  const formatTime = (seconds) => {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    return `${hrs.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // Word count calculators
  const getWordCount = (str) => {
    if (!str) return 0;
    return str.trim().split(/\s+/).filter(Boolean).length;
  };

  const getQuestionButtonClass = (qid, type, idx) => {
    const isCurrent = currentRound === type && currentIndex === idx;
    const isAnswered = isQuestionAnswered(qid, type);
    const isSkipped = !!skippedQuestions[qid];

    let baseClass = "h-10 rounded-xl font-bold text-xs flex items-center justify-center border transition-all cursor-pointer ";
    if (type === 'MCQ') {
      baseClass = "w-10 " + baseClass;
    } else {
      baseClass = "w-full " + baseClass;
    }

    if (isCurrent) {
      return baseClass + "bg-violet-600 border-violet-500 text-white shadow-md shadow-violet-900/20 scale-105 ring-2 ring-violet-500/20";
    }

    if (isAnswered) {
      return baseClass + "bg-emerald-950/40 border-emerald-500 text-emerald-400 hover:bg-emerald-950/60";
    }

    if (isSkipped) {
      return baseClass + "bg-amber-950/40 border-amber-500 text-amber-400 hover:bg-amber-950/60";
    }

    return baseClass + "bg-slate-950/40 border-slate-850 text-slate-500 hover:border-slate-700 hover:text-slate-400";
  };

  if (loading || !testData) {
    return (
      <div className="min-h-screen bg-slate-950 text-white flex flex-col items-center justify-center">
        <Loader2 className="w-12 h-12 animate-spin text-violet-500 mb-4" />
        <p className="text-slate-400 font-medium">Bootstrapping assessment sandbox...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col overflow-hidden select-none">
      {/* Assessment Header */}
      <header className="bg-slate-900 border-b border-slate-850 px-6 py-4 flex items-center justify-between shadow-md z-10">
        <div className="flex items-center gap-3">
          <span className="font-black tracking-wider text-lg uppercase bg-gradient-to-r from-violet-400 to-fuchsia-400 bg-clip-text text-transparent">
            AI Interview Platform
          </span>
          <span className="bg-slate-850 border border-slate-800 text-slate-400 text-xs px-3 py-1.5 rounded-xl font-bold uppercase tracking-wider">
            Round: {currentRound} ({currentIndex + 1}/{questions.length})
          </span>
          <div className="flex items-center gap-2 bg-slate-950/70 border border-slate-800 rounded-xl px-2 py-1.5">
            <video ref={videoRef} autoPlay muted playsInline className="w-16 h-10 rounded-lg object-cover bg-slate-950" />
            <div className="text-[10px] font-bold leading-4">
              <div className={cameraStatus === 'Active' ? 'text-emerald-400' : 'text-rose-400'}><Camera className="w-3 h-3 inline mr-1" />Camera: {cameraStatus}</div>
              <div className={faceStatus === 'Detected' ? 'text-emerald-400' : 'text-amber-400'}><UserRound className="w-3 h-3 inline mr-1" />Face: {faceStatus}</div>
                <div className={identityStatus === 'Enrolled' ? 'text-emerald-400' : 'text-amber-400'}><ShieldCheck className="w-3 h-3 inline mr-1" />Identity: {identityStatus}</div>
              <div className={attentionStatus === 'Normal' ? 'text-slate-400' : 'text-amber-400'}><Eye className="w-3 h-3 inline mr-1" />Attention: {attentionStatus}</div>
            </div>
          </div>
          {violations.length > 0 && (
            <span className="bg-rose-950/60 border border-rose-800/40 text-rose-400 text-xs px-3 py-1.5 rounded-xl font-black animate-pulse flex items-center gap-1.5">
              ⚠️ {violations.length} violations
            </span>
          )}
        </div>

        <div className="flex items-center gap-6">
          <div className={`flex items-center gap-2 font-black text-xl tracking-widest ${timeLeft < 300 ? 'text-red-500 animate-pulse' : 'text-violet-400'}`}>
            <Clock className="w-5 h-5" />
            <span>{formatTime(timeLeft)}</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setShowExitModal(true)}
              className="px-5 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-300 font-bold text-sm rounded-xl border border-slate-700 transition-all tracking-wider uppercase cursor-pointer"
            >
              Exit Assessment
            </button>
            <button
              onClick={openSubmitConfirmation}
              disabled={isSubmitting}
              className="px-6 py-2.5 bg-gradient-to-r from-violet-600 to-fuchsia-600 hover:opacity-95 text-white font-black text-sm rounded-xl tracking-wider uppercase transition-all shadow-md cursor-pointer disabled:opacity-50"
            >
              {isSubmitting ? 'Submitting...' : 'Submit Test'}
            </button>
          </div>
        </div>
      </header>

      {cameraWarning && (
        <div className="fixed inset-0 bg-slate-950/85 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-slate-900 border-2 border-rose-500 rounded-3xl max-w-md w-full p-6 space-y-5 shadow-2xl">
            <div className="flex items-center gap-3 text-rose-400"><Camera className="w-7 h-7" /><h3 className="text-lg font-black">Camera Required</h3></div>
            <p className="text-slate-300 text-sm leading-relaxed">{cameraWarning}</p>
            <button onClick={() => window.location.reload()} className="w-full py-3 bg-rose-600 hover:bg-rose-500 text-white rounded-xl font-black text-sm">Retry Camera</button>
          </div>
        </div>
      )}

      {/* Main Sandbox Layout */}
      <main className="flex-1 flex overflow-hidden">
        {/* Left Navigator Panel */}
        <aside className="w-80 bg-slate-900/40 border-r border-slate-900 p-6 flex flex-col justify-between overflow-y-auto">
          <div className="space-y-8">
            {/* MCQ List */}
            <div className="space-y-3">
              <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
                <ListOrdered className="w-4 h-4 text-violet-400" /> MCQ Round ({testData.mcqQuestions?.length || 0})
              </h4>
              <div className="grid grid-cols-5 gap-2">
                {testData.mcqQuestions?.map((q, idx) => (
                  <button
                    key={q.id}
                    onClick={() => {
                      setCurrentRound('MCQ');
                      setCurrentIndex(idx);
                    }}
                    className={getQuestionButtonClass(q.id, 'MCQ', idx)}
                  >
                    {idx + 1}
                  </button>
                ))}
              </div>
            </div>

            {/* Scenario List */}
            <div className="space-y-3">
              <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
                <FileText className="w-4 h-4 text-fuchsia-400" /> Scenario Round ({testData.scenarioQuestions?.length || 0})
              </h4>
              <div className="grid grid-cols-3 gap-2">
                {testData.scenarioQuestions?.map((q, idx) => (
                  <button
                    key={q.id}
                    onClick={() => {
                      setCurrentRound('SCENARIO');
                      setCurrentIndex(idx);
                    }}
                    className={getQuestionButtonClass(q.id, 'SCENARIO', idx)}
                  >
                    S{idx + 1}
                  </button>
                ))}
              </div>
            </div>

            {/* Project List */}
            <div className="space-y-3">
              <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
                <FileText className="w-4 h-4 text-emerald-400" /> Project Round ({testData.projectQuestions?.length || 0})
              </h4>
              <div className="grid grid-cols-3 gap-2">
                {testData.projectQuestions?.map((q, idx) => (
                  <button
                    key={q.id}
                    onClick={() => {
                      setCurrentRound('PROJECT');
                      setCurrentIndex(idx);
                    }}
                    className={getQuestionButtonClass(q.id, 'PROJECT', idx)}
                  >
                    P{idx + 1}
                  </button>
                ))}
              </div>
            </div>

            {/* Question Palette Status Legend */}
            <div className="bg-slate-950/40 p-4 border border-slate-850 rounded-2xl space-y-2">
              <h5 className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Question Status Palette</h5>
              <div className="grid grid-cols-2 gap-2 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 block"></span>
                  <span>Answered</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-500 block"></span>
                  <span>Skipped</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-slate-700 block"></span>
                  <span>Unattempted</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-violet-600 block"></span>
                  <span>Current</span>
                </div>
              </div>
            </div>
          </div>

          <div className="bg-slate-950/40 p-4 border border-slate-900 rounded-2xl flex items-center gap-3 mt-8">
            <AlertCircle className="w-5 h-5 text-amber-500 shrink-0" />
            <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider leading-relaxed">
              Exit triggers security alerts. Tab visibility is analyzed dynamically.
            </span>
          </div>
        </aside>

        {/* Right Active Question Workspace */}
        <section className="flex-1 flex flex-col overflow-hidden bg-slate-950/40">
          <div className="flex-1 overflow-y-auto p-8">
            {currentQuestion ? (
              <div className="space-y-6 max-w-4xl mx-auto">
                {/* Round: MCQ Question */}
                {currentRound === 'MCQ' && (
                  <div className="space-y-6">
                    <div className="bg-slate-900 p-6 rounded-3xl border border-slate-850 shadow-lg">
                      <div className="flex items-center justify-between border-b border-slate-850/50 pb-3 mb-4">
                        <span className="bg-violet-950/60 border border-violet-850 text-violet-400 text-xs px-3 py-1.5 rounded-xl font-bold uppercase tracking-wider">
                          Question {currentIndex + 1} of {questions.length}
                        </span>
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">Category: {currentQuestion.category || 'General'}</span>
                      </div>
                      <h2 className="text-xl font-black text-slate-200 leading-relaxed">{currentQuestion.question}</h2>
                    </div>

                    <div className="grid grid-cols-1 gap-3">
                      {currentQuestion.options?.map((option, idx) => {
                        const optLetter = option.substring(0, 1);
                        const isSelected = answers[currentQuestion.id] === optLetter;
                        return (
                          <button
                            key={idx}
                            onClick={() => handleSelectMcq(currentQuestion.id, optLetter)}
                            className={`p-5 rounded-2xl border-2 text-left transition-all flex items-center justify-between group ${
                              isSelected
                                ? 'bg-violet-600/10 border-violet-500 text-violet-300 font-bold shadow-md'
                                : 'bg-slate-900 border-slate-850 text-slate-300 hover:border-slate-800'
                            }`}
                          >
                            <span className="text-base">{option}</span>
                            <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center transition-all ${
                              isSelected ? 'border-violet-500 bg-violet-600' : 'border-slate-700'
                            }`}>
                              {isSelected && <div className="w-2.5 h-2.5 bg-white rounded-full" />}
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Round: Scenario Question */}
                {currentRound === 'SCENARIO' && (
                  <div className="space-y-6">
                    <div className="bg-slate-900 p-6 rounded-3xl border border-slate-850 shadow-lg space-y-4">
                      <div className="flex items-center justify-between border-b border-slate-850/50 pb-3 mb-2">
                        <span className="bg-violet-950/60 border border-violet-850 text-violet-400 text-xs px-3 py-1.5 rounded-xl font-bold uppercase tracking-wider">
                          Question {currentIndex + 1} of {questions.length}
                        </span>
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">Scenario Question</span>
                      </div>
                      <div>
                        <h2 className="text-xl font-black text-slate-200 leading-relaxed">{currentQuestion.question}</h2>
                      </div>
                      {currentQuestion.context && (
                        <div className="bg-slate-950 p-4 border border-slate-850 rounded-2xl text-sm font-semibold text-slate-400 leading-relaxed">
                          <span className="text-xs font-bold text-slate-500 uppercase tracking-wider block mb-1">Context:</span>
                          {currentQuestion.context}
                        </div>
                      )}
                    </div>

                    <div className="space-y-2">
                      <div className="flex items-center justify-between text-xs font-bold text-slate-500 uppercase tracking-wider px-1">
                        <span>Candidate Solution</span>
                        <div className="flex items-center gap-3">
                          {hasVoiceSupport ? (
                            <button
                              type="button"
                              onClick={toggleListening}
                              disabled={isTranscribing}
                              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-bold transition-all cursor-pointer ${
                                isTranscribing
                                  ? 'bg-violet-950/60 border-violet-500/80 text-violet-300 opacity-90 cursor-wait'
                                  : isListening
                                  ? 'bg-rose-950/60 border-rose-500/80 text-rose-400 animate-pulse hover:bg-rose-900/60'
                                  : 'bg-slate-900 border-slate-800 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                              }`}
                              title={isTranscribing ? 'Processing audio with AI...' : isListening ? 'Click to stop recording and process' : 'Speak your answer'}
                            >
                              {isTranscribing ? (
                                <>
                                  <Loader2 className="w-3.5 h-3.5 animate-spin text-violet-400" />
                                  <span>Transcribing...</span>
                                </>
                              ) : isListening ? (
                                <>
                                  <span className="w-2 h-2 rounded-full bg-rose-500 animate-ping" />
                                  <span>Recording (Click to Finish)</span>
                                </>
                              ) : (
                                <>
                                  <Mic className="w-3.5 h-3.5 text-violet-400" />
                                  <span>Start Speaking</span>
                                </>
                              )}
                            </button>
                          ) : (
                            <span className="text-slate-500 text-xs font-medium italic">
                              Voice input is not supported in this browser. Please type your answer.
                            </span>
                          )}
                          <span>Words: {getWordCount(answers[currentQuestion.id])} / recommended: 150</span>
                        </div>
                      </div>
                      <textarea
                        value={answers[currentQuestion.id] || ''}
                        onChange={(e) => handleTextareaChange(currentQuestion.id, e.target.value)}
                        onBlur={(e) => handleTextareaBlur(currentQuestion.id, 'SCENARIO', e.target.value)}
                        placeholder="Detail your architecture layout, design patterns, security mechanisms, and failover strategy..."
                        className="w-full h-80 bg-slate-900 border border-slate-850 rounded-3xl p-6 text-white focus:outline-none focus:border-violet-500 transition-all font-medium leading-relaxed resize-none shadow-inner"
                      />
                    </div>
                  </div>
                )}

                {/* Round: Project Question */}
                {currentRound === 'PROJECT' && (
                  <div className="space-y-6">
                    <div className="bg-slate-900 p-6 rounded-3xl border border-slate-850 shadow-lg space-y-3">
                      <div className="flex items-center justify-between border-b border-slate-850/50 pb-3 mb-2">
                        <span className="bg-violet-950/60 border border-violet-850 text-violet-400 text-xs px-3 py-1.5 rounded-xl font-bold uppercase tracking-wider">
                          Question {currentIndex + 1} of {questions.length}
                        </span>
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">Project Deep Dive Discussion</span>
                      </div>
                      <div>
                        <h2 className="text-xl font-black text-slate-200 leading-relaxed">{currentQuestion.question}</h2>
                      </div>
                      {currentQuestion.projectReference && (
                        <div className="inline-block px-3.5 py-1 bg-violet-950/40 border border-violet-900/40 text-violet-400 text-xs font-black rounded-lg uppercase tracking-wider">
                          Project: {currentQuestion.projectReference}
                        </div>
                      )}
                    </div>

                    <div className="space-y-2">
                      <div className="flex items-center justify-between text-xs font-bold text-slate-500 uppercase tracking-wider px-1">
                        <span>Ownership Proof / Details</span>
                        <div className="flex items-center gap-3">
                          {hasVoiceSupport ? (
                            <button
                              type="button"
                              onClick={toggleListening}
                              disabled={isTranscribing}
                              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-bold transition-all cursor-pointer ${
                                isTranscribing
                                  ? 'bg-violet-950/60 border-violet-500/80 text-violet-300 opacity-90 cursor-wait'
                                  : isListening
                                  ? 'bg-rose-950/60 border-rose-500/80 text-rose-400 animate-pulse hover:bg-rose-900/60'
                                  : 'bg-slate-900 border-slate-800 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                              }`}
                              title={isTranscribing ? 'Processing audio with AI...' : isListening ? 'Click to stop recording and process' : 'Speak your answer'}
                            >
                              {isTranscribing ? (
                                <>
                                  <Loader2 className="w-3.5 h-3.5 animate-spin text-violet-400" />
                                  <span>Transcribing...</span>
                                </>
                              ) : isListening ? (
                                <>
                                  <span className="w-2 h-2 rounded-full bg-rose-500 animate-ping" />
                                  <span>Recording (Click to Finish)</span>
                                </>
                              ) : (
                                <>
                                  <Mic className="w-3.5 h-3.5 text-violet-400" />
                                  <span>Start Speaking</span>
                                </>
                              )}
                            </button>
                          ) : (
                            <span className="text-slate-500 text-xs font-medium italic">
                              Voice input is not supported in this browser. Please type your answer.
                            </span>
                          )}
                          <span>Words: {getWordCount(answers[currentQuestion.id])} / recommended: 150</span>
                        </div>
                      </div>
                      <textarea
                        value={answers[currentQuestion.id] || ''}
                        onChange={(e) => handleTextareaChange(currentQuestion.id, e.target.value)}
                        onBlur={(e) => handleTextareaBlur(currentQuestion.id, 'PROJECT', e.target.value)}
                        placeholder="Walk the interviewer through the repository structure, initialization config files, connection settings, and precise debugging trace you wrote..."
                        className="w-full h-80 bg-slate-900 border border-slate-850 rounded-3xl p-6 text-white focus:outline-none focus:border-violet-500 transition-all font-medium leading-relaxed resize-none shadow-inner"
                      />
                    </div>
                  </div>
                )}

                {/* Final Question Completion CTA Banner */}
                {isFinalQuestion && (
                  <div className="bg-gradient-to-r from-violet-950/50 via-slate-900 to-fuchsia-950/40 border-2 border-violet-500/40 rounded-3xl p-6 sm:p-7 shadow-2xl flex flex-col sm:flex-row items-center justify-between gap-5 animate-in fade-in duration-300">
                    <div className="flex items-center gap-4">
                      <div className="w-12 h-12 rounded-2xl bg-emerald-500/20 border border-emerald-500/40 flex items-center justify-center text-emerald-400 shrink-0">
                        <CheckCircle2 className="w-6 h-6" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-xs font-black uppercase tracking-wider text-emerald-400">✓ Final Question</span>
                          <span className="text-slate-500 text-xs font-bold">•</span>
                          <span className="text-xs font-bold text-slate-400">Question 10 of 10</span>
                        </div>
                        <h4 className="font-black text-slate-100 text-base">You&apos;ve reached the end of the assessment</h4>
                        <p className="text-slate-400 text-xs mt-1 leading-relaxed">
                          Review your answers across all sections, then click &quot;Submit Test&quot; when you&apos;re ready to finish.
                        </p>
                      </div>
                    </div>
                    <button
                      onClick={openSubmitConfirmation}
                      disabled={isSubmitting}
                      className="w-full sm:w-auto px-8 py-3.5 bg-gradient-to-r from-violet-600 to-fuchsia-600 hover:opacity-95 text-white font-black text-sm rounded-2xl tracking-wider uppercase transition-all shadow-xl shadow-violet-900/40 cursor-pointer disabled:opacity-50 shrink-0 flex items-center justify-center gap-2.5"
                    >
                      {isSubmitting ? (
                        <>
                          <Loader2 className="w-4.5 h-4.5 animate-spin" />
                          <span>Submitting...</span>
                        </>
                      ) : (
                        <>
                          <span>Submit Test</span>
                          <CheckCircle2 className="w-4.5 h-4.5" />
                        </>
                      )}
                    </button>
                  </div>
                )}

              </div>
            ) : (
              <div className="text-center text-slate-500 font-medium p-12">
                Select a question from the navigation panel to start.
              </div>
            )}
          </div>

          {/* Navigation Controls footer */}
          <footer className="bg-slate-900 border-t border-slate-850 px-8 py-4 flex items-center justify-between shrink-0">
            <button
              onClick={handlePrev}
              disabled={currentIndex === 0 && currentRound === 'MCQ'}
              className="flex items-center gap-1.5 px-5 py-2.5 bg-slate-850 hover:bg-slate-800 border border-slate-850 text-slate-300 font-bold text-sm rounded-xl transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <ChevronLeft className="w-4.5 h-4.5" /> Previous Question
            </button>
            <button
              onClick={handleSkipQuestion}
              className="px-5 py-2.5 bg-amber-950/40 hover:bg-amber-900/40 border border-amber-800/60 text-amber-400 font-bold text-sm rounded-xl transition-all cursor-pointer"
            >
              Skip Question
            </button>
            {isFinalQuestion ? (
              <button
                onClick={openSubmitConfirmation}
                disabled={isSubmitting}
                className="flex items-center gap-2 px-7 py-2.5 bg-gradient-to-r from-violet-600 to-fuchsia-600 hover:opacity-95 text-white font-black text-sm rounded-xl tracking-wider uppercase transition-all shadow-md shadow-violet-900/30 cursor-pointer disabled:opacity-50"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Submitting...
                  </>
                ) : (
                  <>
                    <span>Submit Test</span>
                    <CheckCircle2 className="w-4 h-4" />
                  </>
                )}
              </button>
            ) : (
              <button
                onClick={handleNext}
                className="flex items-center gap-1.5 px-5 py-2.5 bg-violet-600 hover:bg-violet-500 text-white font-bold text-sm rounded-xl transition-all cursor-pointer shadow-md shadow-violet-900/20"
              >
                Next Question <ChevronRight className="w-4.5 h-4.5" />
              </button>
            )}
          </footer>
        </section>
      </main>

      {/* Exit Confirmation Modal */}
      {showExitModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-md w-full p-6 space-y-6 shadow-2xl animate-in fade-in zoom-in duration-200">
            <div className="space-y-2">
              <div className="w-12 h-12 rounded-full bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-500 mb-4">
                <AlertCircle className="w-6 h-6" />
              </div>
              <h3 className="text-lg font-black text-slate-100">Exit Assessment?</h3>
              <p className="text-slate-400 text-sm leading-relaxed">
                Are you sure you want to exit? Your current progress will be saved, but this assessment session will be closed as <strong className="text-rose-400">EXITED</strong>. You will not be able to resume this session.
              </p>
            </div>
            
            <div className="flex items-center gap-3">
              <button
                onClick={() => setShowExitModal(false)}
                disabled={isExiting}
                className="flex-1 py-3 bg-slate-850 hover:bg-slate-800 text-slate-300 font-bold text-sm rounded-xl border border-slate-800 transition-all cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleExitAssessment}
                disabled={isExiting}
                className="flex-1 py-3 bg-gradient-to-r from-rose-600 to-red-600 hover:opacity-95 text-white font-black text-sm rounded-xl tracking-wider uppercase transition-all shadow-md cursor-pointer flex items-center justify-center gap-1.5"
              >
                {isExiting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Exiting...
                  </>
                ) : (
                  'Yes, Exit Test'
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Submit Confirmation Modal */}
      {showSubmitModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-md w-full p-6 space-y-6 shadow-2xl animate-in fade-in zoom-in duration-200">
            <div className="space-y-3">
              <div className="w-12 h-12 rounded-2xl bg-violet-500/10 border border-violet-500/30 flex items-center justify-center text-violet-400">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <h3 className="text-xl font-black text-slate-100">Submit Assessment?</h3>
              <p className="text-slate-400 text-sm leading-relaxed">
                You have answered <strong className="text-violet-400">{getAnsweredCount()}</strong> of <strong className="text-slate-200">{getTotalQuestionCount()}</strong> questions.
              </p>
              <p className="text-slate-400 text-xs leading-relaxed">
                Once submitted, your responses will be securely locked and evaluated by the AI evaluation pipeline. This action cannot be undone.
              </p>
            </div>
            
            <div className="flex items-center gap-3">
              <button
                onClick={() => setShowSubmitModal(false)}
                disabled={isSubmitting}
                className="flex-1 py-3 bg-slate-850 hover:bg-slate-800 text-slate-300 font-bold text-sm rounded-xl border border-slate-800 transition-all cursor-pointer"
              >
                Review Answers
              </button>
              <button
                onClick={handleConfirmSubmit}
                disabled={isSubmitting}
                className="flex-1 py-3 bg-gradient-to-r from-violet-600 to-fuchsia-600 hover:opacity-95 text-white font-black text-sm rounded-xl tracking-wider uppercase transition-all shadow-md cursor-pointer flex items-center justify-center gap-1.5"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Submitting...
                  </>
                ) : (
                  'Yes, Submit Test'
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Immediate Submission Transition State */}
      {isSubmitting && (
        <div className="fixed inset-0 bg-slate-950/95 backdrop-blur-md flex flex-col items-center justify-center z-50 p-6 select-none animate-in fade-in duration-200">
          <div className="max-w-md w-full bg-slate-900/80 border border-slate-800 rounded-3xl p-8 space-y-6 text-center shadow-2xl relative overflow-hidden">
            <div className="w-16 h-16 rounded-3xl bg-violet-600/20 border border-violet-500/40 flex items-center justify-center mx-auto text-violet-400">
              <Loader2 className="w-8 h-8 animate-spin text-violet-400" />
            </div>

            <div className="space-y-2">
              <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-emerald-950/50 border border-emerald-800/50 text-emerald-400 text-xs font-black rounded-lg uppercase tracking-wider">
                <CheckCircle2 className="w-3.5 h-3.5" /> Test Submitted
              </div>
              <h3 className="text-2xl font-black text-slate-100 tracking-tight">Your answers were received</h3>
              <p className="text-slate-400 text-sm leading-relaxed">
                Preparing your results and transitioning to the AI evaluation dashboard...
              </p>
            </div>

            <div className="flex items-center justify-center gap-2 text-xs font-bold text-violet-400 tracking-wider uppercase">
              <span className="w-2 h-2 rounded-full bg-violet-400 animate-ping" />
              <span>Connecting to evaluation engine...</span>
            </div>
          </div>
        </div>
      )}

      {/* Automatic Fullscreen blocked overlay banner */}
      {showFullscreenOverlay && (
        <div 
          onClick={enterFullscreen}
          className="fixed inset-0 bg-slate-950/95 backdrop-blur-md flex flex-col items-center justify-center z-50 p-6 cursor-pointer"
        >
          <div className="text-center space-y-4 max-w-sm">
            <div className="w-16 h-16 rounded-full bg-violet-600/10 border border-violet-500/30 flex items-center justify-center text-violet-400 mx-auto animate-bounce">
              <Camera className="w-8 h-8" />
            </div>
            <h3 className="text-lg font-black text-slate-100">Click anywhere to enter fullscreen mode</h3>
            <p className="text-slate-400 text-xs leading-relaxed">
              Browser security policies require a click gesture to enable fullscreen mode before starting the assessment.
            </p>
          </div>
        </div>
      )}

      {/* Fullscreen Warning Modal */}
      {showFullscreenWarningModal && (
        <div className="fixed inset-0 bg-slate-950/90 backdrop-blur-md flex items-center justify-center z-50 p-4">
          <div className="bg-slate-900 border-2 border-rose-500 rounded-3xl max-w-md w-full p-6 space-y-6 shadow-2xl animate-in fade-in zoom-in duration-200">
            <div className="space-y-2 text-center">
              <div className="w-16 h-16 rounded-full bg-rose-500/10 border-2 border-rose-500 flex items-center justify-center text-rose-500 mx-auto mb-4 animate-pulse">
                <AlertCircle className="w-8 h-8" />
              </div>
              <h3 className="text-xl font-black text-rose-500">Security Warning</h3>
              <p className="text-slate-200 font-bold text-sm">
                Fullscreen mode is required for this assessment.
              </p>
              <p className="text-slate-400 text-xs leading-relaxed">
                Exiting fullscreen is recorded as a security violation. Repeated violations will flag your profile for review. Click the button below to resume in fullscreen.
              </p>
            </div>
            
            <button
              onClick={enterFullscreen}
              className="w-full py-3 bg-gradient-to-r from-rose-600 to-red-600 hover:opacity-95 text-white font-black text-sm rounded-xl tracking-wider uppercase transition-all shadow-md cursor-pointer"
            >
              Resume Assessment
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default TestModule;
