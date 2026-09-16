import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { profileService, skillService, testService, interviewService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { 
  User, Briefcase, Award, GraduationCap, Upload, CheckCircle2, ChevronRight,
  Loader2, X, FileText, TrendingUp, AlertCircle, ShieldCheck, Sparkles, Brain, Search, Clock, Bot
} from 'lucide-react';
import toast from 'react-hot-toast';
import MockInterviewModal from '../components/MockInterviewModal';

const ProfilePage = () => {
  const navigate = useNavigate();
  const { updateUser, user } = useAuth();
  const [activeTab, setActiveTab] = useState('dashboard'); // 'dashboard' | 'settings'
  const [loading, setLoading] = useState(false);
  const [profileLoading, setProfileLoading] = useState(true);
  const [uploadingResume, setUploadingResume] = useState(false);
  const [editMode, setEditMode] = useState(false);
  const [interviewModalOpen, setInterviewModalOpen] = useState(false);
  const [parsingStage, setParsingStage] = useState(0);
  const parsingStages = ['Uploading resume...', 'Parsing resume...', 'Extracting education, skills & experience...', 'Building your AI profile...'];

  const [tests, setTests] = useState([]);
  const [interviews, setInterviews] = useState([]);
  const [skillAnalysis, setSkillAnalysis] = useState(null);

  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    targetRole: '',
    expertiseLevel: '',
    skills: [],
    education: [],
    experience: [],
    projects: [],
    certifications: [],
    resumeFileName: '',
    resumeText: '',
    currentCompany: '',
    educationLevel: '',
    yearsOfExperience: 0
  });

  const parseTimestamp = (timestamp) => {
    if (!timestamp) return 0;
    if (Array.isArray(timestamp)) {
      return new Date(timestamp[0], (timestamp[1] || 1) - 1, timestamp[2] || 1, timestamp[3] || 0, timestamp[4] || 0, timestamp[5] || 0).getTime();
    }
    const t = new Date(timestamp).getTime();
    return isNaN(t) ? 0 : t;
  };

  const formatActivityDate = (timestamp) => {
    if (!timestamp) return 'Recent';
    if (Array.isArray(timestamp)) {
      return `${String(timestamp[2] || 1).padStart(2, '0')}/${String(timestamp[1] || 1).padStart(2, '0')}/${timestamp[0]}`;
    }
    const d = new Date(timestamp);
    if (isNaN(d.getTime())) return 'Recent';
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  };

  const combinedActivities = useMemo(() => {
    const activityMap = new Map();

    (tests || []).forEach(t => {
      if (!t || !t.testId) return;
      const isExited = ['EXITED', 'ABANDONED'].includes(t.status?.toUpperCase());
      const isCompleted = t.status?.toUpperCase() === 'EVALUATED' || t.status?.toUpperCase() === 'SUBMITTED';
      activityMap.set(`test_${t.testId}`, {
        id: t.testId,
        type: 'ASSESSMENT',
        title: t.title || `Assessment Session #${t.testId?.substring(0, 6)}`,
        subtitle: 'Multi-Round Technical Assessment',
        timestamp: parseTimestamp(t.createdAt),
        displayDate: formatActivityDate(t.createdAt),
        status: isExited ? 'Exited in between' : (isCompleted ? 'Completed' : 'In Progress'),
        isExited,
        score: isExited ? 'Incomplete' : (typeof t.finalScore === 'number' ? `${Math.round(t.finalScore)}%` : 'Pending')
      });
    });

    (interviews || []).forEach(inv => {
      if (!inv || !inv.id) return;
      const isCompleted = inv.status?.toUpperCase() === 'COMPLETED';
      const isExited = !isCompleted || ['EXITED', 'ABANDONED', 'EXITED_IN_BETWEEN'].includes(inv.status?.toUpperCase());
      let scoreVal = inv.overallScore != null
        ? Math.round(inv.overallScore)
        : (inv.finalReport?.overallScore != null
            ? Math.round(inv.finalReport.overallScore)
            : (inv.finalReport?.technicalScore != null ? Math.round(inv.finalReport.technicalScore) : null));

      if (scoreVal == null && Array.isArray(inv.history) && inv.history.length > 0 && isCompleted) {
        const validTurns = inv.history.filter(h => h.score != null);
        if (validTurns.length > 0) {
          const avg = validTurns.reduce((acc, h) => acc + h.score, 0) / validTurns.length;
          scoreVal = Math.round(avg);
        }
      }

      activityMap.set(`interview_${inv.id}`, {
        id: inv.id,
        type: 'INTERVIEW',
        title: 'AI Mock Interview',
        subtitle: inv.topic || inv.targetRole || 'Technical Interview',
        timestamp: parseTimestamp(inv.completedAt || inv.createdAt),
        displayDate: formatActivityDate(inv.completedAt || inv.createdAt),
        status: isCompleted ? 'Completed' : 'Exited in between',
        isExited,
        score: isCompleted ? (scoreVal != null ? `${scoreVal}%` : 'Completed') : 'Incomplete'
      });
    });

    return Array.from(activityMap.values()).sort((a, b) => b.timestamp - a.timestamp);
  }, [tests, interviews]);

  const fetchAllData = useCallback(async () => {
    try {
      const [profRes, skillRes, testsRes, interviewsRes] = await Promise.all([
        profileService.getProfile().catch(() => ({ data: null })),
        skillService.getMyAnalysis().catch(() => ({ data: null })),
        testService.getMyTests().catch(() => ({ data: [] })),
        interviewService.getMyInterviews().catch(() => ({ data: [] }))
      ]);

      if (profRes?.data) {
        const data = profRes.data;
        setFormData({
          firstName: data.firstName || user?.firstName || '',
          lastName: data.lastName || user?.lastName || '',
          email: data.email || user?.email || '',
          phone: data.phone || '',
          targetRole: data.targetRole || data.suggestedRole || '',
          expertiseLevel: data.expertiseLevel || '',
          skills: data.skills || [],
          education: data.education || [],
          experience: data.experience || [],
          projects: data.projects || [],
          certifications: data.certifications || [],
          resumeFileName: data.resumeFileName || '',
          resumeText: data.resumeText || '',
          currentCompany: data.currentCompany || '',
          educationLevel: data.educationLevel || '',
          yearsOfExperience: data.yearsOfExperience || 0
        });
      }
      setSkillAnalysis(skillRes?.data || null);
      setTests(Array.isArray(testsRes?.data) ? testsRes.data : []);
      setInterviews(Array.isArray(interviewsRes?.data) ? interviewsRes.data : []);
    } catch (err) {
      console.error('Failed to load profile data', err);
    } finally {
      setProfileLoading(false);
    }
  }, [user]);

  useEffect(() => {
    fetchAllData();
  }, [fetchAllData]);

  const handleSaveProfile = async () => {
    if (!formData.firstName.trim() || !formData.lastName.trim()) {
      toast.error('First name and last name are required.');
      return;
    }
    setLoading(true);
    try {
      await profileService.saveProfile({
        ...formData,
        name: `${formData.firstName.trim()} ${formData.lastName.trim()}`,
        skills: formData.skills.filter(Boolean),
        education: formData.education.filter(Boolean),
        experience: formData.experience.filter(Boolean),
        projects: formData.projects.filter(Boolean),
        certifications: formData.certifications.filter(Boolean)
      });
      const { data } = await profileService.getProfile();
      setFormData(prev => ({
        ...prev,
        ...data,
        skills: data.skills || [],
        education: data.education || [],
        experience: data.experience || [],
        projects: data.projects || [],
        certifications: data.certifications || []
      }));
      setEditMode(false);
      setActiveTab('settings');
      toast.success('Profile updated successfully!');
    } catch (err) {
      toast.error(err.response?.data || 'Failed to update profile. Your unsaved changes are still here.');
    } finally {
      setLoading(false);
    }
  };

  const handleResumeUpload = async (file) => {
    if (!file || uploadingResume) return;
    if (file.type && file.type !== 'application/pdf') {
      toast.error('Only PDF resumes are supported.');
      return;
    }
    setUploadingResume(true);
    setEditMode(true);
    setParsingStage(0);
    try {
      const res = await profileService.uploadResume(file);
      if (res?.data) {
        const parsed = res.data;
        setFormData(prev => ({
          ...prev,
          ...parsed,
          skills: parsed.skills || [],
          education: parsed.education || [],
          experience: parsed.experience || [],
          projects: parsed.projects || [],
          certifications: parsed.certifications || [],
          resumeFileName: parsed.resumeFileName || file.name
        }));
        toast.success(formData.resumeFileName ? 'Resume re-imported. Review your profile before saving.' : 'Resume analyzed. Review your profile before saving.');
      }
    } catch (err) {
      toast.error(err.response?.data || 'Resume parsing failed. Please try another PDF.');
    } finally {
      setUploadingResume(false);
    }
  };

  useEffect(() => {
    if (!uploadingResume) return undefined;
    const interval = window.setInterval(() => setParsingStage(stage => (stage + 1) % parsingStages.length), 1800);
    return () => window.clearInterval(interval);
  }, [uploadingResume, parsingStages.length]);

  const updateList = (field, index, value) => {
    setFormData(prev => ({ ...prev, [field]: prev[field].map((item, itemIndex) => itemIndex === index ? value : item) }));
  };

  const addListItem = (field) => setFormData(prev => ({ ...prev, [field]: [...prev[field], ''] }));
  const removeListItem = (field, index) => setFormData(prev => ({ ...prev, [field]: prev[field].filter((_, itemIndex) => itemIndex !== index) }));

  const renderListSection = (field, label, placeholder, compact = false) => (
    <section className="bg-slate-950/60 border border-slate-800 rounded-2xl p-5" key={field}>
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-black uppercase tracking-wider text-slate-300">{label}</h3>
        <button type="button" onClick={() => addListItem(field)} className="text-xs font-bold text-violet-400 hover:text-violet-300">+ Add</button>
      </div>
      {formData[field].length === 0 ? (
        <p className="text-sm text-slate-500">{label === 'Skills' ? 'No skills added yet' : `${label} not added yet`}</p>
      ) : (
        <div className={compact ? 'flex flex-wrap gap-2' : 'space-y-2'}>
          {formData[field].map((item, index) => (
            <div key={`${field}-${index}`} className={compact ? 'flex items-center gap-1 bg-slate-900 border border-slate-700 rounded-full pl-3 pr-1 py-1' : 'flex items-start gap-2'}>
              {compact ? (
                <input type="text" value={item} onChange={(e) => updateList(field, index, e.target.value)} className="w-24 bg-transparent text-xs text-white focus:outline-none" aria-label={`${label} item ${index + 1}`} />
              ) : (
                <textarea rows={field === 'projects' ? 2 : 1} value={item} onChange={(e) => updateList(field, index, e.target.value)} placeholder={placeholder} className="flex-1 min-w-0 px-3 py-2 bg-slate-900 border border-slate-800 rounded-xl text-white text-sm resize-y focus:border-violet-500 focus:outline-none" />
              )}
              <button type="button" onClick={() => removeListItem(field, index)} aria-label={`Remove ${label.toLowerCase()} item`} className="p-1.5 text-slate-500 hover:text-rose-400"><X className="w-4 h-4" /></button>
            </div>
          ))}
        </div>
      )}
    </section>
  );

  const renderResumeProcessor = () => (
    <section className="bg-slate-900/40 border border-slate-800 rounded-3xl p-6 space-y-3">
      <label className="block text-sm font-bold text-slate-300">AI Resume Processor (PDF)</label>
      <div className="border-2 border-dashed border-slate-800 rounded-3xl p-8 text-center bg-slate-950/50 hover:border-violet-500/50 transition-all cursor-pointer relative">
        <input type="file" accept=".pdf" onChange={(e) => { const file = e.target.files[0]; e.target.value = ''; handleResumeUpload(file); }} disabled={uploadingResume} className="absolute inset-0 opacity-0 cursor-pointer" />
        {uploadingResume ? (
          <div className="flex flex-col items-center gap-3">
            <Loader2 className="w-10 h-10 text-violet-400 animate-spin" />
            <p className="text-violet-200 font-bold text-sm">{parsingStages[parsingStage]}</p>
            <div className="w-full max-w-xs h-1.5 bg-slate-800 rounded-full overflow-hidden"><div className="h-full bg-violet-500 animate-pulse" style={{ width: `${Math.max(18, ((parsingStage + 1) / parsingStages.length) * 100)}%` }} /></div>
            <p className="text-slate-500 text-xs">Please keep this page open while we finish.</p>
          </div>
        ) : (
          <>
            <Upload className="w-10 h-10 text-violet-400 mx-auto mb-3" />
            <p className="text-slate-300 font-bold text-sm">Drop your resume PDF here or click to browse</p>
            <p className="text-slate-500 text-xs mt-1">Automatically extracts skills, experience & projects</p>
          </>
        )}
      </div>
      {formData.resumeFileName && <p className="text-xs font-mono text-emerald-400 flex items-center gap-1"><FileText className="w-3.5 h-3.5" /> Current Resume: {formData.resumeFileName}</p>}
    </section>
  );

  const displayValue = (value, empty) => value || empty;
  const renderProfileView = () => (
    <div className="space-y-6">
      <section className="bg-slate-900/40 border border-slate-800 rounded-3xl p-6">
        <div className="flex flex-col md:flex-row md:items-start justify-between gap-5">
          <div>
            <p className="text-xs font-black uppercase tracking-widest text-violet-400">Candidate Profile</p>
            <h2 className="text-3xl font-black text-white mt-2">{displayValue(`${formData.firstName} ${formData.lastName}`.trim(), 'Name not set')}</h2>
            <div className="flex flex-wrap gap-x-5 gap-y-2 text-sm text-slate-400 mt-3"><span>{displayValue(formData.email, 'Email not set')}</span><span>{displayValue(formData.phone, 'Phone not set')}</span></div>
          </div>
          <button onClick={() => setEditMode(true)} className="px-5 py-2.5 bg-violet-600 text-white rounded-xl font-bold text-sm">Edit Profile</button>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-6">
          <div className="bg-slate-950 rounded-xl p-3"><p className="text-xs text-slate-500">Target Role</p><p className="text-sm font-bold text-white mt-1">{displayValue(formData.targetRole, 'Not set yet')}</p></div>
          <div className="bg-slate-950 rounded-xl p-3"><p className="text-xs text-slate-500">Expertise Level</p><p className="text-sm font-bold text-white mt-1">{displayValue(formData.expertiseLevel, 'Not set yet')}</p></div>
          <div className="bg-slate-950 rounded-xl p-3"><p className="text-xs text-slate-500">Current Resume</p><p className="text-sm font-bold text-white mt-1 truncate">{displayValue(formData.resumeFileName, 'No resume uploaded')}</p></div>
        </div>
      </section>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className="bg-slate-900/40 border border-slate-800 rounded-3xl p-6"><h3 className="text-sm font-black uppercase tracking-wider text-slate-300 mb-3">Education</h3>{formData.education.length ? formData.education.map((item, index) => <p key={index} className="text-sm text-slate-300 border-l-2 border-violet-500 pl-3 mb-3">{item}</p>) : <p className="text-sm text-slate-500">Education not added yet</p>}</section>
        <section className="bg-slate-900/40 border border-slate-800 rounded-3xl p-6"><h3 className="text-sm font-black uppercase tracking-wider text-slate-300 mb-3">Experience</h3>{formData.experience.length ? formData.experience.map((item, index) => <p key={index} className="text-sm text-slate-300 border-l-2 border-emerald-500 pl-3 mb-3">{item}</p>) : <p className="text-sm text-slate-500">Experience not added yet</p>}</section>
      </div>
      <section className="bg-slate-900/40 border border-slate-800 rounded-3xl p-6"><h3 className="text-sm font-black uppercase tracking-wider text-slate-300 mb-3">Skills</h3>{formData.skills.length ? <div className="flex flex-wrap gap-2">{formData.skills.map((item, index) => <span key={index} className="px-3 py-1.5 bg-violet-500/10 border border-violet-500/30 rounded-full text-xs font-bold text-violet-200">{item}</span>)}</div> : <p className="text-sm text-slate-500">Skills not added yet</p>}</section>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className="bg-slate-900/40 border border-slate-800 rounded-3xl p-6"><h3 className="text-sm font-black uppercase tracking-wider text-slate-300 mb-3">Projects</h3>{formData.projects.length ? formData.projects.map((item, index) => <div key={index} className="bg-slate-950 rounded-xl p-3 text-sm text-slate-300 mb-2">{item}</div>) : <p className="text-sm text-slate-500">Projects not added yet</p>}</section>
        <section className="bg-slate-900/40 border border-slate-800 rounded-3xl p-6"><h3 className="text-sm font-black uppercase tracking-wider text-slate-300 mb-3">Certifications</h3>{formData.certifications.length ? formData.certifications.map((item, index) => <p key={index} className="text-sm text-slate-300 mb-3"><Award className="w-4 h-4 inline text-amber-400 mr-2" />{item}</p>) : <p className="text-sm text-slate-500">Certifications not added yet</p>}</section>
      </div>
    </div>
  );

  if (profileLoading) {
    return (
      <div className="min-h-screen bg-slate-950 text-white flex flex-col items-center justify-center">
        <Sparkles className="w-12 h-12 text-violet-500 animate-pulse mb-4" />
        <p className="text-slate-400 font-medium">Loading Candidate Profile Dashboard...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-white py-10 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto space-y-8">
        
        {/* Navigation Tabs Header */}
        <div className="flex items-center justify-between border-b border-slate-900 pb-6">
          <div>
            <h1 className="text-3xl font-black bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">
              {formData.firstName || user?.firstName}'s Career Profile
            </h1>
            <p className="text-slate-400 text-sm mt-1">Real-time Performance Analytics & AI Profile</p>
          </div>

          <div className="flex gap-2 bg-slate-900 p-1.5 rounded-2xl border border-slate-800">
            <button
              onClick={() => setActiveTab('dashboard')}
              className={`px-5 py-2.5 rounded-xl font-bold text-sm transition-all cursor-pointer ${
                activeTab === 'dashboard' ? 'bg-violet-600 text-white shadow-lg' : 'text-slate-400 hover:text-white'
              }`}
            >
              Career Dashboard
            </button>
            <button
              onClick={() => setActiveTab('settings')}
              className={`px-5 py-2.5 rounded-xl font-bold text-sm transition-all cursor-pointer ${
                activeTab === 'settings' ? 'bg-violet-600 text-white shadow-lg' : 'text-slate-400 hover:text-white'
              }`}
            >
              Profile & Resume
            </button>
          </div>
        </div>

        {/* TAB 1: FULL AI-POWERED CAREER DASHBOARD */}
        {activeTab === 'dashboard' && (
          <div className="space-y-8 animate-in fade-in">
            <div className="bg-gradient-to-r from-slate-900 via-slate-900/90 to-violet-950/40 border border-slate-850 rounded-3xl p-8 shadow-xl flex flex-col md:flex-row items-center justify-between gap-6">
              <div className="space-y-2 text-center md:text-left">
                <span className="text-xs font-black text-violet-400 uppercase tracking-widest">Overall Technical Readiness</span>
                <div className="flex items-baseline gap-3">
                  <span className="text-5xl font-black text-white">
                    {skillAnalysis?.overallReadinessScore != null && skillAnalysis.overallReadinessScore > 0 
                      ? `${Math.round(skillAnalysis.overallReadinessScore)}%` 
                      : 'Pending'}
                  </span>
                  <span className="text-slate-400 font-bold text-sm">Target Role: {formData.targetRole || 'Developer'}</span>
                </div>
                <p className="text-slate-400 text-sm max-w-lg">
                  {skillAnalysis?.recommendedNextAction || 'Complete an assessment or mock interview to calculate your readiness score.'}
                </p>
              </div>
              <div className="flex flex-wrap gap-3">
                <button onClick={() => window.location.assign('/assessment/start')} className="px-6 py-3.5 bg-gradient-to-r from-violet-600 to-indigo-600 text-white rounded-2xl font-black text-sm hover:opacity-95 transition-all shadow-xl cursor-pointer">
                  Take Assessment
                </button>
                <button onClick={() => setInterviewModalOpen(true)} className="px-6 py-3.5 bg-gradient-to-r from-fuchsia-600 to-violet-600 text-white rounded-2xl font-black text-sm hover:opacity-95 transition-all shadow-xl cursor-pointer flex items-center gap-2">
                  <Bot className="w-4 h-4" /> Start Mock Interview
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              <div className="bg-slate-900/40 border border-slate-850 rounded-3xl p-6 space-y-4">
                <h3 className="text-lg font-bold flex items-center gap-2"><TrendingUp className="w-5 h-5 text-violet-400" /> Skill Breakdown</h3>
                {skillAnalysis?.skillScores && Object.keys(skillAnalysis.skillScores).length > 0 ? (
                  <div className="space-y-3">
                    {Object.entries(skillAnalysis.skillScores).map(([skill, score]) => (
                      <div key={skill} className="space-y-1">
                        <div className="flex justify-between text-xs font-bold"><span>{skill}</span><span className="text-violet-400">{Math.round(score)}%</span></div>
                        <div className="w-full h-2 bg-slate-950 rounded-full overflow-hidden"><div className="h-full bg-gradient-to-r from-violet-600 to-fuchsia-600 rounded-full" style={{ width: `${Math.min(100, Math.max(0, score))}%` }} /></div>
                      </div>
                    ))}
                  </div>
                ) : <p className="text-slate-500 text-sm py-4 text-center font-medium">Not enough measured data yet</p>}
              </div>

              <div className="bg-slate-900/40 border border-slate-850 rounded-3xl p-6 space-y-4">
                <h3 className="text-lg font-bold flex items-center gap-2 text-emerald-400"><ShieldCheck className="w-5 h-5" /> Verified Strengths</h3>
                {skillAnalysis?.strongConcepts?.length > 0 ? (
                  <div className="space-y-2">{skillAnalysis.strongConcepts.map((concept, index) => <div key={index} className="p-3 bg-emerald-950/20 border border-emerald-800/30 rounded-xl text-xs font-bold text-emerald-300">✓ {concept}</div>)}</div>
                ) : <p className="text-slate-500 text-sm py-4 text-center font-medium">Not enough measured data yet</p>}
              </div>

              <div className="bg-slate-900/40 border border-slate-850 rounded-3xl p-6 space-y-4">
                <h3 className="text-lg font-bold flex items-center gap-2 text-amber-400"><AlertCircle className="w-5 h-5" /> Detected Weak Concepts</h3>
                {skillAnalysis?.weakConcepts?.length > 0 ? (
                  <div className="space-y-2">{skillAnalysis.weakConcepts.map((concept, index) => <div key={index} className="p-3 bg-amber-950/20 border border-amber-800/30 rounded-xl text-xs font-bold text-amber-300">⚠ {concept}</div>)}</div>
                ) : <p className="text-slate-500 text-sm py-4 text-center font-medium">Not enough measured data yet</p>}
              </div>
            </div>

            {skillAnalysis?.gapDetections?.length > 0 && (
              <div className="bg-slate-900/40 border border-slate-850 rounded-3xl p-6 space-y-3">
                <h3 className="text-lg font-bold text-cyan-400 flex items-center gap-2"><Search className="w-5 h-5" /> Resume vs Assessment Knowledge Gaps</h3>
                <div className="space-y-2">{skillAnalysis.gapDetections.map((gap, index) => <div key={index} className="p-4 bg-cyan-950/20 border border-cyan-800/30 rounded-2xl text-xs text-cyan-200 font-medium">{gap}</div>)}</div>
              </div>
            )}

            {/* Recent Activity Section */}
            <div className="bg-slate-900/40 border border-slate-850 rounded-3xl p-6 space-y-4">
              <h3 className="text-lg font-bold flex items-center gap-2">
                <Clock className="w-5 h-5 text-slate-400" /> Recent Activity
              </h3>
              {combinedActivities.length === 0 ? (
                <p className="text-slate-500 text-sm text-center py-4 font-medium">Not enough data yet</p>
              ) : (
                <div className="divide-y divide-slate-900">
                  {combinedActivities.slice(0, 5).map((act) => (
                    <div 
                      key={act.id} 
                      onClick={() => {
                        if (act.type === 'ASSESSMENT' && act.id) {
                          navigate(`/result/${act.id}`);
                        } else if (act.type === 'INTERVIEW' && act.id) {
                          navigate(`/interview/room/${act.id}`);
                        }
                      }}
                      className="py-3 px-3 -mx-3 rounded-2xl flex justify-between items-center text-xs transition-all cursor-pointer hover:bg-slate-800/60 group"
                      title={act.type === 'ASSESSMENT' ? 'Click to view full assessment results & AI feedback' : 'Click to view interview details'}
                    >
                      <div className="space-y-0.5">
                        <div className="flex items-center gap-2">
                          <p className="font-bold text-slate-200 group-hover:text-violet-300 transition-colors">{act.title}</p>
                          <span className={`text-[10px] px-2 py-0.5 rounded-md font-semibold ${
                            act.type === 'INTERVIEW'
                              ? 'bg-fuchsia-500/10 text-fuchsia-400 border border-fuchsia-500/20'
                              : 'bg-violet-500/10 text-violet-400 border border-violet-500/20'
                          }`}>
                            {act.type === 'INTERVIEW' ? 'Mock Interview' : 'Assessment'}
                          </span>
                        </div>
                        <p className="text-slate-400 text-[11px]">{act.subtitle}</p>
                        <div className="flex items-center gap-3 text-slate-500 text-[11px]">
                          <span>{act.displayDate}</span>
                          <span>•</span>
                          <span className={act.isExited ? 'text-amber-400 font-medium' : (act.status === 'Completed' ? 'text-emerald-400 font-medium' : 'text-slate-400')}>
                            Status: {act.status}
                          </span>
                        </div>
                      </div>
                      <div className="flex items-center gap-3 pl-4">
                        <div className="text-right">
                          <p className="text-[10px] text-slate-500 font-bold uppercase">Score</p>
                          <span className={`text-base font-black ${act.isExited ? 'text-amber-400' : 'text-violet-400'}`}>
                            {act.score}
                          </span>
                        </div>
                        <ChevronRight className="w-4 h-4 text-slate-600 group-hover:text-violet-400 group-hover:translate-x-0.5 transition-all" />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>


          </div>
        )}

        {/* TAB 2: PROFILE & RESUME SETTINGS */}
        {activeTab === 'settings' && (
          <div className="space-y-6 animate-in fade-in max-w-6xl mx-auto">
            {renderResumeProcessor()}

            {editMode ? <>
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <section className="bg-slate-900/40 border border-slate-800 rounded-3xl p-6 space-y-4">
                <h2 className="text-lg font-black flex items-center gap-2"><User className="w-5 h-5 text-violet-400" /> Personal Information</h2>
                {[
                  ['firstName', 'First Name'],
                  ['lastName', 'Last Name'],
                  ['email', 'Email'],
                  ['phone', 'Phone']
                ].map(([field, label]) => (
                  <div key={field}>
                    <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">{label}</label>
                    <input type="text" value={formData[field]} onChange={(e) => setFormData(prev => ({ ...prev, [field]: e.target.value }))} className="w-full px-4 py-3 bg-slate-950 border border-slate-800 rounded-2xl text-white text-sm focus:border-violet-500 focus:outline-none" />
                  </div>
                ))}
              </section>

              <section className="bg-slate-900/40 border border-slate-800 rounded-3xl p-6 space-y-4">
                <h2 className="text-lg font-black flex items-center gap-2"><Briefcase className="w-5 h-5 text-violet-400" /> Role Information</h2>

                <div>
                  <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">Target Role</label>
                  <input type="text" value={formData.targetRole} onChange={(e) => setFormData(prev => ({ ...prev, targetRole: e.target.value }))} placeholder="Enter a target role" className="w-full px-4 py-3 bg-slate-950 border border-slate-800 rounded-2xl text-white text-sm focus:border-violet-500 focus:outline-none" />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">Expertise Level</label>
                  <select value={formData.expertiseLevel} onChange={(e) => setFormData(prev => ({ ...prev, expertiseLevel: e.target.value }))} className="w-full px-4 py-3 bg-slate-950 border border-slate-800 rounded-2xl text-white text-sm focus:border-violet-500 focus:outline-none">
                    <option value="">Not set yet</option>
                    <option value="JUNIOR">Junior (0-2 YOE)</option>
                    <option value="INTERMEDIATE">Intermediate (2-5 YOE)</option>
                    <option value="SENIOR">Senior (5+ YOE)</option>
                  </select>
                </div>
              </section>
              </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {renderListSection('education', 'Education', 'Degree, institution, dates, grade')}
              {renderListSection('experience', 'Experience', 'Role, company, dates, responsibilities')}
            </div>
            {renderListSection('skills', 'Skills', 'Add a skill', true)}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {renderListSection('projects', 'Projects', 'Project name, description, technologies')}
              {renderListSection('certifications', 'Certifications', 'Certification, issuer, year')}
            </div>

            <button
              onClick={handleSaveProfile}
              disabled={loading}
              className="w-full py-4 bg-gradient-to-r from-violet-600 to-fuchsia-600 text-white font-black rounded-2xl hover:opacity-95 transition-all shadow-xl cursor-pointer disabled:opacity-50"
            >
              {loading ? 'Saving Profile...' : 'Save Profile Changes'}
            </button>
            </> : renderProfileView()}
          </div>
        )}

        {/* Modals */}
        <MockInterviewModal 
          isOpen={interviewModalOpen} 
          onClose={() => { setInterviewModalOpen(false); fetchAllData(); }} 
          onComplete={() => fetchAllData()} 
        />

      </div>
    </div>
  );
};

export default ProfilePage;

