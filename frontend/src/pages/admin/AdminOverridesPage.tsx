import React, { useState, useEffect } from 'react';
import { api, ScheduleOverrideItem } from '../../services/api';
import { CalendarX, Plus, Trash2, CheckCircle2, AlertTriangle, Clock } from 'lucide-react';

export const AdminOverridesPage: React.FC = () => {
  const [overrides, setOverrides] = useState<ScheduleOverrideItem[]>([]);
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [dept, setDept] = useState('CSE');
  const [year, setYear] = useState(2);
  const [semester, setSemester] = useState(3);
  const [section, setSection] = useState('A');
  const [overrideType, setOverrideType] = useState<'CANCELLED' | 'SUBSTITUTION' | 'SPECIAL_CLASS' | 'HOLIDAY'>('CANCELLED');
  const [startTime, setStartTime] = useState('09:30');
  const [endTime, setEndTime] = useState('10:25');
  const [faculty, setFaculty] = useState('');
  const [subject, setSubject] = useState('');
  const [reason, setReason] = useState('');
  const [msg, setMsg] = useState<string | null>(null);

  const loadOverrides = async () => {
    try {
      const data = await api.admin.getOverrides();
      setOverrides(data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    loadOverrides();
  }, []);

  const handleCreateOverride = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason) {
      alert('Please provide a reason for the override.');
      return;
    }

    try {
      await api.admin.createOverride({
        date,
        department: dept,
        year,
        semester,
        section,
        override_type: overrideType,
        start_time: overrideType === 'HOLIDAY' ? undefined : startTime,
        end_time: overrideType === 'HOLIDAY' ? undefined : endTime,
        faculty: faculty || undefined,
        subject: subject || undefined,
        reason,
      });

      setMsg('Schedule override created successfully.');
      setReason('');
      loadOverrides();
    } catch (err: any) {
      alert(`Override failed: ${err.message}`);
    }
  };

  const handleDeleteOverride = async (id: number) => {
    try {
      await api.admin.deleteOverride(id);
      loadOverrides();
    } catch (err: any) {
      alert(err.message);
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-extrabold text-navy-900 tracking-tight">Schedule Overrides & Exceptions</h1>
        <p className="mt-1 text-sm text-slate-600">
          Set campus holidays, cancelled lectures, faculty substitutions, and temporary date-specific overrides.
        </p>
      </div>

      {msg && (
        <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs rounded-xl flex items-center space-x-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          <span>{msg}</span>
        </div>
      )}

      {/* Form */}
      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6 sm:p-8">
        <h2 className="text-lg font-bold text-navy-900 flex items-center space-x-2 mb-4">
          <CalendarX className="w-5 h-5 text-amber-600" />
          <span>Create Schedule Override</span>
        </h2>

        <form onSubmit={handleCreateOverride} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">Date</label>
              <input
                type="date"
                required
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-sm font-semibold"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">Override Type</label>
              <select
                value={overrideType}
                onChange={(e: any) => setOverrideType(e.target.value)}
                className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-sm font-bold text-amber-900"
              >
                <option value="CANCELLED">Lecture Cancelled</option>
                <option value="SUBSTITUTION">Faculty Substitution</option>
                <option value="SPECIAL_CLASS">Special / Rescheduled Class</option>
                <option value="HOLIDAY">Full Day Campus Holiday</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">Target Department</label>
              <input
                type="text"
                value={dept}
                onChange={(e) => setDept(e.target.value.toUpperCase())}
                placeholder="CSE or ALL"
                className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-sm font-bold uppercase"
              />
            </div>
          </div>

          {overrideType !== 'HOLIDAY' && (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 p-4 bg-slate-50 rounded-2xl border border-slate-200/80">
              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase mb-1">Start Time</label>
                <input
                  type="text"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                  placeholder="09:30"
                  className="w-full bg-white border border-slate-300 rounded-xl px-2.5 py-1.5 text-xs font-mono"
                />
              </div>

              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase mb-1">End Time</label>
                <input
                  type="text"
                  value={endTime}
                  onChange={(e) => setEndTime(e.target.value)}
                  placeholder="10:25"
                  className="w-full bg-white border border-slate-300 rounded-xl px-2.5 py-1.5 text-xs font-mono"
                />
              </div>

              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase mb-1">Section</label>
                <input
                  type="text"
                  value={section}
                  onChange={(e) => setSection(e.target.value.toUpperCase())}
                  placeholder="A or ALL"
                  className="w-full bg-white border border-slate-300 rounded-xl px-2.5 py-1.5 text-xs font-bold uppercase"
                />
              </div>

              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase mb-1">Semester</label>
                <input
                  type="number"
                  value={semester}
                  onChange={(e) => setSemester(Number(e.target.value))}
                  className="w-full bg-white border border-slate-300 rounded-xl px-2.5 py-1.5 text-xs font-bold"
                />
              </div>
            </div>
          )}

          {overrideType === 'SUBSTITUTION' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">
                  Substitute Faculty Name
                </label>
                <input
                  type="text"
                  value={faculty}
                  onChange={(e) => setFaculty(e.target.value)}
                  placeholder="e.g. Dr. A. Sharma"
                  className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">
                  Subject / Topic
                </label>
                <input
                  type="text"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  placeholder="Optional replacement subject"
                  className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-sm"
                />
              </div>
            </div>
          )}

          <div>
            <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">
              Reason / Official Note
            </label>
            <input
              type="text"
              required
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. University Convocation, Faculty on Leave, Campus Maintenance"
              className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3.5 py-2 text-sm text-slate-800"
            />
          </div>

          <button
            type="submit"
            className="px-6 py-2.5 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-bold shadow-xs cursor-pointer flex items-center space-x-1.5"
          >
            <Plus className="w-4 h-4" />
            <span>Apply Override</span>
          </button>
        </form>
      </div>

      {/* List */}
      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6 sm:p-8">
        <h2 className="text-lg font-bold text-navy-900 mb-4">Active & Scheduled Overrides</h2>
        {overrides.length === 0 ? (
          <p className="text-xs text-slate-500 py-4">No active overrides found.</p>
        ) : (
          <div className="overflow-x-auto custom-scrollbar">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-slate-200 text-slate-400 uppercase font-bold">
                  <th className="py-2.5 px-3">Date</th>
                  <th className="py-2.5 px-3">Type</th>
                  <th className="py-2.5 px-3">Target</th>
                  <th className="py-2.5 px-3">Slot</th>
                  <th className="py-2.5 px-3">Reason</th>
                  <th className="py-2.5 px-3">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {overrides.map((ov) => (
                  <tr key={ov.id} className="hover:bg-slate-50">
                    <td className="py-2.5 px-3 font-mono font-bold text-navy-900">{ov.date}</td>
                    <td className="py-2.5 px-3">
                      <span className="px-2 py-0.5 rounded font-bold uppercase tracking-wider text-[10px] bg-amber-100 text-amber-800">
                        {ov.override_type}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 font-bold">
                      {ov.department} Sec {ov.section}
                    </td>
                    <td className="py-2.5 px-3 font-mono text-slate-500">
                      {ov.start_time && ov.end_time ? `${ov.start_time} – ${ov.end_time}` : 'All Day'}
                    </td>
                    <td className="py-2.5 px-3 text-slate-700">{ov.reason}</td>
                    <td className="py-2.5 px-3">
                      <button
                        onClick={() => handleDeleteOverride(ov.id)}
                        className="text-slate-400 hover:text-red-500 p-1 cursor-pointer"
                        title="Delete"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
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

