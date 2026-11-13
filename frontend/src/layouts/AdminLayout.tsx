import React from 'react';
import { NavLink, Outlet, Link } from 'react-router-dom';
import { Navbar } from '../components/Navbar';
import {
  LayoutDashboard,
  Upload,
  FileSpreadsheet,
  Building2,
  CalendarDays,
  CalendarX,
  Users,
  ScrollText,
  Settings,
  ArrowLeft
} from 'lucide-react';

export const AdminLayout: React.FC = () => {
  const navItems = [
    { to: '/admin', label: 'Overview', icon: LayoutDashboard, end: true },
    { to: '/admin/upload', label: 'Upload Timetable', icon: Upload },
    { to: '/admin/timetables', label: 'Timetable Library', icon: FileSpreadsheet },
    { to: '/admin/departments', label: 'Departments', icon: Building2 },
    { to: '/admin/academic', label: 'Academic Structure', icon: CalendarDays },
    { to: '/admin/overrides', label: 'Schedule Overrides', icon: CalendarX },
    { to: '/admin/users', label: 'User Management', icon: Users },
    { to: '/admin/settings', label: 'Settings & API Keys', icon: Settings },
    { to: '/admin/audit', label: 'Audit Logs', icon: ScrollText },
  ];

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <Navbar />

      <div className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <div className="flex flex-col md:flex-row gap-6">
          {/* Admin Sidebar */}
          <aside className="w-full md:w-64 shrink-0">
            <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm p-4 sticky top-24">
              <div className="mb-4 pb-3 border-b border-slate-100 flex items-center justify-between">
                <div>
                  <h2 className="text-xs uppercase tracking-wider font-bold text-slate-400">
                    Admin Console
                  </h2>
                  <p className="text-sm font-semibold text-slate-800">Campus Operations</p>
                </div>
                <Link
                  to="/"
                  className="text-xs text-blue-600 hover:text-blue-700 flex items-center space-x-1 font-medium"
                  title="Switch to Faculty View"
                >
                  <ArrowLeft className="w-3 h-3" />
                  <span>Faculty</span>
                </Link>
              </div>

              <nav className="space-y-1">
                {navItems.map((item) => {
                  const Icon = item.icon;
                  return (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end={item.end}
                      className={({ isActive }) =>
                        `flex items-center space-x-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
                          isActive
                            ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/20'
                            : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/80'
                        }`
                      }
                    >
                      <Icon className="w-4 h-4" />
                      <span>{item.label}</span>
                    </NavLink>
                  );
                })}
              </nav>
            </div>
          </aside>

          {/* Main Workspace Area */}
          <main className="flex-1 min-w-0">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
};

