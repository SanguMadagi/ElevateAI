import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
});

// Configure defaults for retry
api.defaults.retry = 3; 
api.defaults.retryDelay = 1500;

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor: handle 401 and retry logic
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { config, response } = error;
    
    // Normalize response data errors to string to protect React from rendering object children
    if (response) {
      let statusMessage = '';
      if (response.status === 404) {
        statusMessage = 'Profile not found';
      } else if (response.status === 403) {
        statusMessage = 'Access denied';
      } else if (response.status === 401) {
        statusMessage = 'Session expired';
      }

      if (response.data) {
        const data = response.data;
        if (typeof data === 'object') {
          const msg = data.message || data.error || statusMessage || JSON.stringify(data);
          const finalMsg = (msg === 'Not Found' || msg === 'Forbidden' || msg === 'Unauthorized' || msg === 'Forbidden Exception') && statusMessage ? statusMessage : msg;
          response.data = finalMsg;
          error.message = finalMsg;
        } else if (typeof data === 'string') {
          let msg = data;
          if (data.startsWith('{')) {
            try {
              const parsed = JSON.parse(data);
              msg = parsed.message || parsed.error || statusMessage || data;
            } catch (e) {}
          }
          const finalMsg = (msg === 'Not Found' || msg === 'Forbidden' || msg === 'Unauthorized' || msg === 'Forbidden Exception') && statusMessage ? statusMessage : msg;
          response.data = finalMsg;
          error.message = finalMsg;
        }
      } else {
        response.data = statusMessage || 'An unexpected error occurred';
        error.message = statusMessage || 'An unexpected error occurred';
      }
    }

    // Redirect to login if unauthorized (401), unless it's a login request or already on login page
    if (response && response.status === 401) {
      const isLoginRequest = config?.url?.includes('/api/auth/login') || window.location.pathname.includes('/login');
      if (!isLoginRequest) {
        localStorage.removeItem('authToken');
        localStorage.removeItem('authUser');
        window.location.href = '/login';
      }
      return Promise.reject(error);
    }

    if (!config || !config.retry) {
      return Promise.reject(error);
    }
    
    // Only retry on network errors or transient 5xx server errors
    const isNetworkError = !error.response;
    const is5xxError = error.response && error.response.status >= 500 && error.response.status <= 599;
    
    if (!isNetworkError && !is5xxError) {
      return Promise.reject(error);
    }

    config.__retryCount = config.__retryCount || 0;
    if (config.__retryCount >= config.retry) {
      return Promise.reject(error);
    }

    config.__retryCount += 1;
    const backoffDelay = config.retryDelay ? config.retryDelay * config.__retryCount : 1500;
    
    await new Promise((resolve) => setTimeout(resolve, backoffDelay));
    return api(config);
  }
);

export const authService = {
  login: (data) => api.post('/api/auth/login', data),
  signup: (data) => api.post('/api/auth/signup', data),
  verifyOtp: (data) => api.post('/api/auth/verify-otp', data),
  resendOtp: (email) => api.post(`/api/auth/resend-otp?email=${encodeURIComponent(email)}`),
  forgotPassword: (data) => api.post('/api/auth/forgot-password', data),
  resetPassword: (data) => api.post('/api/auth/reset-password', data),
  getCurrentUser: () => api.get('/api/auth/me'),
};

export const profileService = {
  getProfile: () => api.get('/api/profile/me'),
  saveProfile: (data) => api.post('/api/profile/create', data),
  uploadResume: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post('/api/profile/upload-resume', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
};

export const testService = {
  startTest: () => api.post('/api/test/start', null, { retry: 0, timeout: 60000 }),
  getTestSession: (testId) => api.get(`/api/test/${testId}`),
  saveAnswer: (testId, data) => api.post(`/api/test/${testId}/save-answer`, data),
  submitViolation: (testId, data) => api.post(`/api/test/${testId}/submit-violation`, data),
  enrollIdentity: (testId, embedding) => api.post(`/api/test/${testId}/identity/enroll`, { embedding }),
  recordIdentityCheck: (testId, status, faceCount) => api.post(`/api/test/${testId}/identity/check`, { status, faceCount }),
  submitTest: (testId, payload) => api.post(`/api/test/${testId}/submit`, payload, { retry: 0 }),
  getEvaluationStatus: (testId) => api.get(`/api/test/${testId}/evaluation-status`),
  exitTest: (testId) => api.post(`/api/test/${testId}/exit`),
  getResult: (testId) => api.get(`/api/test/${testId}/result`),
  getMyTests: () => api.get('/api/test/my-tests'),
  transcribeAudio: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post('/api/test/transcribe-audio', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
};

export const interviewService = {
  startInterview: (topic) => api.post('/api/interview/start', { topic }),
  submitAnswer: (sessionId, answer) => api.post(`/api/interview/${sessionId}/answer`, { answer }),
  getSession: (sessionId) => api.get(`/api/interview/${sessionId}`),
  getMyInterviews: () => api.get('/api/interview/my-interviews'),
  transcribeAudio: (audioBase64, mimeType) => api.post('/api/interview/transcribe', { audio: audioBase64, mimeType }),
  exitInterview: (sessionId) => api.post(`/api/interview/${sessionId}/exit`),
};

export const skillService = {
  getMyAnalysis: () => api.get('/api/skills/me'),
  reanalyze: () => api.post('/api/skills/reanalyze'),
};

export const agentService = {
  chat: (data) => api.post('/api/agent/chat', typeof data === 'string' ? { message: data } : data),
  getConversations: () => api.get('/api/agent/conversations'),
  getConversationMessages: (conversationId) => api.get(`/api/agent/conversations/${conversationId}`),
  deleteConversation: (conversationId) => api.delete(`/api/agent/conversations/${conversationId}`),
  createConversation: () => api.post('/api/agent/conversations'),
  renameConversation: (conversationId, title) => api.patch(`/api/agent/conversations/${conversationId}/rename`, { title }),
};

export const workspaceService = {
  generate: () => api.post('/api/workspace/generate'),
  createCustomFile: ({ fileName, folder, prompt }) => api.post('/api/workspace/custom-file', { fileName, folder, prompt }),
  listFiles: () => api.get('/api/workspace/files'),
  readFile: (path) => api.get('/api/workspace/file', { params: { path } }),
  deleteFile: (path) => api.delete('/api/workspace/file', { params: { path } }),
  clearWorkspace: () => api.delete('/api/workspace/clear'),
};

export default api;

