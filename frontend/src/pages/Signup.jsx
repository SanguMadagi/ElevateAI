import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authService } from '../services/api';
import { Brain, Lock, Mail, User, Eye, EyeOff, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';

const Signup = () => {
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  // Password strength calculation
  const getPasswordStrength = (pwd) => {
    if (!pwd) return { label: '', color: 'bg-slate-800', width: 'w-0', textClass: 'text-slate-500' };
    
    let score = 0;
    if (pwd.length >= 8) score++;
    if (/[A-Z]/.test(pwd)) score++;
    if (/[a-z]/.test(pwd)) score++;
    if (/[0-9]/.test(pwd)) score++;
    if (/[^A-Za-z0-9]/.test(pwd)) score++;

    if (pwd.length < 6) {
      return { label: 'Weak', color: 'bg-rose-500', width: 'w-1/3', textClass: 'text-rose-400' };
    }

    if (score <= 2) {
      return { label: 'Weak', color: 'bg-rose-500', width: 'w-1/3', textClass: 'text-rose-400' };
    } else if (score <= 4) {
      return { label: 'Medium', color: 'bg-amber-500', width: 'w-2/3', textClass: 'text-amber-400' };
    } else {
      return { label: 'Strong', color: 'bg-emerald-500', width: 'w-full', textClass: 'text-emerald-400' };
    }
  };

  const strength = getPasswordStrength(password);

  const validateForm = () => {
    const newErrors = {};
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    
    if (!firstName.trim()) {
      newErrors.firstName = 'First name is required';
    }
    if (!lastName.trim()) {
      newErrors.lastName = 'Last name is required';
    }
    if (!email.trim()) {
      newErrors.email = 'Email address is required';
    } else if (!emailRegex.test(email.trim())) {
      newErrors.email = 'Please enter a valid email address';
    }
    if (!password) {
      newErrors.password = 'Password is required';
    } else if (password.length < 6) {
      newErrors.password = 'Password must be at least 6 characters long';
    }
    if (password !== confirmPassword) {
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
      const payload = {
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim(),
        password: password
      };

      await authService.signup(payload);
      toast.success('Registration successful! OTP sent.');
      navigate(`/verify-otp?email=${encodeURIComponent(email.trim())}`);
    } catch (err) {
      console.error(err);
      let message = "Registration failed. Please try again.";
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

        <div className="flex flex-col items-center mb-8 relative">
          <div className="w-14 h-14 bg-gradient-to-tr from-violet-600 to-fuchsia-600 rounded-2xl flex items-center justify-center mb-4 shadow-lg shadow-violet-500/10">
            <Brain className="w-8 h-8 text-white animate-pulse" />
          </div>
          <h2 className="text-3xl font-black bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">Create Account</h2>
          <p className="text-slate-400 text-sm mt-2 font-medium">Join our AI assessment platform</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5 relative">
          {/* First & Last Name fields */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">First Name</label>
              <div className="relative">
                <User className="absolute left-4 top-3 w-5 h-5 text-slate-500" />
                <input
                  type="text"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  className={`w-full pl-12 pr-4 py-2.5 bg-slate-950 border ${errors.firstName ? 'border-rose-500' : 'border-slate-800 focus:border-violet-500'} rounded-xl text-white outline-none transition-all font-medium text-sm`}
                  placeholder="John"
                />
              </div>
              {errors.firstName && <p className="text-rose-500 text-xs font-semibold">{errors.firstName}</p>}
            </div>

            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">Last Name</label>
              <div className="relative">
                <User className="absolute left-4 top-3 w-5 h-5 text-slate-500" />
                <input
                  type="text"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  className={`w-full pl-12 pr-4 py-2.5 bg-slate-950 border ${errors.lastName ? 'border-rose-500' : 'border-slate-800 focus:border-violet-500'} rounded-xl text-white outline-none transition-all font-medium text-sm`}
                  placeholder="Doe"
                />
              </div>
              {errors.lastName && <p className="text-rose-500 text-xs font-semibold">{errors.lastName}</p>}
            </div>
          </div>

          {/* Email field */}
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">Email Address</label>
            <div className="relative">
              <Mail className="absolute left-4 top-3 w-5 h-5 text-slate-500" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={`w-full pl-12 pr-4 py-2.5 bg-slate-950 border ${errors.email ? 'border-rose-500' : 'border-slate-800 focus:border-violet-500'} rounded-xl text-white outline-none transition-all font-medium text-sm`}
                placeholder="name@example.com"
              />
            </div>
            {errors.email && <p className="text-rose-500 text-xs font-semibold">{errors.email}</p>}
          </div>

          {/* Password field */}
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">Password</label>
            <div className="relative">
              <Lock className="absolute left-4 top-3 w-5 h-5 text-slate-500" />
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={`w-full pl-12 pr-12 py-2.5 bg-slate-950 border ${errors.password ? 'border-rose-500' : 'border-slate-800 focus:border-violet-500'} rounded-xl text-white outline-none transition-all font-medium text-sm`}
                placeholder="••••••••"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-4 top-3 text-slate-500 hover:text-white transition-colors cursor-pointer"
              >
                {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
              </button>
            </div>
            {errors.password && <p className="text-rose-500 text-xs font-semibold">{errors.password}</p>}
            
            {/* Password strength bar */}
            {password && (
              <div className="space-y-1 mt-2">
                <div className="flex justify-between items-center text-[10px] font-bold">
                  <span className="text-slate-500 uppercase tracking-wider">Password Strength:</span>
                  <span className={`${strength.textClass} uppercase tracking-wider`}>{strength.label}</span>
                </div>
                <div className="w-full h-1 bg-slate-950 rounded-full overflow-hidden border border-slate-850">
                  <div className={`h-full ${strength.color} ${strength.width} transition-all duration-350`} />
                </div>
              </div>
            )}
          </div>

          {/* Confirm Password field */}
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">Confirm Password</label>
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
            <div className="p-3.5 bg-rose-950/40 border border-rose-900/30 rounded-xl text-xs font-semibold text-rose-400 animate-in fade-in slide-in-from-top-1 duration-205">
              {serverError}
            </div>
          )}

          {/* Submit button */}
          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 text-white bg-violet-600 hover:bg-violet-500 rounded-xl font-bold text-sm tracking-wider uppercase transition-all shadow-lg shadow-violet-500/10 disabled:opacity-50 flex items-center justify-center gap-2 cursor-pointer mt-2"
          >
            {loading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" /> Creating Account...
              </>
            ) : (
              'Create Account'
            )}
          </button>
        </form>

        <p className="mt-8 text-center text-xs font-bold text-slate-500 uppercase tracking-wider font-semibold">
          Already have an account?{' '}
          <Link to="/login" className="text-violet-400 hover:text-violet-300 transition-colors">Login</Link>
        </p>
      </div>
    </div>
  );
};

export default Signup;
