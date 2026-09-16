import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authService } from '../services/api';
import { Brain, Lock, Mail, Eye, EyeOff, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const validateForm = () => {
    const newErrors = {};
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!email.trim()) {
      newErrors.email = 'Email address is required';
    } else if (!emailRegex.test(email.trim())) {
      newErrors.email = 'Please enter a valid email address';
    }
    if (!password) {
      newErrors.password = 'Password is required';
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
      const { data } = await authService.login({ email: email.trim(), password });
      
      // Store token & user in context and localStorage
      login(data.token, data.user);
      
      toast.success('Welcome back!');
      navigate('/dashboard', { replace: true });
    } catch (err) {
      console.error(err);
      let message = "Invalid email or password";
      if (err.response) {
        if (typeof err.response.data === 'string') {
          message = err.response.data;
        } else if (err.response.data && typeof err.response.data === 'object') {
          message = err.response.data.error || err.response.data.message || message;
        }
      } else if (err.message) {
        message = err.message;
      }
      setServerError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-slate-950 px-6 py-12">
      <div className="w-full max-w-md p-10 bg-slate-900 border border-slate-850 rounded-3xl shadow-2xl relative overflow-hidden">
        
        {/* Decorative background glow */}
        <div className="absolute top-0 right-0 -mt-12 -mr-12 w-32 h-32 bg-violet-600/10 rounded-full blur-2xl pointer-events-none" />
        <div className="absolute bottom-0 left-0 -mb-12 -ml-12 w-32 h-32 bg-fuchsia-600/10 rounded-full blur-2xl pointer-events-none" />

        <div className="flex flex-col items-center mb-10 relative">
          <div className="w-14 h-14 bg-gradient-to-tr from-violet-600 to-fuchsia-600 rounded-2xl flex items-center justify-center mb-4 shadow-lg shadow-violet-500/10">
            <Brain className="w-8 h-8 text-white animate-pulse" />
          </div>
          <h2 className="text-3xl font-black bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">Welcome Back</h2>
          <p className="text-slate-400 text-sm mt-2 font-medium">Sign in to your assessment portal</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6 relative">
          
          {/* Email field */}
          <div className="space-y-2">
            <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">Email Address</label>
            <div className="relative">
              <Mail className="absolute left-4 top-3.5 w-5 h-5 text-slate-500" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={`w-full pl-12 pr-4 py-3 bg-slate-950 border ${errors.email ? 'border-rose-500' : 'border-slate-800 focus:border-violet-500'} rounded-xl text-white outline-none transition-all font-medium text-sm`}
                placeholder="name@example.com"
              />
            </div>
            {errors.email && <p className="text-rose-500 text-xs font-semibold">{errors.email}</p>}
          </div>

          {/* Password field */}
          <div className="space-y-2">
            <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">Password</label>
            <div className="relative">
              <Lock className="absolute left-4 top-3.5 w-5 h-5 text-slate-500" />
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={`w-full pl-12 pr-12 py-3 bg-slate-950 border ${errors.password ? 'border-rose-500' : 'border-slate-800 focus:border-violet-500'} rounded-xl text-white outline-none transition-all font-medium text-sm`}
                placeholder="••••••••"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-4 top-3.5 text-slate-500 hover:text-white transition-colors cursor-pointer"
              >
                {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
              </button>
            </div>
            {errors.password && <p className="text-rose-500 text-xs font-semibold">{errors.password}</p>}
          </div>

          {/* Forgot Password link */}
          <div className="flex justify-end">
            <Link to="/forgot-password" className="text-xs font-bold text-violet-400 hover:text-violet-300 transition-colors">
              Forgot Password?
            </Link>
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
                <Loader2 className="w-4 h-4 animate-spin" /> Signing in...
              </>
            ) : (
              'Sign In'
            )}
          </button>
        </form>

        <p className="mt-8 text-center text-xs font-bold text-slate-500 uppercase tracking-wider">
          Don't have an account?{' '}
          <Link to="/signup" className="text-violet-400 hover:text-violet-300 transition-colors">Create one</Link>
        </p>
      </div>
    </div>
  );
};

export default Login;
