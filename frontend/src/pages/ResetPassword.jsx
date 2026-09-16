import { useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { authService } from '../services/api';
import { Brain, Lock, Hash, ArrowLeft, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  const email = searchParams.get('email') || '';
  const navigate = useNavigate();

  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [loading, setLoading] = useState(false);

  const validateForm = () => {
    const newErrors = {};
    if (!otp || otp.length < 6) {
      newErrors.otp = 'Please enter the 6-digit OTP';
    }
    if (!newPassword) {
      newErrors.newPassword = 'New password is required';
    } else if (newPassword.length < 6) {
      newErrors.newPassword = 'Password must be at least 6 characters long';
    }
    if (newPassword !== confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError('');
    if (!validateForm()) return;

    setLoading(true);
    try {
      await authService.resetPassword({
        email: email.trim(),
        otp: otp.trim(),
        newPassword: newPassword
      });
      
      toast.success('Password reset successfully!');
      navigate('/login', { replace: true });
    } catch (err) {
      console.error(err);
      const errorMessage = err.response?.data?.error || err.response?.data?.message || err.message || 'Reset failed. Please verify your OTP and try again.';
      setServerError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  if (!email) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-950 px-6">
        <div className="text-center p-10 bg-slate-900 border border-slate-850 rounded-3xl shadow-2xl max-w-md space-y-6">
          <p className="text-xl font-bold text-slate-300">Invalid access. Missing target email address.</p>
          <Link to="/forgot-password" className="inline-block px-8 py-3 bg-violet-600 text-white rounded-xl font-bold hover:bg-violet-500 transition-all uppercase text-xs tracking-wider">
            Go to Forgot Password
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-slate-950 px-6 py-12">
      <div className="w-full max-w-md p-10 bg-slate-900 border border-slate-850 rounded-3xl shadow-2xl relative overflow-hidden">
        
        {/* Decorative background glow */}
        <div className="absolute top-0 right-0 -mt-12 -mr-12 w-32 h-32 bg-violet-600/10 rounded-full blur-2xl pointer-events-none" />
        <div className="absolute bottom-0 left-0 -mb-12 -ml-12 w-32 h-32 bg-fuchsia-600/10 rounded-full blur-2xl pointer-events-none" />

        <div className="flex flex-col items-center mb-8 text-center relative">
          <div className="w-14 h-14 bg-gradient-to-tr from-violet-600 to-fuchsia-600 rounded-2xl flex items-center justify-center mb-4 shadow-lg shadow-violet-500/10">
            <Brain className="w-8 h-8 text-white animate-pulse" />
          </div>
          <h2 className="text-3xl font-black bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">Reset Password</h2>
          <p className="text-slate-400 text-sm mt-2 font-medium">Reset password for <span className="text-violet-400 font-bold">{email}</span></p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5 relative">
          {/* OTP field */}
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">6-Digit OTP</label>
            <div className="relative">
              <Hash className="absolute left-4 top-3 w-5 h-5 text-slate-500" />
              <input
                type="text"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                maxLength={6}
                className={`w-full pl-12 pr-4 py-2.5 bg-slate-950 border ${errors.otp ? 'border-rose-500' : 'border-slate-800 focus:border-violet-500'} rounded-xl text-white outline-none transition-all font-medium text-sm`}
                placeholder="000000"
                inputMode="numeric"
              />
            </div>
            {errors.otp && <p className="text-rose-500 text-xs font-semibold">{errors.otp}</p>}
          </div>

          {/* New Password field */}
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">New Password</label>
            <div className="relative">
              <Lock className="absolute left-4 top-3 w-5 h-5 text-slate-500" />
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className={`w-full pl-12 pr-4 py-2.5 bg-slate-950 border ${errors.newPassword ? 'border-rose-500' : 'border-slate-800 focus:border-violet-500'} rounded-xl text-white outline-none transition-all font-medium text-sm`}
                placeholder="••••••••"
              />
            </div>
            {errors.newPassword && <p className="text-rose-500 text-xs font-semibold">{errors.newPassword}</p>}
          </div>

          {/* Confirm New Password field */}
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">Confirm New Password</label>
            <div className="relative">
              <Lock className="absolute left-4 top-3 w-5 h-5 text-slate-500" />
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className={`w-full pl-12 pr-4 py-2.5 bg-slate-950 border ${errors.confirmPassword ? 'border-rose-500' : 'border-slate-800 focus:border-violet-500'} rounded-xl text-white outline-none transition-all font-medium text-sm`}
                placeholder="••••••••"
              />
            </div>
            {errors.confirmPassword && <p className="text-rose-500 text-xs font-semibold">{errors.confirmPassword}</p>}
          </div>

          {/* Form-level Server Error */}
          {serverError && (
            <div className="p-3.5 bg-rose-950/40 border border-rose-900/30 rounded-xl text-xs font-semibold text-rose-400 animate-in fade-in slide-in-from-top-1 duration-200">
              {serverError}
            </div>
          )}

          {/* Submit button */}
          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 text-white bg-violet-600 hover:bg-violet-500 rounded-xl font-bold text-sm tracking-wider uppercase transition-all shadow-lg shadow-violet-500/10 disabled:opacity-50 flex items-center justify-center gap-2 cursor-pointer"
          >
            {loading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" /> Resetting...
              </>
            ) : (
              'Reset Password'
            )}
          </button>
        </form>

        <div className="mt-8 text-center">
          <Link to="/login" className="inline-flex items-center gap-2 text-xs font-bold text-slate-400 hover:text-white transition-colors uppercase tracking-wider">
            <ArrowLeft className="w-4 h-4" /> Back to Login
          </Link>
        </div>
      </div>
    </div>
  );
};

export default ResetPassword;
