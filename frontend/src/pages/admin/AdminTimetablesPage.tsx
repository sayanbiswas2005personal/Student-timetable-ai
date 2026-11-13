import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api, TimetableListItem } from '../../services/api';
import {
  FileSpreadsheet,
  Upload,
  CheckCircle2,
  XCircle,
  Trash2,
  Eye,
  RefreshCw,
  PowerOff,
  Layers
} from 'lucide-react';

export const AdminTimetablesPage: React.FC = () => {
  const [timetables, setTimetables] = useState<TimetableListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionMsg, setActionMsg] = useState<string | null>(null);

  const loadData = async () => {
    try {
      const data = await api.admin.getTimetables();
      setTimetables(data);
    } catch (err) {
      console.error('Failed to load timetables', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleDeactivate = async (id: number) => {
    if (!window.confirm('Are you sure you want to deactivate this timetable? Faculty searches will report timetable unavailable.')) {
      return;
    }
    try {
      await api.admin.deactivateTimetable(id);
      setActionMsg(`Timetable #${id} deactivated.`);
      loadData();
    } catch (err: any) {
      alert(`Deactivate failed: ${err.message}`);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this timetable record?')) {
      return;
    }
    try {
      await api.admin.deleteTimetable(id);
      setActionMsg(`Timetable #${id} deleted.`);
      loadData();
    } catch (err: any) {
      alert(`Delete failed: ${err.message}`);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold text-navy-900 tracking-tight">Timetable Library</h1>
          <p className="mt-1 text-sm text-slate-600">
            Version history, active releases, and timetable life-cycle management.
          </p>
        </div>

        <Link
          to="/admin/upload"
          className="inline-flex items-center space-x-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold shadow-sm transition-all"
        >
          <Upload className="w-4 h-4" />
          <span>Upload New Timetable</span>
        </Link>
      </div>

      {actionMsg && (
        <div className="p-3 bg-blue-50 border border-blue-200 text-blue-800 text-xs rounded-xl font-medium">
          {actionMsg}
        </div>
      )}

      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm overflow-hidden p-6">
        {loading ? (
          <div className="py-12 flex justify-center items-center text-slate-400">
            <RefreshCw className="w-6 h-6 animate-spin mr-2" />
            <span>Loading Timetable Archives...</span>
          </div>
        ) : timetables.length === 0 ? (
          <div className="text-center py-12">
            <FileSpreadsheet className="w-10 h-10 text-slate-300 mx-auto mb-2" />
            <p className="text-sm font-semibold text-slate-700">No Timetables Found</p>
          </div>
        ) : (
          <div className="overflow-x-auto custom-scrollbar">
            <table className="w-full text-left text-sm border-collapse">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase font-bold text-slate-400">
                  <th className="py-3 px-3">ID</th>
                  <th className="py-3 px-3">File Name</th>
                  <th className="py-3 px-3">Academic Year</th>
                  <th className="py-3 px-3">Status</th>
                  <th className="py-3 px-3">Entries</th>
                  <th className="py-3 px-3">Uploaded Date</th>
                  <th className="py-3 px-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {timetables.map((tt) => (
                  <tr key={tt.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 px-3 font-mono text-xs text-slate-400">#{tt.id}</td>
                    <td className="py-3 px-3 font-bold text-navy-900 max-w-xs truncate">
                      {tt.original_filename}
                    </td>
                    <td className="py-3 px-3 text-xs text-slate-600 font-medium">
                      {tt.academic_year || '—'}
                    </td>
                    <td className="py-3 px-3">
                      {tt.is_published ? (
                        <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800">
                          Active (v{tt.version})
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-600">
                          {tt.processing_status}
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-3 text-xs font-bold text-slate-700 font-mono">
                      {tt.total_entries}
                    </td>
                    <td className="py-3 px-3 text-xs text-slate-500">
                      {new Date(tt.uploaded_at).toLocaleDateString()}
                    </td>
                    <td className="py-3 px-3">
                      <div className="flex items-center space-x-2">
                        <Link
                          to={`/admin/upload?timetable_id=${tt.id}`}
                          className="p-1.5 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                          title="Inspect / Edit"
                        >
                          <Eye className="w-4 h-4" />
                        </Link>

                        {tt.is_published && (
                          <button
                            type="button"
                            onClick={() => handleDeactivate(tt.id)}
                            className="p-1.5 text-amber-600 hover:bg-amber-50 rounded-lg transition-colors cursor-pointer"
                            title="Deactivate Timetable"
                          >
                            <PowerOff className="w-4 h-4" />
                          </button>
                        )}

                        {!tt.is_published && (
                          <button
                            type="button"
                            onClick={() => handleDelete(tt.id)}
                            className="p-1.5 text-rose-500 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer"
                            title="Delete Record"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </div>
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

