import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { profileService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { FileText, ChevronRight, CheckCircle, Brain, Upload, X, Briefcase, GraduationCap, Code, Rocket, ArrowLeft } from 'lucide-react';
import toast from 'react-hot-toast';

const Onboarding = () => {
  const navigate = useNavigate();
  const { updateUser, user } = useAuth();
  
  const [step, setStep] = useState(() => {
    const saved = localStorage.getItem('onboarding_step');
    return saved ? parseInt(saved, 10) : 1;
  });
  
  const [loading, setLoading] = useState(false);
  const [profileData, setProfileData] = useState(() => {
    const saved = localStorage.getItem('onboarding_profile');
    return saved ? JSON.parse(saved) : {
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      skills: [],
      technologies: [],
      education: [],
      experience: [],
      projects: [],
      suggestedRole: '',
      expertiseLevel: '',
      resumeUrl: '',
      yearsOfExperience: 0,
      currentCompany: ''
    };
  });

  useEffect(() => {
    if (user) {
      setProfileData(prev => ({
        ...prev,
        firstName: prev.firstName || user.firstName || '',
        lastName: prev.lastName || user.lastName || '',
        email: prev.email || user.email || ''
      }));
    }
  }, [user]);

  useEffect(() => {
    localStorage.setItem('onboarding_step', step.toString());
  }, [step]);

  useEffect(() => {
    localStorage.setItem('onboarding_profile', JSON.stringify(profileData));
  }, [profileData]);

  const cleanOnboardingStorage = () => {
    localStorage.removeItem('onboarding_step');
    localStorage.removeItem('onboarding_profile');
  };
  
  const fileInputRef = useRef(null);

  const handleFileUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (file.type !== 'application/pdf') {
      toast.error('Please upload a PDF file');
      return;
    }

    setLoading(true);
    try {
      const { data } = await profileService.uploadResume(file);
      const extractedTechnologies = data.technologies || data.resumeTechnologies || data.skills || [];
      setProfileData({
        ...profileData,
        ...data,
        firstName: data.firstName || profileData.firstName || user?.firstName || '',
        lastName: data.lastName || profileData.lastName || user?.lastName || '',
        email: data.email || profileData.email || user?.email || '',
        phone: data.phone || profileData.phone || '',
        skills: data.skills || [],
        technologies: extractedTechnologies,
        education: data.education || [],
        experience: data.experience || [],
        projects: data.projects || [],
        suggestedRole: data.suggestedRole || data.targetRole || profileData.suggestedRole || '',
        expertiseLevel: data.expertiseLevel || profileData.expertiseLevel || '',
        resumeUrl: data.resumeUrl || '',
        yearsOfExperience: data.yearsOfExperience || profileData.yearsOfExperience || 0,
        currentCompany: data.currentCompany || profileData.currentCompany || ''
      });
      if (data.aiAnalysisFailed) {
        toast.error('AI analysis temporarily unavailable. Resume uploaded successfully.', { duration: 6000 });
      } else {
        toast.success('Resume analyzed successfully!');
      }
      setStep(2);
    } catch (err) {
      toast.error('Failed to parse resume. Please enter details manually.');
      setStep(2);
    } finally {
      setLoading(false);
    }
  };

  const handlePhoneValidation = (phone) => {
    if (!phone) return true;
    const phoneRegex = /^\+\d{1,4}\s\d{6,14}$/;
    return phoneRegex.test(phone);
  };

  const handleStep2Next = () => {
    if (!profileData.firstName?.trim() || !profileData.lastName?.trim()) {
      toast.error('First Name and Last Name are required');
      return;
    }
    if (profileData.phone && !handlePhoneValidation(profileData.phone)) {
      toast.error('Phone number format must be: +[countryCode] [number] (e.g. +91 9876543210)');
      return;
    }
    setStep(3);
  };

  const handleSaveProfile = async () => {
    setLoading(true);
    try {
      const finalPayload = {
        ...profileData,
        name: `${profileData.firstName} ${profileData.lastName}`,
        targetRole: profileData.suggestedRole,
        technologies: profileData.technologies && profileData.technologies.length > 0 ? profileData.technologies : (profileData.skills.length > 0 ? profileData.skills : []),
        resumeTechnologies: profileData.technologies && profileData.technologies.length > 0 ? profileData.technologies : (profileData.skills.length > 0 ? profileData.skills : [])
      };
      const { data } = await profileService.saveProfile(finalPayload);
      if (data) {
        updateUser({ 
          name: data.name,
          firstName: data.firstName,
          lastName: data.lastName,
          email: data.email
        });
      }
      setStep(4);
    } catch (err) {
      toast.error(err.response?.data || err.message || 'Failed to save profile.');
    } finally {
      setLoading(false);
    }
  };

  const handleFinish = () => {
    cleanOnboardingStorage();
    toast.success('Profile completed successfully!');
    navigate('/dashboard');
  };

  const removeListItem = (listName, index) => {
    const updatedList = [...profileData[listName]];
    updatedList.splice(index, 1);
    setProfileData({ ...profileData, [listName]: updatedList });
  };

  const addListItem = (listName, placeholder) => {
    const val = prompt(`Enter ${placeholder}:`);
    if (!val || !val.trim()) return;
    setProfileData({ ...profileData, [listName]: [...profileData[listName], val.trim()] });
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gray-50 px-6 py-12">
      <div className="w-full max-w-4xl bg-white rounded-3xl shadow-xl border border-gray-100 overflow-hidden">
        
        {/* Progress Header */}
        <div className="bg-primary-600 p-8 text-white">
          <div className="flex items-center justify-between max-w-3xl mx-auto relative">
            {/* Step 1: Upload */}
            <div className={`flex flex-col items-center z-10 transition-all duration-300 ${step >= 1 ? 'opacity-100' : 'opacity-50'}`}>
              <div className={`w-12 h-12 rounded-2xl flex items-center justify-center mb-2 shadow-lg ${step === 1 ? 'bg-white text-primary-600 scale-110' : 'bg-primary-500 text-white'}`}>
                <Upload className="w-6 h-6" />
              </div>
              <span className="text-xs font-bold uppercase tracking-wider">Resume Upload</span>
            </div>
            
            {/* Progress line 1 to 2 */}
            <div className="flex-1 h-0.5 bg-primary-500 absolute left-[12%] right-[66%] top-6 -z-0">
               <div className={`h-full bg-white transition-all duration-500 ${step > 1 ? 'w-full' : 'w-0'}`}></div>
            </div>

            {/* Step 2: Personal */}
            <div className={`flex flex-col items-center z-10 transition-all duration-300 ${step >= 2 ? 'opacity-100' : 'opacity-50'}`}>
              <div className={`w-12 h-12 rounded-2xl flex items-center justify-center mb-2 shadow-lg ${step === 2 ? 'bg-white text-primary-600 scale-110' : 'bg-primary-500 text-white'}`}>
                <FileText className="w-6 h-6" />
              </div>
              <span className="text-xs font-bold uppercase tracking-wider">Personal & Role</span>
            </div>

            {/* Progress line 2 to 3 */}
            <div className="flex-1 h-0.5 bg-primary-500 absolute left-[45%] right-[33%] top-6 -z-0">
               <div className={`h-full bg-white transition-all duration-500 ${step > 2 ? 'w-full' : 'w-0'}`}></div>
            </div>

            {/* Step 3: Skills & Experience */}
            <div className={`flex flex-col items-center z-10 transition-all duration-300 ${step >= 3 ? 'opacity-100' : 'opacity-50'}`}>
              <div className={`w-12 h-12 rounded-2xl flex items-center justify-center mb-2 shadow-lg ${step === 3 ? 'bg-white text-primary-600 scale-110' : 'bg-primary-500 text-white'}`}>
                <Briefcase className="w-6 h-6" />
              </div>
              <span className="text-xs font-bold uppercase tracking-wider">Skills & Background</span>
            </div>

            {/* Progress line 3 to 4 */}
            <div className="flex-1 h-0.5 bg-primary-500 absolute left-[77%] right-[10%] top-6 -z-0">
               <div className={`h-full bg-white transition-all duration-500 ${step > 3 ? 'w-full' : 'w-0'}`}></div>
            </div>

            {/* Step 4: Complete */}
            <div className={`flex flex-col items-center z-10 transition-all duration-300 ${step >= 4 ? 'opacity-100' : 'opacity-50'}`}>
              <div className={`w-12 h-12 rounded-2xl flex items-center justify-center mb-2 shadow-lg ${step === 4 ? 'bg-white text-primary-600 scale-110' : 'bg-primary-500 text-white'}`}>
                <CheckCircle className="w-6 h-6" />
              </div>
              <span className="text-xs font-bold uppercase tracking-wider">Complete</span>
            </div>
          </div>
        </div>

        <div className="p-10">
          {step === 1 && (
            <div className="max-w-xl mx-auto text-center space-y-8 py-10">
              <div className="space-y-4">
                <h2 className="text-4xl font-black text-gray-900">Upload Your Resume</h2>
                <p className="text-gray-500 text-lg">We'll parse your skills, experiences, and background to automatically build your profile.</p>
              </div>

              <div 
                onClick={() => fileInputRef.current.click()}
                className={`group border-3 border-dashed border-gray-200 rounded-3xl p-12 transition-all cursor-pointer hover:border-primary-500 hover:bg-primary-50/30 flex flex-col items-center justify-center space-y-4 ${loading ? 'opacity-50 pointer-events-none' : ''}`}
              >
                <input 
                  type="file" 
                  ref={fileInputRef} 
                  onChange={handleFileUpload} 
                  className="hidden" 
                  accept=".pdf"
                />
                <div className="w-20 h-20 bg-gray-100 rounded-full flex items-center justify-center group-hover:bg-primary-100 group-hover:text-primary-600 transition-all">
                  <Upload className="w-10 h-10 text-gray-400 group-hover:text-primary-600" />
                </div>
                <div className="space-y-1">
                  <p className="text-xl font-bold text-gray-700">Browse Resume PDF</p>
                  <p className="text-gray-400">Drag and drop or select files (Max 5MB)</p>
                </div>
              </div>

              {loading && (
                <div className="flex items-center justify-center gap-3 text-primary-600 font-bold animate-pulse">
                  <Brain className="w-6 h-6 animate-bounce" />
                  <span>AI is parsing and extracting your profile data...</span>
                </div>
              )}

              <button 
                onClick={() => setStep(2)} 
                className="text-gray-400 font-bold hover:text-primary-600 transition-all cursor-pointer"
              >
                Skip and enter manually
              </button>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-10">
              <div className="flex items-center justify-between border-b border-gray-100 pb-4">
                <div>
                  <h2 className="text-3xl font-black text-gray-900">Personal & Role Details</h2>
                  <p className="text-gray-500 mt-1">Review and update your contact info and career targets.</p>
                </div>
                <div className="flex gap-3">
                  <button 
                    onClick={() => setStep(1)}
                    className="px-5 py-3 border border-gray-200 text-gray-600 rounded-2xl font-bold flex items-center gap-2 hover:bg-gray-50 transition-all cursor-pointer"
                  >
                    <ArrowLeft className="w-4 h-4" /> Back
                  </button>
                  <button 
                    onClick={handleStep2Next}
                    className="px-8 py-3 bg-primary-600 text-white rounded-2xl font-black flex items-center gap-2 hover:bg-primary-700 shadow-xl shadow-primary-200 transition-all cursor-pointer"
                  >
                    Next Step <ChevronRight className="w-5 h-5" />
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                {/* Basic Details */}
                <div className="space-y-4 bg-gray-50 p-6 rounded-3xl border border-gray-100">
                  <h3 className="text-sm font-black uppercase tracking-wider text-primary-600">Contact Details</h3>
                  
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-1">
                      <label className="text-xs font-bold text-gray-500">First Name</label>
                      <input 
                        type="text" 
                        value={profileData.firstName}
                        onChange={(e) => setProfileData({ ...profileData, firstName: e.target.value })}
                        className="w-full bg-white border border-gray-200 rounded-xl p-3 text-sm focus:outline-none focus:border-primary-500"
                        placeholder="John"
                      />
                    </div>
                    <div className="space-y-1">
                      <label className="text-xs font-bold text-gray-500">Last Name</label>
                      <input 
                        type="text" 
                        value={profileData.lastName}
                        onChange={(e) => setProfileData({ ...profileData, lastName: e.target.value })}
                        className="w-full bg-white border border-gray-200 rounded-xl p-3 text-sm focus:outline-none focus:border-primary-500"
                        placeholder="Doe"
                      />
                    </div>
                  </div>

                  <div className="space-y-1">
                    <label className="text-xs font-bold text-gray-500">Email Address</label>
                    <input 
                      type="email" 
                      value={profileData.email}
                      onChange={(e) => setProfileData({ ...profileData, email: e.target.value })}
                      className="w-full bg-white border border-gray-200 rounded-xl p-3 text-sm focus:outline-none focus:border-primary-500"
                      placeholder="john.doe@example.com"
                    />
                  </div>

                  <div className="space-y-1">
                    <label className="text-xs font-bold text-gray-500">Phone (Format: +91 9876543210)</label>
                    <input 
                      type="text" 
                      value={profileData.phone}
                      onChange={(e) => setProfileData({ ...profileData, phone: e.target.value })}
                      className={`w-full bg-white border ${profileData.phone && !handlePhoneValidation(profileData.phone) ? 'border-red-500' : 'border-gray-200'} rounded-xl p-3 text-sm focus:outline-none focus:border-primary-500`}
                      placeholder="+91 9876543210"
                    />
                    {profileData.phone && !handlePhoneValidation(profileData.phone) && (
                      <p className="text-red-500 text-[10px] font-bold">Must match: +[countryCode] [number] with a space</p>
                    )}
                  </div>
                </div>

                {/* Job Targeting */}
                <div className="space-y-4 bg-gray-50 p-6 rounded-3xl border border-gray-100">
                  <h3 className="text-sm font-black uppercase tracking-wider text-primary-600">Career Targets</h3>
                  
                  <div className="space-y-1">
                    <label className="text-xs font-bold text-gray-500">Target Role</label>
                    <input 
                      type="text" 
                      value={profileData.suggestedRole}
                      onChange={(e) => setProfileData({ ...profileData, suggestedRole: e.target.value })}
                      className="w-full bg-white border border-gray-200 rounded-xl p-3 text-sm focus:outline-none focus:border-primary-500"
                      placeholder="e.g. Java Backend Developer"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-xs font-bold text-gray-500 block">Expertise Level</label>
                    <div className="grid grid-cols-3 gap-2">
                      {['BEGINNER', 'INTERMEDIATE', 'SENIOR'].map((level) => (
                        <button
                          type="button"
                          key={level}
                          onClick={() => setProfileData({ ...profileData, expertiseLevel: level })}
                          className={`p-3 text-xs font-black rounded-xl border transition-all cursor-pointer ${profileData.expertiseLevel === level ? 'bg-primary-600 text-white border-transparent' : 'bg-white text-gray-700 border-gray-200 hover:border-primary-200'}`}
                        >
                          {level}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="space-y-10">
              <div className="flex items-center justify-between border-b border-gray-100 pb-4">
                <div>
                  <h2 className="text-3xl font-black text-gray-900">Skills & Background</h2>
                  <p className="text-gray-500 mt-1">Refine your technical skills, work experience history, and educational background.</p>
                </div>
                <div className="flex gap-3">
                  <button 
                    onClick={() => setStep(2)}
                    className="px-5 py-3 border border-gray-200 text-gray-600 rounded-2xl font-bold flex items-center gap-2 hover:bg-gray-50 transition-all cursor-pointer"
                  >
                    <ArrowLeft className="w-4 h-4" /> Back
                  </button>
                  <button 
                    onClick={handleSaveProfile}
                    disabled={loading}
                    className="px-8 py-3 bg-primary-600 text-white rounded-2xl font-black flex items-center gap-2 hover:bg-primary-700 shadow-xl shadow-primary-200 transition-all cursor-pointer disabled:opacity-50"
                  >
                    {loading ? 'Saving...' : 'Save & Finish'} <ChevronRight className="w-5 h-5" />
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                {/* Years of Exp & Company */}
                <div className="space-y-4 bg-gray-50 p-6 rounded-3xl border border-gray-100 md:col-span-2">
                  <h3 className="text-sm font-black uppercase tracking-wider text-primary-600">Background Summary</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div className="space-y-1">
                      <label className="text-xs font-bold text-gray-500">Years of Experience</label>
                      <input 
                        type="number" 
                        value={profileData.yearsOfExperience || 0}
                        onChange={(e) => setProfileData({ ...profileData, yearsOfExperience: parseInt(e.target.value, 10) || 0 })}
                        className="w-full bg-white border border-gray-200 rounded-xl p-3 text-sm focus:outline-none focus:border-primary-500"
                        min="0"
                      />
                    </div>
                    <div className="space-y-1">
                      <label className="text-xs font-bold text-gray-500">Current/Most Recent Company</label>
                      <input 
                        type="text" 
                        value={profileData.currentCompany || ''}
                        onChange={(e) => setProfileData({ ...profileData, currentCompany: e.target.value })}
                        className="w-full bg-white border border-gray-200 rounded-xl p-3 text-sm focus:outline-none focus:border-primary-500"
                        placeholder="e.g. Acme Corp"
                      />
                    </div>
                  </div>
                </div>

                {/* Skills */}
                <div className="space-y-4 bg-gray-50 p-6 rounded-3xl border border-gray-100 md:col-span-2">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-black uppercase tracking-wider text-primary-600 flex items-center gap-1.5"><Code className="w-4 h-4" /> Technical Skills</h3>
                    <button 
                      onClick={() => addListItem('skills', 'skill')}
                      className="text-xs font-black text-primary-600 hover:underline cursor-pointer"
                    >
                      + Add Custom Skill
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {profileData.skills && profileData.skills.length > 0 ? (
                      profileData.skills.map((skill, i) => (
                        <span key={i} className="px-3 py-1.5 bg-white text-gray-800 rounded-xl text-xs font-bold border border-gray-200 flex items-center gap-2 shadow-sm">
                          {skill}
                          <X onClick={() => removeListItem('skills', i)} className="w-3.5 h-3.5 text-gray-400 hover:text-red-500 cursor-pointer" />
                        </span>
                      ))
                    ) : (
                      <p className="text-gray-400 text-xs italic">No skills loaded. Click above to add some manually.</p>
                    )}
                  </div>
                </div>

                {/* Technologies */}
                <div className="space-y-4 bg-gray-50 p-6 rounded-3xl border border-gray-100 md:col-span-2">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-black uppercase tracking-wider text-primary-600 flex items-center gap-1.5"><Code className="w-4 h-4" /> Technologies</h3>
                    <button 
                      onClick={() => addListItem('technologies', 'technology')}
                      className="text-xs font-black text-primary-600 hover:underline cursor-pointer"
                    >
                      + Add Custom Technology
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {profileData.technologies && profileData.technologies.length > 0 ? (
                      profileData.technologies.map((tech, i) => (
                        <span key={i} className="px-3 py-1.5 bg-white text-gray-800 rounded-xl text-xs font-bold border border-gray-200 flex items-center gap-2 shadow-sm">
                          {tech}
                          <X onClick={() => removeListItem('technologies', i)} className="w-3.5 h-3.5 text-gray-400 hover:text-red-500 cursor-pointer" />
                        </span>
                      ))
                    ) : (
                      <p className="text-gray-400 text-xs italic">No technologies loaded. Click above to add some manually.</p>
                    )}
                  </div>
                </div>

                {/* Experience */}
                <div className="space-y-4 bg-gray-50 p-6 rounded-3xl border border-gray-100 md:col-span-2">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-black uppercase tracking-wider text-primary-600 flex items-center gap-1.5"><Briefcase className="w-4 h-4" /> Experience Records</h3>
                    <button 
                      onClick={() => addListItem('experience', 'work experience record')}
                      className="text-xs font-black text-primary-600 hover:underline cursor-pointer"
                    >
                      + Add Record
                    </button>
                  </div>
                  <div className="space-y-2">
                    {profileData.experience && profileData.experience.length > 0 ? (
                      profileData.experience.map((exp, i) => (
                        <div key={i} className="bg-white p-3.5 rounded-xl border border-gray-200 flex items-start justify-between gap-3 group relative">
                          <p className="text-sm text-gray-700 font-medium leading-relaxed">{exp}</p>
                          <button 
                            onClick={() => removeListItem('experience', i)}
                            className="text-gray-400 hover:text-red-500 p-1 shrink-0 rounded hover:bg-gray-50"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      ))
                    ) : (
                      <p className="text-gray-400 text-xs italic">No experience records loaded.</p>
                    )}
                  </div>
                </div>

                {/* Education */}
                <div className="space-y-4 bg-gray-50 p-6 rounded-3xl border border-gray-100 md:col-span-2">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-black uppercase tracking-wider text-primary-600 flex items-center gap-1.5"><GraduationCap className="w-4 h-4" /> Education</h3>
                    <button 
                      onClick={() => addListItem('education', 'education record')}
                      className="text-xs font-black text-primary-600 hover:underline cursor-pointer"
                    >
                      + Add Record
                    </button>
                  </div>
                  <div className="space-y-2">
                    {profileData.education && profileData.education.length > 0 ? (
                      profileData.education.map((edu, i) => (
                        <div key={i} className="bg-white p-3.5 rounded-xl border border-gray-200 flex items-start justify-between gap-3 group">
                          <p className="text-sm text-gray-700 font-medium leading-relaxed">{edu}</p>
                          <button 
                            onClick={() => removeListItem('education', i)}
                            className="text-gray-400 hover:text-red-500 p-1 shrink-0 rounded hover:bg-gray-50"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      ))
                    ) : (
                      <p className="text-gray-400 text-xs italic">No education records loaded.</p>
                    )}
                  </div>
                </div>

                {/* Projects */}
                <div className="space-y-4 bg-gray-50 p-6 rounded-3xl border border-gray-100 md:col-span-2">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-black uppercase tracking-wider text-primary-600 flex items-center gap-1.5"><Rocket className="w-4 h-4" /> Projects</h3>
                    <button 
                      onClick={() => addListItem('projects', 'project details')}
                      className="text-xs font-black text-primary-600 hover:underline cursor-pointer"
                    >
                      + Add Project
                    </button>
                  </div>
                  <div className="space-y-2.5">
                    {profileData.projects && profileData.projects.length > 0 ? (
                      profileData.projects.map((proj, i) => (
                        <div key={i} className="bg-white p-4 rounded-xl border border-gray-200 flex items-start justify-between gap-3 group">
                          <p className="text-sm text-gray-700 font-medium leading-relaxed">{proj}</p>
                          <button 
                            onClick={() => removeListItem('projects', i)}
                            className="text-gray-400 hover:text-red-500 p-1 shrink-0 rounded hover:bg-gray-50"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      ))
                    ) : (
                      <p className="text-gray-400 text-xs italic">No projects loaded.</p>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          {step === 4 && (
            <div className="max-w-xl mx-auto text-center space-y-8 py-14">
              <div className="w-24 h-24 bg-green-50 text-green-600 rounded-full flex items-center justify-center mx-auto shadow-inner">
                <CheckCircle className="w-12 h-12" />
              </div>
              
              <div className="space-y-4">
                <h2 className="text-4xl font-black text-gray-900">Profile Complete!</h2>
                <p className="text-gray-500 text-lg">Your candidate profile details have been successfully saved. You are ready to start taking evaluations.</p>
              </div>

              <button
                onClick={handleFinish}
                className="w-full py-5 bg-primary-600 text-white rounded-2xl font-black text-xl shadow-2xl shadow-primary-200 hover:bg-primary-700 transition-all flex items-center justify-center gap-3 cursor-pointer"
              >
                Launch Dashboard <Rocket className="w-6 h-6" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Onboarding;
