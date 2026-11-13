import React, { useState, useEffect } from 'react';
import { api, DepartmentItem, AcademicStructureItem } from '../../services/api';
import { Building2, CalendarDays, Plus, Trash2, CheckCircle2 } from 'lucide-react';

export const AdminAcademicPage: React.FC = () => {
  const [departments, setDepartments] = useState<DepartmentItem[]>([]);
  const [structures, setStructures] = useState<AcademicStructureItem[]>([]);

  // Add Dept form
  const [newDeptCode, setNewDeptCode] = useState('');
  const [newDeptName, setNewDeptName] = useState('');

  // Add Structure form
  const [structDept, setStructDept] = useState('CSE');
  const [structYear, setStructYear] = useState(1);
  const [structSem, setStructSem] = useState(1);
  const [structSec, setStructSec] = useState('A');
  const [structAcYear, setStructAcYear] = useState('2026-2027');

  const [msg, setMsg] = useState<string | null>(null);

  const loadData = async () => {
    try {
      const [d, s] = await Promise.all([
        api.admin.getDepartments(),
        api.admin.getStructures(),
      ]);
      setDepartments(d);
      setStructures(s);
      if (d.length > 0 && !structDept) {
        setStructDept(d[0].code);
      }
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleAddDept = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newDeptCode || !newDeptName) return;
    try {
      await api.admin.createDepartment({ code: newDeptCode, name: newDeptName });
      setNewDeptCode('');
      setNewDeptName('');
      setMsg('Department added successfully.');
      loadData();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const handleDeleteDept = async (id: number) => {
    if (!window.confirm('Delete this department?')) return;
    try {
      await api.admin.deleteDepartment(id);
      loadData();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const handleAddStructure = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.admin.createStructure({
        department_code: structDept,
        academic_year: structAcYear,
        year: structYear,
        semester: structSem,
        section: structSec,
      });
      setMsg('Section structure added.');
      loadData();
    } catch (err: any) {
      alert(err.message);
    }
  };

  const handleDeleteStructure = async (id: number) => {
    try {
      await api.admin.deleteStructure(id);
      loadData();
    } catch (err: any) {
      alert(err.message);
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-extrabold text-navy-900 tracking-tight">Academic Structure Configuration</h1>
        <p className="mt-1 text-sm text-slate-600">
          Configure institutional departments, degree years, semesters, and sections for student lookup dropdowns.
        </p>
      </div>

      {msg && (
        <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs rounded-xl flex items-center space-x-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          <span>{msg}</span>
        </div>
      )}

      {/* Departments Section */}
      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6 sm:p-8">
        <h2 className="text-lg font-bold text-navy-900 flex items-center space-x-2 mb-4">
          <Building2 className="w-5 h-5 text-blue-600" />
          <span>Academic Departments</span>
        </h2>

        {/* Add Dept Form */}
        <form onSubmit={handleAddDept} className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-6 p-4 bg-slate-50 rounded-2xl border border-slate-200/80">
          <input
            type="text"
            required
            placeholder="Code (e.g. CSE)"
            value={newDeptCode}
            onChange={(e) => setNewDeptCode(e.target.value.toUpperCase())}
            className="bg-white border border-slate-300 rounded-xl px-3 py-2 text-xs font-bold uppercase"
          />
          <input
            type="text"
            required
            placeholder="Department Name (e.g. Computer Science)"
            value={newDeptName}
            onChange={(e) => setNewDeptName(e.target.value)}
            className="bg-white border border-slate-300 rounded-xl px-3 py-2 text-xs font-medium text-slate-800"
          />
          <button
            type="submit"
            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold flex items-center justify-center space-x-1 shadow-xs cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Department</span>
          </button>
        </form>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {departments.map((d) => (
            <div
              key={d.id}
              className="p-3.5 rounded-xl border border-slate-200 bg-white flex items-center justify-between shadow-2xs"
            >
              <div>
                <span className="text-xs font-black text-navy-900 uppercase tracking-wider">{d.code}</span>
                <p className="text-xs text-slate-600 truncate max-w-[180px]">{d.name}</p>
              </div>
              <button
                onClick={() => handleDeleteDept(d.id)}
                className="text-slate-400 hover:text-red-500 p-1 cursor-pointer"
                title="Remove"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Sections & Structures Section */}
      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6 sm:p-8">
        <h2 className="text-lg font-bold text-navy-900 flex items-center space-x-2 mb-4">
          <CalendarDays className="w-5 h-5 text-indigo-600" />
          <span>Class Sections & Semesters</span>
        </h2>

        {/* Add Structure Form */}
        <form onSubmit={handleAddStructure} className="grid grid-cols-2 sm:grid-cols-6 gap-3 mb-6 p-4 bg-slate-50 rounded-2xl border border-slate-200/80 items-end">
          <div>
            <label className="block text-[10px] uppercase font-bold text-slate-400 mb-1">Dept</label>
            <select
              value={structDept}
              onChange={(e) => setStructDept(e.target.value)}
              className="w-full bg-white border border-slate-300 rounded-xl px-2.5 py-1.5 text-xs font-bold"
            >
              {departments.map((d) => (
                <option key={d.code} value={d.code}>
                  {d.code}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-[10px] uppercase font-bold text-slate-400 mb-1">Year</label>
            <select
              value={structYear}
              onChange={(e) => setStructYear(Number(e.target.value))}
              className="w-full bg-white border border-slate-300 rounded-xl px-2.5 py-1.5 text-xs font-bold"
            >
              <option value={1}>1st Year</option>
              <option value={2}>2nd Year</option>
              <option value={3}>3rd Year</option>
              <option value={4}>4th Year</option>
            </select>
          </div>

          <div>
            <label className="block text-[10px] uppercase font-bold text-slate-400 mb-1">Semester</label>
            <input
              type="number"
              min={1}
              max={8}
              value={structSem}
              onChange={(e) => setStructSem(Number(e.target.value))}
              className="w-full bg-white border border-slate-300 rounded-xl px-2.5 py-1.5 text-xs font-bold"
            />
          </div>

          <div>
            <label className="block text-[10px] uppercase font-bold text-slate-400 mb-1">Section</label>
            <input
              type="text"
              required
              value={structSec}
              onChange={(e) => setStructSec(e.target.value.toUpperCase())}
              placeholder="e.g. A"
              className="w-full bg-white border border-slate-300 rounded-xl px-2.5 py-1.5 text-xs font-bold uppercase"
            />
          </div>

          <div>
            <label className="block text-[10px] uppercase font-bold text-slate-400 mb-1">Session</label>
            <input
              type="text"
              value={structAcYear}
              onChange={(e) => setStructAcYear(e.target.value)}
              className="w-full bg-white border border-slate-300 rounded-xl px-2.5 py-1.5 text-xs font-medium"
            />
          </div>

          <button
            type="submit"
            className="w-full py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold flex items-center justify-center space-x-1 shadow-xs cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add</span>
          </button>
        </form>

        <div className="overflow-x-auto custom-scrollbar border border-slate-200 rounded-2xl max-h-[400px]">
          <table className="w-full text-left text-xs border-collapse">
            <thead className="sticky top-0 bg-slate-100 z-10 text-slate-500 font-bold uppercase tracking-wider border-b border-slate-200">
              <tr>
                <th className="py-2.5 px-3">Dept</th>
                <th className="py-2.5 px-3">Academic Year</th>
                <th className="py-2.5 px-3">Year</th>
                <th className="py-2.5 px-3">Semester</th>
                <th className="py-2.5 px-3">Section</th>
                <th className="py-2.5 px-3">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {structures.map((s) => (
                <tr key={s.id} className="hover:bg-slate-50">
                  <td className="py-2 px-3 font-bold text-navy-900">{s.department_code}</td>
                  <td className="py-2 px-3 font-mono text-slate-500">{s.academic_year}</td>
                  <td className="py-2 px-3">{s.year}</td>
                  <td className="py-2 px-3 font-bold text-indigo-700">Sem {s.semester}</td>
                  <td className="py-2 px-3 font-bold">Sec {s.section}</td>
                  <td className="py-2 px-3">
                    <button
                      onClick={() => handleDeleteStructure(s.id)}
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
      </div>
    </div>
  );
};

