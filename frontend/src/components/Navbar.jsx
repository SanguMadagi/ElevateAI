import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Brain, User, LogOut, Settings, LayoutDashboard, Sparkles, FileText } from 'lucide-react';
import { useState, useEffect, useRef } from 'react';
import { profileService } from '../services/api';

const Navbar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [profileName, setProfileName] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef(null);
  const timeoutRef = useRef(null);

  const handleMouseEnter = () => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    setDropdownOpen(true);
  };

  const handleMouseLeave = () => {
    timeoutRef.current = setTimeout(() => {
      setDropdownOpen(false);
    }, 200);
  };

  useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, []);

  useEffect(() => {
    if (user) {
      profileService.getProfile()
        .then(res => {
          if (res?.data?.name) {
            setProfileName(res.data.name);
          }
        })
        .catch(() => {});
    }
  }, [user]);

  // Click outside to close dropdown
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const displayName = profileName || (user?.firstName && user?.lastName ? `${user.firstName} ${user.lastName}` : (user?.name || 'Candidate'));

  return (
    <nav className="sticky top-0 z-50 bg-slate-950/85 backdrop-blur-md border-b border-slate-850 px-4 sm:px-6 py-3.5 transition-colors">
      <div className="max-w-7xl mx-auto flex items-center justify-between">
        
        {/* Brand Logo */}
        <Link to="/dashboard" className="flex items-center gap-3 group cursor-pointer focus:outline-none" aria-label="Home Dashboard">
          <div className="w-10 h-10 bg-gradient-to-tr from-violet-600 to-indigo-600 rounded-2xl flex items-center justify-center shadow-lg shadow-violet-600/20 transition-transform group-hover:scale-105 border border-violet-500/30">
            <Brain className="w-5 h-5 text-white" />
          </div>
          <div>
            <span className="text-lg font-black tracking-tight bg-gradient-to-r from-white via-slate-100 to-slate-300 bg-clip-text text-transparent">
              Elevate<span className="text-violet-400">AI</span>
            </span>
            <span className="hidden sm:block text-[10px] uppercase font-bold tracking-widest text-slate-400 -mt-1">
              Interview Platform
            </span>
          </div>
        </Link>

        {/* Navigation Links */}
        <div className="flex items-center gap-4 sm:gap-6">
          <Link 
            to="/dashboard" 
            className={`flex items-center gap-2 text-sm font-bold transition-colors py-1.5 px-3 rounded-xl cursor-pointer ${
              location.pathname === '/dashboard' 
                ? 'text-violet-400 bg-violet-500/10 border border-violet-500/20' 
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <LayoutDashboard className="w-4 h-4" />
            <span className="hidden md:inline">Dashboard</span>
          </Link>

          <Link 
            to="/profile" 
            className={`flex items-center gap-2 text-sm font-bold transition-colors py-1.5 px-3 rounded-xl cursor-pointer ${
              location.pathname === '/profile' 
                ? 'text-violet-400 bg-violet-500/10 border border-violet-500/20' 
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <FileText className="w-4 h-4" />
            <span className="hidden md:inline">Profile & Skills</span>
          </Link>

          <div className="h-5 w-px bg-slate-800 hidden sm:block"></div>

          {/* User Profile Pill & Dropdown */}
          <div className="flex items-center gap-3">
             <div className="text-right hidden sm:block">
                <p className="text-sm font-bold text-slate-200 truncate max-w-[150px]">
                  {displayName}
                </p>
                <p className="text-xs text-slate-400 truncate max-w-[150px]">{user?.email}</p>
             </div>
             
             <div 
               className="relative" 
               ref={dropdownRef}
               onMouseEnter={handleMouseEnter}
               onMouseLeave={handleMouseLeave}
             >
                <button 
                  onClick={() => setDropdownOpen(!dropdownOpen)}
                  aria-label="User profile menu"
                  aria-expanded={dropdownOpen}
                  className="w-10 h-10 bg-slate-900 rounded-2xl flex items-center justify-center border border-slate-800 hover:border-violet-500/50 transition-all cursor-pointer focus:outline-none focus:ring-2 focus:ring-violet-500/30"
                >
                   <User className="w-5 h-5 text-violet-400" />
                </button>
                
                {dropdownOpen && (
                  <div className="absolute right-0 top-full pt-2 w-56 z-50 animate-in fade-in slide-in-from-top-2">
                    <div className="bg-slate-900/95 backdrop-blur-xl rounded-2xl shadow-2xl border border-slate-800 py-2">
                       <div className="px-4 py-2.5 border-b border-slate-800/80 mb-1">
                          <p className="text-sm font-bold text-white truncate">
                            {displayName}
                          </p>
                          <p className="text-xs text-slate-400 truncate">{user?.email}</p>
                       </div>
                       
                       <Link 
                         to="/profile" 
                         onClick={() => setDropdownOpen(false)}
                         className="flex items-center gap-3 px-4 py-2.5 text-sm text-slate-300 hover:text-white hover:bg-slate-800/60 transition-colors cursor-pointer"
                       >
                          <Settings className="w-4 h-4 text-violet-400" /> Profile & Analytics
                       </Link>
                       
                       <div className="my-1 border-t border-slate-800/80"></div>
                       
                       <button 
                         onClick={() => {
                           setDropdownOpen(false);
                           handleLogout();
                         }}
                         className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-rose-400 hover:bg-rose-500/10 hover:text-rose-300 transition-colors cursor-pointer text-left"
                       >
                          <LogOut className="w-4 h-4" /> Sign Out
                       </button>
                    </div>
                  </div>
                )}
             </div>
          </div>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
