import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api, TimetableListItem } from '../../services/api';
import {
  FileSpreadsheet,
  Building2,
  CalendarDays,
  CalendarX,
  Upload,
  CheckCircle2,
  Clock,
  ArrowRight,
  Sparkles
} from 'lucide-react';

export const AdminOverviewPage: React.FC = () => {
  const [timetables, setTimetables] = useState<TimetableListItem[]>([]);
  const [deptCount, setDeptCount] = useState(0);
  const [structCount, setStructCount] = useState(0);
  const [overrideCount, setOverrideCount] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchOverview = async () => {
      try {
        const [ttList, depts, structs, overrides] = await Promise.all([
          api.admin.getTimetables(),
          api.admin.getDepartments(),
          api.admin.getStructures(),
          api.admin.getOverrides(),
        ]);
        setTimetables(ttList);
        setDeptCount(depts.length);
        setStructCount(structs.length);
        setOverrideCount(overrides.length);
      } catch (err) {
        console.error('Error fetching admin overview', err);
      } finally {
        setLoading(false);
      }
    };
    fetchOverview();
  }, []);

  const activeTimetable = timetables.find((t) => t.is_published);

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="bg-navy-900 rounded-3xl p-6 sm:p-8 text-white relative overflow-hidden shadow-lg shadow-navy-950/20">
        <div className="relative z-10 max-w-2xl">
          <span className="text-xs font-bold uppercase tracking-widest text-blue-400 bg-blue-900/50 px-3 py-1 rounded-full inline-block mb-3 border border-blue-700/50">
            Institutional Administration
          </span>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            Campus Timetable Control Center
          </h1>
          <p className="mt-2 text-sm text-slate-300 leading-relaxed">
            Manage institutional timetable versions, upload official schedule PDFs, process structured schedules with Google Gemini API, and approve timetable releases.
          </p>

          <div className="mt-6 flex flex-wrap gap-3">
            <Link
              to="/admin/upload"
              className="inline-flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 font-semibold text-sm shadow-md transition-all cursor-pointer"
            >
              <Upload className="w-4 h-4" />
              <span>Upload New PDF</span>
            </Link>
            <Link
              to="/"
              className="inline-flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-navy-800 hover:bg-navy-700 text-slate-200 font-semibold text-sm border border-navy-700 transition-all cursor-pointer"
            >
              <span>Faculty Portal</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Active Timetable */}
        <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Active Version</span>
            <div className="w-9 h-9 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <CheckCircle2 className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl font-extrabold text-navy-900">
              {activeTimetable ? `v${activeTimetable.version} Live` : 'None Active'}
            </div>
            <div className="text-xs text-slate-500 mt-1 truncate">
              {activeTimetable ? activeTimetable.original_filename : 'Upload and publish a timetable'}
            </div>
          </div>
        </div>

        {/* Departments */}
        <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Departments</span>
            <div className="w-9 h-9 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <Building2 className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl font-extrabold text-navy-900">{deptCount}</div>
            <div className="text-xs text-slate-500 mt-1">Configured branches</div>
          </div>
        </div>

        {/* Sections */}
        <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Class Sections</span>
            <div className="w-9 h-9 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <CalendarDays className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl font-extrabold text-navy-900">{structCount}</div>
            <div className="text-xs text-slate-500 mt-1">Year/Semester groupings</div>
          </div>
        </div>

        {/* Overrides */}
        <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Schedule Overrides</span>
            <div className="w-9 h-9 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
              <CalendarX className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl font-extrabold text-navy-900">{overrideCount}</div>
            <div className="text-xs text-slate-500 mt-1">Holidays & substitutions</div>
          </div>
        </div>
      </div>

      {/* Recent Timetables Table */}
      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-bold text-navy-900">Uploaded Timetables</h2>
            <p className="text-xs text-slate-500">History of timetable PDFs and processing lifecycle</p>
          </div>
          <Link
            to="/admin/timetables"
            className="text-xs text-blue-600 hover:text-blue-700 font-semibold flex items-center space-x-1"
          >
            <span>View All</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        {timetables.length === 0 ? (
          <div className="text-center py-10 bg-slate-50 rounded-2xl border border-dashed border-slate-200">
            <FileSpreadsheet className="w-10 h-10 text-slate-300 mx-auto mb-2" />
            <p className="text-sm font-semibold text-slate-700">No timetables uploaded yet</p>
            <p className="text-xs text-slate-500 mt-1">Upload the institution's official PDF timetable to begin.</p>
            <Link
              to="/admin/upload"
              className="mt-4 inline-flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-xl text-xs font-bold"
            >
              <Upload className="w-3.5 h-3.5" />
              <span>Upload PDF</span>
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto custom-scrollbar">
            <table className="w-full text-left text-sm border-collapse">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase font-bold text-slate-400">
                  <th className="py-3 px-3">File Name</th>
                  <th className="py-3 px-3">Academic Year</th>
                  <th className="py-3 px-3">Status</th>
                  <th className="py-3 px-3">Provider</th>
                  <th className="py-3 px-3">Entries</th>
                  <th className="py-3 px-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {timetables.slice(0, 5).map((tt) => (
                  <tr key={tt.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 px-3 font-semibold text-slate-800 max-w-xs truncate">
                      {tt.original_filename}
                    </td>
                    <td className="py-3 px-3 text-xs text-slate-600">
                      {tt.academic_year || '—'}
                    </td>
                    <td className="py-3 px-3">
                      {tt.is_published ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800">
                          Active v{tt.version}
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-700">
                          {tt.processing_status}
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-3 text-xs font-mono text-slate-500">
                      {tt.extraction_provider || '—'}
                    </td>
                    <td className="py-3 px-3 text-xs font-bold text-slate-700">
                      {tt.total_entries}
                    </td>
                    <td className="py-3 px-3">
                      <Link
                        to={`/admin/upload?timetable_id=${tt.id}`}
                        className="text-xs text-blue-600 hover:text-blue-800 font-semibold"
                      >
                        Inspect / Edit
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

