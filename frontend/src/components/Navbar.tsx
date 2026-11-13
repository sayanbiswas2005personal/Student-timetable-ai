import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  GraduationCap,
  Clock,
  LogOut,
  ShieldAlert,
  User as UserIcon,
  Search,
  LayoutDashboard
} from 'lucide-react';
import { api } from '../services/api';

export const Navbar: React.FC = () => {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [campusTime, setCampusTime] = useState<{
    time: string;
    date: string;
    day: string;
  }>({
    time: '--:--',
    date: 'Loading...',
    day: '',
  });

  useEffect(() => {
    const fetchTime = async () => {
      try {
        const t = await api.search.getServerTime();
        setCampusTime({
          time: t.time.substring(0, 5),
          date: t.date,
          day: t.day,
        });
      } catch {
        // Fallback to local
        const now = new Date();
        setCampusTime({
          time: now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          date: now.toISOString().split('T')[0],
          day: now.toLocaleDateString([], { weekday: 'long' }).toUpperCase(),
        });
      }
    };

    fetchTime();
    const interval = setInterval(fetchTime, 30000);
    return () => clearInterval(interval);
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <header className="sticky top-0 z-40 bg-navy-900 border-b border-navy-800 text-white shadow-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo */}
          <div className="flex items-center space-x-6">
            <Link to="/" className="flex items-center space-x-3 group">
              <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center shadow-lg shadow-blue-500/30 group-hover:bg-blue-500 transition-colors">
                <GraduationCap className="w-6 h-6 text-white" />
              </div>
              <div>
                <span className="text-xl font-bold tracking-tight text-white flex items-center">
                  StudentAI<span className="text-blue-400">.com</span>
                </span>
                <span className="text-[10px] uppercase tracking-wider text-slate-400 block -mt-1 font-medium">
                  Smart Campus Timetable
                </span>
              </div>
            </Link>

            {/* Campus Clock Pill */}
            <div className="hidden md:flex items-center space-x-2 bg-navy-800/80 px-3 py-1.5 rounded-full border border-navy-700 text-xs">
              <Clock className="w-3.5 h-3.5 text-blue-400 animate-pulse" />
              <span className="text-slate-300 font-medium">IST:</span>
              <span className="font-mono font-bold text-blue-200">{campusTime.time}</span>
              <span className="text-slate-500">•</span>
              <span className="text-slate-300">{campusTime.day}</span>
            </div>
          </div>

          {/* Navigation Links */}
          <div className="flex items-center space-x-3">
            <Link
              to="/"
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors flex items-center space-x-1.5 ${
                location.pathname === '/'
                  ? 'bg-blue-600/20 text-blue-400 border border-blue-500/30'
                  : 'text-slate-300 hover:text-white hover:bg-navy-800'
              }`}
            >
              <Search className="w-4 h-4" />
              <span>Timetable Search</span>
            </Link>

            {isAdmin && (
              <Link
                to="/admin"
                className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors flex items-center space-x-1.5 ${
                  location.pathname.startsWith('/admin')
                    ? 'bg-blue-600 text-white shadow-sm'
                    : 'text-slate-300 hover:text-white hover:bg-navy-800'
                }`}
              >
                <LayoutDashboard className="w-4 h-4" />
                <span>Admin Console</span>
              </Link>
            )}

            {/* User Profile */}
            {user ? (
              <div className="flex items-center space-x-3 pl-3 border-l border-navy-800">
                <div className="hidden sm:block text-right">
                  <div className="text-xs font-semibold text-slate-200">{user.name}</div>
                  <div className="text-[10px] text-blue-300 font-medium flex items-center justify-end space-x-1">
                    <span>{user.role}</span>
                    {user.department && <span className="text-slate-500">• {user.department}</span>}
                  </div>
                </div>
                <div className="w-8 h-8 rounded-full bg-navy-800 border border-navy-700 flex items-center justify-center text-blue-300 font-bold text-xs">
                  {user.name.charAt(0)}
                </div>
                <button
                  onClick={handleLogout}
                  title="Logout"
                  className="p-1.5 rounded-lg text-slate-400 hover:text-red-400 hover:bg-navy-800 transition-colors"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <Link
                to="/login"
                className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold transition-colors"
              >
                Sign In
              </Link>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};

