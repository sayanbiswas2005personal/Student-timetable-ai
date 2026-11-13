import React, { useState, useEffect } from 'react';
import { Navbar } from '../components/Navbar';
import { api, FacultySearchResult, DepartmentItem, AcademicStructureItem } from '../services/api';
import {
  Search,
  Clock,
  MapPin,
  User as UserIcon,
  BookOpen,
  Calendar,
  AlertTriangle,
  Info,
  CheckCircle,
  HelpCircle,
  Sparkles,
  ArrowRight,
  SlidersHorizontal,
  RefreshCw
} from 'lucide-react';

export const FacultySearchPage: React.FC = () => {
  // Filter state
  const [departments, setDepartments] = useState<DepartmentItem[]>([]);
  const [academicStructures, setAcademicStructures] = useState<AcademicStructureItem[]>([]);

  const [selectedDept, setSelectedDept] = useState('CSE');
  const [selectedYear, setSelectedYear] = useState<number>(2);
  const [selectedSem, setSelectedSem] = useState<number>(3);
  const [selectedSection, setSelectedSection] = useState('A');

  // Time simulation state
  const [enableSimulation, setEnableSimulation] = useState(false);
  const [simulatedDate, setSimulatedDate] = useState('2026-11-02'); // Monday
  const [simulatedTime, setSimulatedTime] = useState('10:00');

  // Search Results
  const [loading, setLoading] = useState(false);
  const [searchResult, setSearchResult] = useState<FacultySearchResult | null>(null);
  const [searchError, setSearchError] = useState<string | null>(null);

  // Initial load
  useEffect(() => {
    const loadMetadata = async () => {
      try {
        const [depts, structs] = await Promise.all([
          api.search.getDepartments(),
          api.search.getAcademicStructures(),
        ]);
        setDepartments(depts);
        setAcademicStructures(structs);

        const initialDepartment = depts.find((dept) => dept.code === selectedDept)?.code ?? depts[0]?.code;
        const initialStructure = structs.find(
          (structure) =>
            structure.department_code === initialDepartment &&
            structure.year === selectedYear &&
            structure.semester === selectedSem &&
            structure.section === selectedSection
        ) ?? structs.find((structure) => structure.department_code === initialDepartment) ?? structs[0];
        if (initialStructure) {
          setSelectedDept(initialStructure.department_code);
          setSelectedYear(initialStructure.year);
          setSelectedSem(initialStructure.semester);
          setSelectedSection(initialStructure.section);
        }
      } catch (err) {
        console.error('Failed to load metadata', err);
      }
    };
    loadMetadata();
  }, []);

  // Update available semesters/sections when dept or year changes
  const departmentStructures = academicStructures.filter(
    (structure) => structure.department_code.toUpperCase() === selectedDept.toUpperCase()
  );
  const availableYears = [...new Set(departmentStructures.map((structure) => structure.year))].sort((a, b) => a - b);
  const availableSemesters = [...new Set(
    departmentStructures
      .filter((structure) => structure.year === selectedYear)
      .map((structure) => structure.semester)
  )].sort((a, b) => a - b);

  // Filter sections matching current selection from academicStructures
  const matchingSections = academicStructures
    .filter(
      (s) =>
        s.department_code.toUpperCase() === selectedDept.toUpperCase() &&
        s.year === selectedYear &&
        s.semester === selectedSem
    )
    .map((s) => s.section);

  const availableSections = matchingSections;

  // Execute search
  const handleSearch = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setLoading(true);
    setSearchError(null);

    try {
      const res = await api.search.getCurrentClass({
        department: selectedDept,
        year: selectedYear,
        semester: selectedSem,
        section: selectedSection,
        simulated_date: enableSimulation ? simulatedDate : undefined,
        simulated_time: enableSimulation ? simulatedTime : undefined,
      });
      setSearchResult(res);
    } catch (err: any) {
      setSearchError(err.message || 'Failed to check timetable.');
      setSearchResult(null);
    } finally {
      setLoading(false);
    }
  };

  // Perform search on mount once metadata is loaded
  useEffect(() => {
    handleSearch();
  }, [selectedDept, selectedYear, selectedSem, selectedSection]);

  const getBadgeStyle = (color: string) => {
    switch (color) {
      case 'green':
        return 'bg-emerald-50 text-emerald-700 border-emerald-200 ring-emerald-500/20';
      case 'blue':
        return 'bg-blue-50 text-blue-700 border-blue-200 ring-blue-500/20';
      case 'yellow':
        return 'bg-amber-50 text-amber-700 border-amber-200 ring-amber-500/20';
      case 'red':
        return 'bg-rose-50 text-rose-700 border-rose-200 ring-rose-500/20';
      default:
        return 'bg-slate-50 text-slate-700 border-slate-200 ring-slate-500/20';
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <Navbar />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="mb-8">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <h1 className="text-2xl sm:text-3xl font-extrabold text-navy-900 tracking-tight">
                Student Timetable Verification
              </h1>
              <p className="mt-1 text-sm text-slate-600">
                Check active lecture schedules, expected rooms, and faculty for any student section in real time.
              </p>
            </div>

            {/* Time Simulation Toggle */}
            <div className="flex items-center space-x-3">
              <button
                type="button"
                onClick={() => setEnableSimulation(!enableSimulation)}
                className={`flex items-center space-x-2 px-3.5 py-2 rounded-xl text-xs font-semibold border transition-all cursor-pointer ${
                  enableSimulation
                    ? 'bg-amber-50 border-amber-300 text-amber-900 shadow-sm'
                    : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
                }`}
              >
                <SlidersHorizontal className="w-3.5 h-3.5 text-amber-600" />
                <span>{enableSimulation ? 'Simulation Mode Active' : 'Test / Simulate Time'}</span>
              </button>
            </div>
          </div>

          {/* Time Simulation Panel */}
          {enableSimulation && (
            <div className="mt-4 p-4 bg-amber-50/80 border border-amber-200/90 rounded-2xl flex flex-wrap items-center gap-4 text-xs text-amber-900 animate-fadeIn">
              <div className="flex items-center space-x-2 font-bold text-amber-900">
                <Sparkles className="w-4 h-4 text-amber-600" />
                <span>Simulation Settings:</span>
              </div>
              <div className="flex items-center space-x-2">
                <label className="font-medium text-amber-800">Date:</label>
                <input
                  type="date"
                  value={simulatedDate}
                  onChange={(e) => setSimulatedDate(e.target.value)}
                  className="px-2.5 py-1.5 bg-white border border-amber-200 rounded-lg text-slate-800 focus:outline-none focus:ring-1 focus:ring-amber-500 font-mono"
                />
              </div>
              <div className="flex items-center space-x-2">
                <label className="font-medium text-amber-800">Time (HH:MM):</label>
                <input
                  type="time"
                  value={simulatedTime}
                  onChange={(e) => setSimulatedTime(e.target.value)}
                  className="px-2.5 py-1.5 bg-white border border-amber-200 rounded-lg text-slate-800 focus:outline-none focus:ring-1 focus:ring-amber-500 font-mono"
                />
              </div>
              <button
                type="button"
                onClick={() => handleSearch()}
                className="px-3 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-lg font-bold shadow-xs transition-colors cursor-pointer"
              >
                Apply Simulation
              </button>
              <button
                type="button"
                onClick={() => setEnableSimulation(false)}
                className="text-amber-800 underline hover:text-amber-950 font-medium ml-auto"
              >
                Reset to Live IST
              </button>
            </div>
          )}
        </div>

        {/* Filter Dropdown Card */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm p-6 mb-8">
          <form onSubmit={handleSearch} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 items-end">
            {/* Department */}
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-1.5">
                Department
              </label>
              <select
                value={selectedDept}
                onChange={(e) => {
                  const department = e.target.value;
                  const firstStructure = academicStructures.find((structure) => structure.department_code === department);
                  setSelectedDept(department);
                  if (firstStructure) {
                    setSelectedYear(firstStructure.year);
                    setSelectedSem(firstStructure.semester);
                    setSelectedSection(firstStructure.section);
                  }
                }}
                className="w-full bg-slate-50/80 border border-slate-300 rounded-xl px-3.5 py-2.5 text-sm font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
              >
                {departments.map((d) => (
                  <option key={d.code} value={d.code}>
                    {d.code} — {d.name}
                  </option>
                ))}
              </select>
            </div>

            {/* Academic Year */}
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-1.5">
                Academic Year
              </label>
              <select
                value={selectedYear}
                onChange={(e) => {
                  const yr = Number(e.target.value);
                  const firstStructure = departmentStructures.find((structure) => structure.year === yr);
                  setSelectedYear(yr);
                  if (firstStructure) {
                    setSelectedSem(firstStructure.semester);
                    setSelectedSection(firstStructure.section);
                  }
                }}
                className="w-full bg-slate-50/80 border border-slate-300 rounded-xl px-3.5 py-2.5 text-sm font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
              >
                {availableYears.map((year) => (
                  <option key={year} value={year}>
                    {year}{year === 1 ? 'st' : year === 2 ? 'nd' : year === 3 ? 'rd' : 'th'} Year
                  </option>
                ))}
              </select>
            </div>

            {/* Semester */}
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-1.5">
                Semester
              </label>
              <select
                value={selectedSem}
                onChange={(e) => {
                  const semester = Number(e.target.value);
                  const firstStructure = departmentStructures.find(
                    (structure) => structure.year === selectedYear && structure.semester === semester
                  );
                  setSelectedSem(semester);
                  if (firstStructure) setSelectedSection(firstStructure.section);
                }}
                className="w-full bg-slate-50/80 border border-slate-300 rounded-xl px-3.5 py-2.5 text-sm font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
              >
                {availableSemesters.map((semester) => (
                  <option key={semester} value={semester}>
                    Semester {semester}
                  </option>
                ))}
              </select>
            </div>

            {/* Section */}
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 mb-1.5">
                Section
              </label>
              <select
                value={selectedSection}
                onChange={(e) => setSelectedSection(e.target.value)}
                className="w-full bg-slate-50/80 border border-slate-300 rounded-xl px-3.5 py-2.5 text-sm font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
              >
                {availableSections.map((sec) => (
                  <option key={sec} value={sec}>
                    Section {sec}
                  </option>
                ))}
              </select>
            </div>

            {/* Search Button */}
            <div>
              <button
                type="submit"
                disabled={loading}
                className="w-full h-11 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-bold text-sm shadow-md shadow-blue-500/20 flex items-center justify-center space-x-2 transition-all cursor-pointer disabled:opacity-50"
              >
                {loading ? (
                  <RefreshCw className="w-4 h-4 animate-spin" />
                ) : (
                  <>
                    <Search className="w-4 h-4" />
                    <span>Verify Schedule</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>

        {/* Error message */}
        {searchError && (
          <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-2xl text-red-700 text-sm flex items-center space-x-3">
            <AlertTriangle className="w-5 h-5 shrink-0 text-red-500" />
            <span>{searchError}</span>
          </div>
        )}

        {/* Results Area */}
        {searchResult && (
          <div className="space-y-6">
            {/* Primary Status Card */}
            <div className="bg-white rounded-3xl border border-slate-200/90 shadow-sm overflow-hidden">
              <div className="p-6 sm:p-8">
                {/* Header row: Status Badge & Query Context */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-6 border-b border-slate-100">
                  <div className="flex items-center space-x-3">
                    <span
                      className={`inline-flex items-center px-4 py-1.5 rounded-full text-xs font-bold tracking-wide uppercase border ring-1 ${getBadgeStyle(
                        searchResult.status_badge_color
                      )}`}
                    >
                      {searchResult.status_label}
                    </span>
                    {searchResult.is_simulated && (
                      <span className="text-xs bg-amber-100 text-amber-800 px-2.5 py-1 rounded-full font-semibold">
                        Simulated Time
                      </span>
                    )}
                  </div>

                  <div className="flex items-center space-x-3 text-xs text-slate-500 font-medium">
                    <span className="flex items-center space-x-1">
                      <Calendar className="w-3.5 h-3.5 text-slate-400" />
                      <span>{searchResult.query_day}, {searchResult.query_date}</span>
                    </span>
                    <span>•</span>
                    <span className="flex items-center space-x-1 font-mono font-bold text-slate-700">
                      <Clock className="w-3.5 h-3.5 text-blue-500" />
                      <span>{searchResult.query_time}</span>
                    </span>
                  </div>
                </div>

                {/* Primary Result Details */}
                <div className="mt-6">
                  {searchResult.current_class ? (
                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                      {/* Left: Main Subject & Details */}
                      <div className="lg:col-span-2 space-y-4">
                        <div>
                          <span className="text-xs font-bold text-blue-600 uppercase tracking-wider">
                            Subject in Progress
                          </span>
                          <h2 className="text-2xl sm:text-3xl font-extrabold text-navy-900 mt-1">
                            {searchResult.current_class.subject}
                          </h2>
                          {searchResult.current_class.subject_code && (
                            <span className="inline-block mt-1 font-mono text-xs bg-slate-100 text-slate-700 px-2.5 py-0.5 rounded-md font-medium">
                              Code: {searchResult.current_class.subject_code}
                            </span>
                          )}
                        </div>

                        {/* Metadata Grid */}
                        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 pt-4 border-t border-slate-100">
                          <div>
                            <div className="text-xs text-slate-400 uppercase font-semibold">Faculty</div>
                            <div className="text-sm font-bold text-slate-800 flex items-center space-x-1.5 mt-0.5">
                              <UserIcon className="w-4 h-4 text-blue-500 shrink-0" />
                              <span className="truncate">{searchResult.current_class.faculty || 'Assigned Faculty'}</span>
                            </div>
                          </div>

                          <div>
                            <div className="text-xs text-slate-400 uppercase font-semibold">Room / Lab</div>
                            <div className="text-sm font-bold text-slate-800 flex items-center space-x-1.5 mt-0.5">
                              <MapPin className="w-4 h-4 text-rose-500 shrink-0" />
                              <span>{searchResult.current_class.room || 'AU6 Campus'}</span>
                            </div>
                          </div>

                          <div>
                            <div className="text-xs text-slate-400 uppercase font-semibold">Period Timing</div>
                            <div className="text-sm font-bold text-slate-800 flex items-center space-x-1.5 mt-0.5">
                              <Clock className="w-4 h-4 text-amber-500 shrink-0" />
                              <span className="font-mono">
                                {searchResult.current_class.start_time} – {searchResult.current_class.end_time}
                              </span>
                            </div>
                          </div>
                        </div>

                        {/* Prompt Message */}
                        <div className="mt-4 p-4 rounded-2xl bg-blue-50/80 border border-blue-200 text-blue-900 text-sm font-medium flex items-start space-x-3">
                          <Info className="w-5 h-5 text-blue-600 shrink-0 mt-0.5" />
                          <div>{searchResult.message}</div>
                        </div>
                      </div>

                      {/* Right: Section Badge Card */}
                      <div className="bg-slate-50 rounded-2xl p-5 border border-slate-200/80 flex flex-col justify-between">
                        <div>
                          <div className="text-xs uppercase font-bold text-slate-400">Class Target</div>
                          <div className="text-xl font-black text-navy-900 mt-1">
                            {searchResult.department} — Sec {searchResult.section}
                          </div>
                          <div className="text-xs text-slate-600 font-medium mt-1">
                            {searchResult.year === 1
                              ? '1st Year'
                              : searchResult.year === 2
                              ? '2nd Year'
                              : searchResult.year === 3
                              ? '3rd Year'
                              : '4th Year'} • Semester {searchResult.semester}
                          </div>
                        </div>

                        {/* Next Class Preview */}
                        <div className="mt-6 pt-4 border-t border-slate-200">
                          <div className="text-[11px] uppercase font-bold text-slate-400 flex items-center space-x-1">
                            <span>Next Lecture</span>
                            <ArrowRight className="w-3 h-3 text-slate-400" />
                          </div>
                          {searchResult.next_class ? (
                            <div className="mt-1.5">
                              <div className="text-sm font-bold text-slate-800 truncate">
                                {searchResult.next_class.subject}
                              </div>
                              <div className="text-xs text-slate-500 font-mono mt-0.5">
                                {searchResult.next_class.start_time} – {searchResult.next_class.end_time}
                              </div>
                            </div>
                          ) : (
                            <div className="text-xs text-slate-500 mt-1">No subsequent lectures today.</div>
                          )}
                        </div>
                      </div>
                    </div>
                  ) : (
                    /* Free Period / No Class State */
                    <div className="py-6">
                      <div className="max-w-xl">
                        <h2 className="text-2xl font-extrabold text-slate-800">
                          {searchResult.status_label}
                        </h2>
                        <p className="mt-2 text-base text-slate-600 font-medium">
                          {searchResult.message}
                        </p>

                        {searchResult.next_class && (
                          <div className="mt-6 p-4 rounded-2xl bg-blue-50 border border-blue-200 text-blue-950 flex items-start space-x-3">
                            <Clock className="w-5 h-5 text-blue-600 shrink-0 mt-0.5" />
                            <div>
                              <span className="text-xs uppercase tracking-wider font-bold text-blue-700 block">
                                Upcoming Lecture
                              </span>
                              <span className="font-bold text-base block mt-0.5">
                                {searchResult.next_class.subject}
                              </span>
                              <span className="text-xs text-blue-800 font-mono">
                                Scheduled at {searchResult.next_class.start_time} – {searchResult.next_class.end_time} • {searchResult.next_class.room || 'AU6 Campus'}
                              </span>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </div>

                {/* Conflict Alert if any */}
                {searchResult.has_conflict && (
                  <div className="mt-6 p-4 bg-rose-50 border border-rose-200 rounded-2xl text-rose-800 text-sm">
                    <div className="font-bold flex items-center space-x-2">
                      <AlertTriangle className="w-4 h-4 text-rose-600" />
                      <span>Timetable Conflict Detected:</span>
                    </div>
                    <ul className="mt-2 list-disc list-inside text-xs space-y-1">
                      {searchResult.conflicting_entries.map((c, i) => (
                        <li key={i}>
                          <span className="font-semibold">{c.subject}</span> ({c.start_time}-{c.end_time})
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>

              {/* Mandatory Policy Disclaimer Footer */}
              <div className="px-6 py-4 bg-slate-100/80 border-t border-slate-200 flex items-start space-x-3">
                <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                <p className="text-xs text-slate-600 leading-relaxed font-medium">
                  <span className="font-bold text-slate-800">Institutional Policy Notice:</span>{' '}
                  {searchResult.disclaimer}
                </p>
              </div>
            </div>

            {/* Today's Full Schedule Table */}
            {searchResult.today_schedule && searchResult.today_schedule.length > 0 && (
              <div className="bg-white rounded-3xl border border-slate-200/90 shadow-sm p-6 sm:p-8">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h3 className="text-lg font-bold text-navy-900">
                      Today's Expected Schedule ({searchResult.query_day})
                    </h3>
                    <p className="text-xs text-slate-500">
                      Full class sequence for {searchResult.department} Year {searchResult.year} Sem {searchResult.semester} Sec {searchResult.section}
                    </p>
                  </div>
                  <span className="text-xs font-mono font-bold bg-slate-100 text-slate-700 px-3 py-1 rounded-full">
                    {searchResult.today_schedule.length} Periods
                  </span>
                </div>

                <div className="overflow-x-auto custom-scrollbar">
                  <table className="w-full text-left text-sm border-collapse">
                    <thead>
                      <tr className="border-b border-slate-200 text-xs uppercase font-bold text-slate-400">
                        <th className="py-3 px-3">Time</th>
                        <th className="py-3 px-3">Subject</th>
                        <th className="py-3 px-3">Code</th>
                        <th className="py-3 px-3">Faculty</th>
                        <th className="py-3 px-3">Room</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {searchResult.today_schedule.map((slot, i) => {
                        const isCurrentSlot =
                          searchResult.current_class &&
                          searchResult.current_class.start_time === slot.start_time;

                        return (
                          <tr
                            key={i}
                            className={`transition-colors ${
                              isCurrentSlot
                                ? 'bg-blue-50/70 font-semibold text-blue-950'
                                : 'hover:bg-slate-50 text-slate-700'
                            }`}
                          >
                            <td className="py-3 px-3 font-mono font-medium text-xs whitespace-nowrap">
                              <span className="flex items-center space-x-1.5">
                                {isCurrentSlot && (
                                  <span className="w-2 h-2 rounded-full bg-blue-600 animate-ping" />
                                )}
                                <span>{slot.start_time} – {slot.end_time}</span>
                              </span>
                            </td>
                            <td className="py-3 px-3 font-medium">
                              {slot.subject}
                            </td>
                            <td className="py-3 px-3 font-mono text-xs text-slate-500">
                              {slot.subject_code || '—'}
                            </td>
                            <td className="py-3 px-3 text-xs text-slate-600">
                              {slot.faculty || '—'}
                            </td>
                            <td className="py-3 px-3 text-xs text-slate-600">
                              {slot.room ? (
                                <span className="bg-slate-100 px-2 py-0.5 rounded text-slate-700 font-mono">
                                  {slot.room}
                                </span>
                              ) : (
                                '—'
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
};
