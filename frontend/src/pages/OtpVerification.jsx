import { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { authService } from '../services/api';
import { Brain, ShieldCheck, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';

const OtpVerification = () => {
  const [searchParams] = useSearchParams();
  const email = searchParams.get('email') || '';
  const navigate = useNavigate();

  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [serverError, setServerError] = useState('');
  const [countdown, setCountdown] = useState(60);

  const inputRefs = useRef([]);

  // Countdown timer logic
  useEffect(() => {
    let timer;
    if (countdown > 0) {
      timer = setInterval(() => {
        setCountdown((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [countdown]);

  // Focus first input on mount
  useEffect(() => {
    if (inputRefs.current[0]) {
      inputRefs.current[0].focus();
    }
  }, []);

  const handleChange = (e, index) => {
    const val = e.target.value;
    // Allow only numeric input
    if (val && !/^[0-9]$/.test(val)) return;

    const newOtp = [...otp];
    newOtp[index] = val;
    setOtp(newOtp);

    // Auto-focus next input
    if (val && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (e, index) => {
    // Backspace: clear current and focus previous
    if (e.key === 'Backspace') {
      if (!otp[index] && index > 0) {
        const newOtp = [...otp];
        newOtp[index - 1] = '';
        setOtp(newOtp);
        inputRefs.current[index - 1]?.focus();
      } else {
        const newOtp = [...otp];
        newOtp[index] = '';
        setOtp(newOtp);
      }
    }
  };

  const handlePaste = (e) => {
    e.preventDefault();
    const pastedData = e.clipboardData.getData('text').trim();
    if (!/^\d{6}$/.test(pastedData)) return;

    const digits = pastedData.split('');
    setOtp(digits);
    // Focus last input
    inputRefs.current[5]?.focus();
  };

  const handleSubmit = async (e) => {
    if (e) e.preventDefault();
    setServerError('');
    
    const fullOtp = otp.join('');
    if (fullOtp.length < 6) {
      setServerError('Please enter all 6 digits of the verification code.');
      return;
    }

    setLoading(true);
    try {
      await authService.verifyOtp({ email, otp: fullOtp });
      toast.success('Email verified! Please login.');
      navigate('/login', { replace: true });
    } catch (err) {
      console.error(err);
      const errorMessage = err.response?.data?.error || err.response?.data?.message || err.message || 'Verification failed. Please check your OTP.';
      setServerError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (countdown > 0 || resending) return;

    setServerError('');
    setResending(true);
    try {
      await authService.resendOtp(email);
      toast.success('A new verification code has been sent to your email.');
      setCountdown(60);
    } catch (err) {
      console.error(err);
      const errorMessage = err.response?.data?.error || err.response?.data?.message || err.message || 'Failed to resend verification code.';
      setServerError(errorMessage);
    } finally {
      setResending(false);
    }
  };

  // Auto-submit when all 6 digits are entered
  useEffect(() => {
    const fullOtp = otp.join('');
    if (fullOtp.length === 6) {
      handleSubmit();
    }
  }, [otp]);

  if (!email) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-950 px-6">
        <div className="text-center p-10 bg-slate-900 border border-slate-850 rounded-3xl shadow-2xl max-w-md space-y-6">
          <p className="text-xl font-bold text-slate-300">Invalid access. Missing target email address.</p>
          <button 
            onClick={() => navigate('/signup')}
            className="px-8 py-3 bg-violet-600 text-white rounded-xl font-bold hover:bg-violet-500 transition-all cursor-pointer uppercase text-xs tracking-wider"
          >
            Go to Signup
          </button>
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
          <div className="w-14 h-14 bg-emerald-500/10 border border-emerald-500/25 rounded-2xl flex items-center justify-center mb-4 shadow-lg">
            <ShieldCheck className="w-8 h-8 text-emerald-400" />
          </div>
          <h2 className="text-3xl font-black bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">Verify Your Email</h2>
          <p className="text-slate-400 text-xs mt-3 font-semibold uppercase tracking-wider leading-relaxed">
            We sent a 6-digit code to <br />
            <span className="text-violet-400 font-black normal-case text-sm tracking-normal">{email}</span>
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6 relative">
          {/* OTP Input Grid */}
          <div className="flex justify-between gap-2.5" onPaste={handlePaste}>
            {otp.map((digit, idx) => (
              <input
                key={idx}
                id={`otp-${idx}`}
                ref={(el) => (inputRefs.current[idx] = el)}
                type="text"
                maxLength={1}
                value={digit}
                onChange={(e) => handleChange(e, idx)}
                onKeyDown={(e) => handleKeyDown(e, idx)}
                className="w-12 h-14 text-center bg-slate-950 border border-slate-800 focus:border-violet-500 rounded-xl text-white outline-none text-xl font-black transition-all shadow-inner"
                inputMode="numeric"
              />
            ))}
          </div>

          {/* Form-level Server Error */}
          {serverError && (
            <div className="p-3.5 bg-rose-950/40 border border-rose-900/30 rounded-xl text-xs font-semibold text-rose-400 animate-in fade-in slide-in-from-top-1 duration-200">
              {serverError}
            </div>
          )}

          {/* Verify OTP Button */}
          <button
            type="submit"
            disabled={loading || otp.join('').length < 6}
            className="w-full py-3.5 text-white bg-violet-600 hover:bg-violet-500 rounded-xl font-bold text-sm tracking-wider uppercase transition-all shadow-lg shadow-violet-500/10 disabled:opacity-50 flex items-center justify-center gap-2 cursor-pointer"
          >
            {loading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" /> Verifying...
              </>
            ) : (
              'Verify OTP'
            )}
          </button>
        </form>

        {/* Resend Action */}
        <div className="mt-8 text-center text-xs font-bold text-slate-500 uppercase tracking-wider">
          Didn't receive the code?{' '}
          {countdown > 0 ? (
            <span className="text-slate-400 font-bold">Resend in {countdown}s</span>
          ) : (
            <button
              onClick={handleResend}
              disabled={resending}
              className="text-violet-400 hover:text-violet-300 transition-colors font-bold underline bg-transparent border-none cursor-pointer focus:outline-none"
            >
              {resending ? 'Resending...' : 'Resend OTP'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default OtpVerification;
