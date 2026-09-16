import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Toaster } from 'react-hot-toast';
import Landing from './pages/Landing';
import Login from './pages/Login';
import Signup from './pages/Signup';
import OtpVerification from './pages/OtpVerification';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import Onboarding from './pages/Onboarding';
import Dashboard from './pages/Dashboard';
import AssessmentStartPage from './pages/AssessmentStartPage';
import TestModule from './pages/TestModule';
import ResultPage from './pages/ResultPage';
import ProfilePage from './pages/ProfilePage';
import CareerAgentPage from './pages/CareerAgentPage';
import InterviewRoomPage from './pages/InterviewRoomPage';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';

const GuestRoute = ({ children }) => {
  const { user, loading } = useAuth();
  if (loading) return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-primary-600"></div>
    </div>
  );
  if (user) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
};

function App() {
  return (
    <AuthProvider>
      <Router>
        <Toaster 
          position="top-right" 
          toastOptions={{
            style: {
              background: '#0f172a',
              color: '#f8fafc',
              border: '1px solid #1e293b'
            }
          }}
        />
        <div className="min-h-screen bg-slate-950 text-slate-100 selection:bg-violet-500 selection:text-white">
          <Routes>
            <Route path="/" element={<Landing />} />
            
            <Route path="/login" element={
              <GuestRoute><Login /></GuestRoute>
            } />
            <Route path="/signup" element={
              <GuestRoute><Signup /></GuestRoute>
            } />
            <Route path="/verify-otp" element={
              <GuestRoute><OtpVerification /></GuestRoute>
            } />
            <Route path="/forgot-password" element={
              <GuestRoute><ForgotPassword /></GuestRoute>
            } />
            <Route path="/reset-password" element={
              <GuestRoute><ResetPassword /></GuestRoute>
            } />
            
            <Route path="/onboarding" element={
              <ProtectedRoute><Onboarding /></ProtectedRoute>
            } />
            
            <Route path="/dashboard" element={
              <ProtectedRoute>
                <>
                  <Navbar />
                  <Dashboard />
                </>
              </ProtectedRoute>
            } />

            <Route path="/assessment/start" element={
              <ProtectedRoute>
                <>
                  <Navbar />
                  <AssessmentStartPage />
                </>
              </ProtectedRoute>
            } />

            <Route path="/assessment/:testId" element={
              <ProtectedRoute><TestModule /></ProtectedRoute>
            } />

            <Route path="/result/:testId" element={
              <ProtectedRoute>
                <>
                  <Navbar />
                  <ResultPage />
                </>
              </ProtectedRoute>
            } />

            <Route path="/career-agent" element={
              <ProtectedRoute>
                <CareerAgentPage />
              </ProtectedRoute>
            } />

            <Route path="/career-agent/:conversationId" element={
              <ProtectedRoute>
                <CareerAgentPage />
              </ProtectedRoute>
            } />

            <Route path="/profile" element={
              <ProtectedRoute>
                <>
                  <Navbar />
                  <ProfilePage />
                </>
              </ProtectedRoute>
            } />

            <Route path="/interview/room/:sessionId" element={
              <ProtectedRoute>
                <InterviewRoomPage />
              </ProtectedRoute>
            } />
          </Routes>
        </div>
      </Router>
    </AuthProvider>
  );
}

export default App;
