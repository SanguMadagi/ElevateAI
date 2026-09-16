import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { testService, profileService } from '../services/api';
import { ShieldCheck, HelpCircle, FileText, CheckSquare, Sparkles, Loader2, AlertCircle, Camera, CheckCircle2, XCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import { getFaceDescriptors, loadFaceModels } from '../utils/faceRecognition';

const AssessmentStartPage = () => {
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [phase, setPhase] = useState('ready');
  const [checks, setChecks] = useState({ browser: 'pending', network: 'pending', camera: 'pending' });
  const [cameraStream, setCameraStream] = useState(null);
  const [faceCount, setFaceCount] = useState(null);
  const [capture, setCapture] = useState(null);
  const [identityEmbedding, setIdentityEmbedding] = useState(null);
  const videoRef = useRef(null);
  const cameraStreamRef = useRef(null);
  const faceTimerRef = useRef(null);
  const faceSamplesRef = useRef({ visible: 0, absent: 0, multiple: 0 });

  const stopCamera = () => {
    if (faceTimerRef.current) window.clearInterval(faceTimerRef.current);
    cameraStreamRef.current?.getTracks().forEach(track => track.stop());
    cameraStreamRef.current = null;
    setCameraStream(null);
  };

  useEffect(() => () => {
    if (faceTimerRef.current) window.clearInterval(faceTimerRef.current);
    cameraStreamRef.current?.getTracks().forEach(track => track.stop());
    cameraStreamRef.current = null;
  }, []);
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
        setChecks(prev => ({ ...prev, camera: 'passed' }));
      } catch (error) {
        setChecks(prev => ({ ...prev, camera: 'failed' }));
        toast.error('Camera is permitted but the live preview could not start. Please retry.');
      }
    };
    if (video.readyState >= 1) startPlayback();
    else video.addEventListener('loadedmetadata', startPlayback, { once: true });
    return () => video.removeEventListener('loadedmetadata', startPlayback);
  }, [cameraStream]);

  const runSystemCheck = async () => {
    setPhase('system');
    let browserOk = Boolean(navigator.mediaDevices?.getUserMedia && document.visibilityState && 'visibilityState' in document);
    try {
      await loadFaceModels();
    } catch (error) {
      browserOk = false;
    }
    setChecks(prev => ({ ...prev, browser: browserOk ? 'passed' : 'failed' }));
    let networkOk = false;
    try {
      await profileService.getProfile();
      networkOk = true;
    } catch (error) {
      networkOk = false;
    }
    setChecks(prev => ({ ...prev, network: networkOk ? 'passed' : 'failed' }));
    if (!browserOk || !networkOk) return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false });
      cameraStreamRef.current = stream;
      setCameraStream(stream);
      faceTimerRef.current = window.setInterval(async () => {
        if (!videoRef.current || videoRef.current.readyState < 2) return;
        try {
          const faces = await getFaceDescriptors(videoRef.current, 3);
          if (faces.length === 1) {
            faceSamplesRef.current.visible += 1;
            faceSamplesRef.current.absent = 0;
            faceSamplesRef.current.multiple = 0;
          } else if (faces.length > 1) {
            faceSamplesRef.current.multiple += 1;
            faceSamplesRef.current.visible = 0;
            faceSamplesRef.current.absent = 0;
          } else {
            faceSamplesRef.current.absent += 1;
            faceSamplesRef.current.visible = 0;
            faceSamplesRef.current.multiple = 0;
          }
          setFaceCount(faces.length === 1 && faceSamplesRef.current.visible >= 2 ? 1 : faces.length > 1 && faceSamplesRef.current.multiple >= 4 ? 2 : faces.length === 0 && faceSamplesRef.current.absent >= 4 ? 0 : null);
        } catch (error) {
          setFaceCount(null);
        }
      }, 1500);
    } catch (error) {
      setChecks(prev => ({ ...prev, camera: 'failed' }));
    }
  };

  const capturePhoto = async () => {
    if (faceCount !== 1 || !videoRef.current) return;
    const canvas = document.createElement('canvas');
    canvas.width = videoRef.current.videoWidth;
    canvas.height = videoRef.current.videoHeight;
    canvas.getContext('2d').drawImage(videoRef.current, 0, 0);
    const dataUrl = canvas.toDataURL('image/jpeg', 0.85);
    const descriptors = await getFaceDescriptors(videoRef.current, 2);
    if (descriptors.length !== 1) return;
    setCapture(dataUrl);
    setIdentityEmbedding(Array.from(descriptors[0].descriptor));
  };

  const confirmIdentity = async () => {
    if (!capture || !identityEmbedding) return;
    setStarting(true);
    try {
      if (!document.fullscreenElement && document.documentElement.requestFullscreen) await document.documentElement.requestFullscreen();
      const { data } = await testService.startTest();
      const testId = data.testId || data.id;
      await testService.enrollIdentity(testId, identityEmbedding);
      sessionStorage.setItem(`assessment_identity_${testId}`, JSON.stringify(identityEmbedding));
      stopCamera();
      navigate(`/assessment/${testId}`);
    } catch (error) {
      toast.error('Unable to start the verified assessment. Please retry.');
    } finally {
      setStarting(false);
    }
  };

  const checkRows = [
    ['browser', 'Browser and required APIs'],
    ['network', 'Backend connection'],
    ['camera', 'Camera detected, permitted and working']
  ];

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const { data } = await profileService.getProfile();
        const isComplete = data && data.name && data.targetRole && data.skills && data.skills.length > 0;
        if (!isComplete) {
          toast.error('Please complete your profile before starting an assessment.');
          navigate('/profile');
        } else {
          setProfile(data);
        }
      } catch (err) {
        console.error(err);
        toast.error('Failed to load candidate profile. Please configure profile first.');
        navigate('/profile');
      } finally {
        setLoading(false);
      }
    };
    fetchProfile();
  }, [navigate]);

  const handleStart = async () => {
    if (!agreed) {
      toast.error('You must agree to the assessment rules to proceed.');
      return;
    }
    await runSystemCheck();
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 text-white flex flex-col items-center justify-center">
        <Loader2 className="w-12 h-12 animate-spin text-violet-500 mb-4" />
        <p className="text-slate-400 font-medium animate-pulse">Loading assessment settings...</p>
      </div>
    );
  }

  if (phase === 'system' || phase === 'identity') {
    const systemReady = Object.values(checks).every(status => status === 'passed');
    return (
      <div className="min-h-screen bg-slate-950 text-white py-12 px-4 flex items-center justify-center">
        <div className="max-w-3xl w-full space-y-6">
          <div className="text-center space-y-2"><h1 className="text-3xl font-black">{phase === 'system' ? 'Assessment System Check' : 'Identity Verification'}</h1><p className="text-slate-400">{phase === 'system' ? 'We need to verify your camera, browser and connection before you begin.' : 'Position your face inside the camera frame and capture a live photo. Your camera image stays on this device.'}</p></div>
          <div className="bg-slate-900/60 border border-slate-800 rounded-3xl p-6 grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="bg-slate-950 rounded-2xl p-4 flex items-center justify-center"><div className="camera-preview"><video ref={videoRef} autoPlay muted playsInline /></div></div>
            {phase === 'system' ? (
              <div className="space-y-3"><h2 className="font-black text-lg">Preflight checks</h2>{checkRows.map(([key, label]) => <div key={key} className="flex items-center justify-between p-3 bg-slate-950 rounded-xl text-sm"><span>{label}</span>{checks[key] === 'passed' ? <CheckCircle2 className="w-5 h-5 text-emerald-400" /> : checks[key] === 'failed' ? <XCircle className="w-5 h-5 text-rose-400" /> : <Loader2 className="w-5 h-5 text-violet-400 animate-spin" />}</div>)}<p className="text-xs text-slate-500 pt-2">Microphone access is not requested because this assessment does not require it.</p>{systemReady && <button onClick={() => setPhase('identity')} className="w-full py-3 bg-violet-600 rounded-xl font-black">Continue to Identity Verification</button>}{!systemReady && checks.browser === 'failed' && <p className="text-sm text-rose-400">This browser does not support the required live face detection APIs.</p>}</div>
            ) : (
              <div className="space-y-4"><div className="p-3 bg-slate-950 rounded-xl text-sm"><p className="text-slate-400">Face status</p><p className={faceCount === 1 ? 'text-emerald-400 font-bold' : 'text-amber-400 font-bold'}>{faceCount === null ? 'Checking camera...' : faceCount === 1 ? 'Exactly one face detected' : faceCount === 0 ? 'No face detected' : 'Multiple faces detected'}</p></div>{capture ? <img src={capture} alt="Captured identity" className="w-full aspect-video object-cover rounded-xl" /> : <p className="text-xs text-slate-500">Only a live capture from this webcam can be used.</p>}<div className="flex gap-3">{capture && <button onClick={() => { setCapture(null); setIdentityEmbedding(null); }} className="flex-1 py-3 bg-slate-800 rounded-xl font-bold">Retake</button>} {!capture ? <button disabled={faceCount !== 1} onClick={capturePhoto} className="flex-1 py-3 bg-violet-600 disabled:opacity-40 rounded-xl font-black">Capture Photo</button> : <button disabled={starting} onClick={confirmIdentity} className="flex-1 py-3 bg-emerald-600 disabled:opacity-40 rounded-xl font-black">{starting ? 'Starting...' : 'Confirm Identity Photo'}</button>}</div><button onClick={() => { stopCamera(); setPhase('ready'); }} className="w-full text-sm text-slate-500 hover:text-white">Cancel</button></div>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-white py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-4xl mx-auto space-y-8">
        {/* Header */}
        <div className="text-center space-y-2">
          <h1 className="text-4xl font-black bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent flex items-center justify-center gap-3">
            <Sparkles className="w-9 h-9 text-violet-400 animate-pulse" /> Ready to Assess Your Skills?
          </h1>
          <p className="text-slate-400 text-lg">Please review your profile details and rules before initiating the session.</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* Profile Summary Card */}
          <div className="bg-slate-900/40 border border-slate-850 rounded-3xl p-8 space-y-6 shadow-xl">
            <h3 className="text-xl font-bold border-b border-slate-800 pb-3 flex items-center gap-2">
              <ShieldCheck className="w-5 h-5 text-violet-400" /> Candidate Profile Summary
            </h3>
            
            <div className="space-y-4">
              <div>
                <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Name</p>
                <p className="font-bold text-slate-200 text-lg">{profile?.name || 'N/A'}</p>
              </div>

              <div>
                <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Target Role</p>
                <p className="font-bold text-slate-200 text-lg">{profile?.targetRole || 'N/A'}</p>
              </div>

              <div>
                <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Expertise level</p>
                <p className="font-bold text-violet-400 text-sm tracking-wider uppercase">{profile?.expertiseLevel || 'N/A'}</p>
              </div>

              <div>
                <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Skills</p>
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {profile?.skills && profile.skills.length > 0 ? (
                    profile.skills.map((s, i) => (
                      <span key={i} className="px-2.5 py-1 bg-slate-950 border border-slate-850 text-slate-400 text-xs font-bold rounded-lg">{s}</span>
                    ))
                  ) : (
                    <span className="text-slate-500 text-sm">No skills added</span>
                  )}
                </div>
              </div>

              <div>
                <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Resume Analyzed</p>
                <p className="font-bold text-slate-200 flex items-center gap-2 mt-1">
                  {profile?.resumeFileName ? (
                    <span className="px-2.5 py-1 bg-emerald-950/40 border border-emerald-900/40 text-emerald-400 rounded-lg text-xs font-bold uppercase tracking-wider flex items-center gap-1.5">
                      <FileText className="w-3.5 h-3.5" /> Yes ({profile.resumeFileName})
                    </span>
                  ) : (
                    <span className="px-2.5 py-1 bg-rose-950/40 border border-rose-900/40 text-rose-400 rounded-lg text-xs font-bold uppercase tracking-wider">
                      No
                    </span>
                  )}
                </p>
              </div>
            </div>
          </div>

          {/* Assessment Overview Card */}
          <div className="bg-slate-900/40 border border-slate-850 rounded-3xl p-8 space-y-6 shadow-xl flex flex-col justify-between">
            <div className="space-y-6">
              <h3 className="text-xl font-bold border-b border-slate-800 pb-3 flex items-center gap-2">
                <HelpCircle className="w-5 h-5 text-violet-400" /> Assessment Structure
              </h3>

              <div className="space-y-4">
                <div className="flex items-center justify-between font-bold text-sm bg-slate-950/40 p-3.5 border border-slate-850 rounded-2xl">
                  <span className="text-slate-300">Round 1: Multiple Choice (MCQ)</span>
                  <span className="text-violet-400">4 Questions (40%)</span>
                </div>

                <div className="flex items-center justify-between font-bold text-sm bg-slate-950/40 p-3.5 border border-slate-850 rounded-2xl">
                  <span className="text-slate-300">Round 2: Scenario Analysis</span>
                  <span className="text-violet-400">3 Questions (30%)</span>
                </div>

                <div className="flex items-center justify-between font-bold text-sm bg-slate-950/40 p-3.5 border border-slate-850 rounded-2xl">
                  <span className="text-slate-300">Round 3: Project Discussion</span>
                  <span className="text-violet-400">3 Questions (30%)</span>
                </div>
              </div>
            </div>

            <div className="font-bold text-lg text-slate-300 flex items-center justify-between bg-violet-600/10 p-4 border border-violet-900/20 rounded-2xl">
              <span>Total Duration:</span>
              <span className="text-violet-400">60 Minutes</span>
            </div>
          </div>
        </div>

        {/* Security Rules Card */}
        <div className="bg-slate-900/40 border border-slate-850 rounded-3xl p-8 space-y-6 shadow-xl">
          <h3 className="text-xl font-bold border-b border-slate-800 pb-3 flex items-center gap-2 text-rose-400">
            <AlertCircle className="w-5 h-5 text-rose-500" /> Security & Integrity Guidelines
          </h3>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="flex items-start gap-3 bg-slate-950/40 p-4 border border-slate-850 rounded-2xl font-semibold text-sm">
              <CheckSquare className="w-5 h-5 text-rose-500 shrink-0 mt-0.5" />
              <span className="text-slate-300">Do not switch browser tabs (will trigger security warning)</span>
            </div>
            <div className="flex items-start gap-3 bg-slate-950/40 p-4 border border-slate-850 rounded-2xl font-semibold text-sm">
              <CheckSquare className="w-5 h-5 text-rose-500 shrink-0 mt-0.5" />
              <span className="text-slate-300">Stay in fullscreen mode during the entire assessment</span>
            </div>
            <div className="flex items-start gap-3 bg-slate-950/40 p-4 border border-slate-850 rounded-2xl font-semibold text-sm">
              <CheckSquare className="w-5 h-5 text-rose-500 shrink-0 mt-0.5" />
              <span className="text-slate-300">Do not copy or paste assessment content</span>
            </div>
            <div className="flex items-start gap-3 bg-slate-950/40 p-4 border border-slate-850 rounded-2xl font-semibold text-sm">
              <CheckSquare className="w-5 h-5 text-rose-500 shrink-0 mt-0.5" />
              <span className="text-slate-300">Do not attempt to open browser DevTools</span>
            </div>
          </div>

          <div className="flex items-center gap-3 pt-4 border-t border-slate-850">
            <input 
              type="checkbox"
              id="rules-agree"
              checked={agreed}
              onChange={(e) => setAgreed(e.target.checked)}
              className="w-5 h-5 rounded-lg border-slate-850 bg-slate-950 text-violet-600 focus:ring-violet-500 cursor-pointer"
            />
            <label htmlFor="rules-agree" className="font-bold text-slate-300 select-none cursor-pointer">
              I agree to the assessment rules and understand that violations will be reported to review.
            </label>
          </div>

          <div className="flex justify-center pt-4">
            <button
              onClick={handleStart}
              disabled={starting || !agreed}
              className="flex items-center gap-2 px-12 py-4.5 bg-gradient-to-r from-violet-600 to-fuchsia-600 text-white rounded-2xl font-black text-lg hover:opacity-95 transition-all shadow-xl shadow-violet-500/10 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              {starting ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  Generating Test...
                </>
              ) : (
                'Start Assessment Now'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AssessmentStartPage;
