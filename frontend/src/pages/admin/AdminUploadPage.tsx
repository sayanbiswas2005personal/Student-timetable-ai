import React, { useState, useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, TimetablePreview, TimetableEntry } from '../../services/api';
import {
  Upload,
  FileText,
  Sparkles,
  CheckCircle,
  AlertTriangle,
  AlertCircle,
  Trash2,
  Plus,
  Save,
  Rocket,
  RefreshCw,
  Cpu
} from 'lucide-react';

export const AdminUploadPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const timetableIdParam = searchParams.get('timetable_id');

  // File Upload State
  const [file, setFile] = useState<File | null>(null);
  const [academicYear, setAcademicYear] = useState('2026-2027');
  const [department, setDepartment] = useState('CSE');
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  // Active Timetable Record
  const [currentTimetableId, setCurrentTimetableId] = useState<number | null>(
    timetableIdParam ? Number(timetableIdParam) : null
  );

  // Extraction State
  const [extractionProvider, setExtractionProvider] = useState<'gemini' | 'layout_parser'>('layout_parser');
  const [pageLimit, setPageLimit] = useState<number | undefined>();
  const [extracting, setExtracting] = useState(false);
  const [extractError, setExtractError] = useState<string | null>(null);

  // Preview & Editor State
  const [preview, setPreview] = useState<TimetablePreview | null>(null);
  const [editableEntries, setEditableEntries] = useState<TimetableEntry[]>([]);
  const [savingEdits, setSavingEdits] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Fetch preview when timetable ID changes
  const loadPreview = async (id: number) => {
    try {
      const data = await api.admin.getPreview(id);
      setPreview(data);
      if (data.data?.entries) {
        setEditableEntries(data.data.entries);
      }
    } catch (err: any) {
      console.error('Failed to load timetable preview', err);
    }
  };

  useEffect(() => {
    if (currentTimetableId) {
      loadPreview(currentTimetableId);
    }
  }, [currentTimetableId]);

  // Handle file select
  const handleFileDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const selected = e.dataTransfer.files[0];
      if (selected.name.toLowerCase().endsWith('.pdf')) {
        setFile(selected);
        setUploadError(null);
      } else {
        setUploadError('Only PDF files (.pdf) are allowed.');
      }
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selected = e.target.files[0];
      if (selected.name.toLowerCase().endsWith('.pdf')) {
        setFile(selected);
        setUploadError(null);
      } else {
        setUploadError('Only PDF files (.pdf) are allowed.');
      }
    }
  };

  // Upload PDF
  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setUploadError('Please select a PDF file first.');
      return;
    }

    setUploading(true);
    setUploadError(null);
    setActionSuccess(null);

    const formData = new FormData();
    formData.append('file', file);
    formData.append('academic_year', academicYear);
    formData.append('department', department);

    try {
      const res = await api.admin.uploadPdf(formData);
      setCurrentTimetableId(res.id);
      setSearchParams({ timetable_id: res.id.toString() });
      setActionSuccess(`File uploaded successfully! Timetable ID #${res.id} is ready for extraction.`);
      await loadPreview(res.id);
    } catch (err: any) {
      setUploadError(err.message || 'PDF upload failed.');
    } finally {
      setUploading(false);
    }
  };

  // Run Extraction
  const handleExtract = async () => {
    if (!currentTimetableId) return;

    setExtracting(true);
    setExtractError(null);
    setActionSuccess(null);

    try {
      const res = await api.admin.extractTimetable(
        currentTimetableId,
        extractionProvider,
        pageLimit && pageLimit > 0 ? pageLimit : undefined
      );
      setActionSuccess(`Extraction completed: ${res.total_entries} entries extracted via ${res.extraction_provider}.`);
      await loadPreview(currentTimetableId);
    } catch (err: any) {
      setExtractError(err.message || 'Timetable extraction failed.');
    } finally {
      setExtracting(false);
    }
  };

  // Edit entry in table
  const handleEntryChange = (index: number, field: keyof TimetableEntry, value: any) => {
    const updated = [...editableEntries];
    updated[index] = { ...updated[index], [field]: value };
    setEditableEntries(updated);
  };

  // Delete entry row
  const handleDeleteEntry = (index: number) => {
    const updated = editableEntries.filter((_, i) => i !== index);
    setEditableEntries(updated);
  };

  // Add new empty entry row
  const handleAddEntry = () => {
    const newEntry: TimetableEntry = {
      day: 'MONDAY',
      start_time: '09:30',
      end_time: '10:25',
      department: department || 'CSE',
      year: 2,
      semester: 3,
      section: 'A',
      subject: 'New Subject',
      subject_code: '',
      faculty: '',
      room: 'AU6-LAB',
    };
    setEditableEntries([newEntry, ...editableEntries]);
  };

  // Save changes to JSON
  const handleSaveEdits = async () => {
    if (!currentTimetableId) return;
    setSavingEdits(true);
    setActionSuccess(null);

    try {
      const res = await api.admin.updateTimetable(currentTimetableId, {
        academic_year: academicYear,
        entries: editableEntries,
      });
      setActionSuccess(`Changes saved successfully! (${res.total_entries} entries stored)`);
      await loadPreview(currentTimetableId);
    } catch (err: any) {
      alert(`Save failed: ${err.message}`);
    } finally {
      setSavingEdits(false);
    }
  };

  // Approve & Publish
  const handlePublish = async () => {
    if (!currentTimetableId) return;
    if (!window.confirm('Are you sure you want to approve and publish this timetable? It will become the live active schedule for campus verification.')) {
      return;
    }

    setPublishing(true);
    setActionSuccess(null);

    try {
      const res = await api.admin.publishTimetable(currentTimetableId);
      setActionSuccess(`Timetable Published! Version v${res.version} is now active across the campus.`);
      await loadPreview(currentTimetableId);
    } catch (err: any) {
      alert(`Publish failed: ${err.message}`);
    } finally {
      setPublishing(false);
    }
  };

  return (
    <div className="space-y-8">
      {/* Page Title */}
      <div>
        <h1 className="text-2xl font-extrabold text-navy-900 tracking-tight">
          Upload & Process Timetable PDF
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          Upload official timetable PDFs, extract structured schedules using Google Gemini API or Layout Parser, verify and publish.
        </p>
      </div>

      {actionSuccess && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-2xl text-emerald-800 text-sm flex items-center space-x-3">
          <CheckCircle className="w-5 h-5 text-emerald-600 shrink-0" />
          <span className="font-medium">{actionSuccess}</span>
        </div>
      )}

      {/* Step 1: Upload Card */}
      <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6 sm:p-8">
        <h2 className="text-lg font-bold text-navy-900 flex items-center space-x-2 mb-4">
          <span className="w-6 h-6 rounded-full bg-blue-100 text-blue-700 text-xs font-black flex items-center justify-center">
            1
          </span>
          <span>Upload PDF Document</span>
        </h2>

        <form onSubmit={handleUpload} className="space-y-6">
          {/* Dropzone */}
          <div
            onDragOver={(e) => e.preventDefault()}
            onDrop={handleFileDrop}
            onClick={() => fileInputRef.current?.click()}
            className="border-2 border-dashed border-slate-300 hover:border-blue-500 bg-slate-50/50 hover:bg-blue-50/20 rounded-2xl p-8 text-center cursor-pointer transition-all"
          >
            <input
              type="file"
              ref={fileInputRef}
              accept=".pdf"
              onChange={handleFileChange}
              className="hidden"
            />
            <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center mx-auto mb-3">
              <Upload className="w-6 h-6" />
            </div>
            {file ? (
              <div>
                <p className="text-sm font-bold text-navy-900">{file.name}</p>
                <p className="text-xs text-slate-500 mt-1">
                  {(file.size / (1024 * 1024)).toFixed(2)} MB • Ready to upload
                </p>
              </div>
            ) : (
              <div>
                <p className="text-sm font-semibold text-slate-700">
                  Click to select or drag and drop institutional timetable PDF
                </p>
                <p className="text-xs text-slate-400 mt-1">
                  Supports multi-page aSc timetable PDFs up to 25 MB
                </p>
              </div>
            )}
          </div>

          {uploadError && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-xl text-red-700 text-xs flex items-center space-x-2">
              <AlertCircle className="w-4 h-4 shrink-0 text-red-500" />
              <span>{uploadError}</span>
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">
                Academic Year
              </label>
              <input
                type="text"
                value={academicYear}
                onChange={(e) => setAcademicYear(e.target.value)}
                placeholder="2026-2027"
                className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3.5 py-2 text-sm font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">
                Department
              </label>
              <input
                type="text"
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                placeholder="CSE or ALL"
                className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3.5 py-2 text-sm font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={!file || uploading}
            className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl font-bold text-sm shadow-sm transition-all cursor-pointer flex items-center space-x-2"
          >
            {uploading ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin" />
                <span>Uploading Document...</span>
              </>
            ) : (
              <>
                <Upload className="w-4 h-4" />
                <span>Upload PDF</span>
              </>
            )}
          </button>
        </form>
      </div>

      {/* Step 2: Extraction Configuration */}
      {currentTimetableId && (
        <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6 sm:p-8">
          <h2 className="text-lg font-bold text-navy-900 flex items-center space-x-2 mb-4">
            <span className="w-6 h-6 rounded-full bg-blue-100 text-blue-700 text-xs font-black flex items-center justify-center">
              2
            </span>
            <span>Configure AI / Layout Extraction</span>
          </h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
            {/* Option A: Gemini API */}
            <div
              onClick={() => setExtractionProvider('gemini')}
              className={`p-5 rounded-2xl border-2 transition-all cursor-pointer ${
                extractionProvider === 'gemini'
                  ? 'border-blue-600 bg-blue-50/40 ring-2 ring-blue-500/20'
                  : 'border-slate-200 hover:border-slate-300 bg-white'
              }`}
            >
              <div className="flex items-center space-x-3 mb-2">
                <div className="w-8 h-8 rounded-xl bg-blue-600 text-white flex items-center justify-center font-black text-xs shadow-sm">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-navy-900">Google Gemini 3.8 Flash Cloud API</h3>
                  <span className="text-[10px] text-blue-600 font-bold uppercase tracking-wider">Cloud Vision Understanding</span>
                  <div className="flex items-center space-x-2">
                    <span className="text-[10px] text-blue-600 font-bold uppercase tracking-wider">Cloud Vision Understanding</span>
                    <span className="text-slate-300">•</span>
                    <a
                      href="/admin/settings"
                      onClick={(e) => e.stopPropagation()}
                      className="text-[10px] text-blue-700 underline font-semibold hover:text-blue-900"
                    >
                      Configure Key in Settings &rarr;
                    </a>
                  </div>
                </div>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Sends the timetable PDF directly to the official Google Gemini API interactions endpoint. Analyzes complex visual grids, non-standard text spans, and outputs strict Pydantic JSON.
              </p>
            </div>

            {/* Option B: Local Layout Parser */}
            <div
              onClick={() => setExtractionProvider('layout_parser')}
              className={`p-5 rounded-2xl border-2 transition-all cursor-pointer ${
                extractionProvider === 'layout_parser'
                  ? 'border-blue-600 bg-blue-50/40 ring-2 ring-blue-500/20'
                  : 'border-slate-200 hover:border-slate-300 bg-white'
              }`}
            >
              <div className="flex items-center space-x-3 mb-2">
                <div className="w-8 h-8 rounded-xl bg-indigo-600 text-white flex items-center justify-center font-black text-xs shadow-sm">
                  <Cpu className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-navy-900">Institutional Layout Parser (Offline / Demo)</h3>
                  <span className="text-[10px] text-indigo-600 font-bold uppercase tracking-wider">Zero-Dependency Coordinate Engine</span>
                </div>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Deterministic mathematical coordinate matching specifically calibrated for university aSc timetables. Runs locally in seconds without OCR or external API calls.
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-4 mb-6">
            <div className="w-48">
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">
                Pages to Process
              </label>
              <input
                type="number"
                min={1}
                max={60}
                value={pageLimit ?? ''}
                onChange={(e) => setPageLimit(e.target.value ? Number(e.target.value) : undefined)}
                className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-sm font-semibold text-slate-800"
              />
            </div>
            <p className="text-xs text-slate-500 mt-5">
              Leave blank to process every page, or enter a limit for a quick preview.
            </p>
          </div>

          {extractError && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-xl text-red-700 text-xs flex items-center space-x-2">
              <AlertCircle className="w-4 h-4 shrink-0 text-red-500" />
              <span>{extractError}</span>
            </div>
          )}

          <button
            type="button"
            onClick={handleExtract}
            disabled={extracting}
            className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl font-bold text-sm shadow-sm transition-all cursor-pointer flex items-center space-x-2"
          >
            {extracting ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin" />
                <span>Processing PDF Timetable...</span>
              </>
            ) : (
              <>
                <Sparkles className="w-4 h-4" />
                <span>Extract Timetable JSON</span>
              </>
            )}
          </button>
        </div>
      )}

      {/* Step 3: Interactive Preview & Editor */}
      {preview && (
        <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6 sm:p-8 space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-100">
            <div>
              <h2 className="text-lg font-bold text-navy-900 flex items-center space-x-2">
                <span className="w-6 h-6 rounded-full bg-blue-100 text-blue-700 text-xs font-black flex items-center justify-center">
                  3
                </span>
                <span>Review & Approve Timetable Data</span>
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                {preview.original_filename} • {editableEntries.length} entries • Provider:{' '}
                <span className="font-mono font-bold text-blue-600">{preview.extraction_provider || 'Pending'}</span>
              </p>
            </div>

            {/* Action Buttons */}
            <div className="flex items-center space-x-3">
              <button
                type="button"
                onClick={handleAddEntry}
                className="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold rounded-xl flex items-center space-x-1.5 transition-colors cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Row</span>
              </button>

              <button
                type="button"
                onClick={handleSaveEdits}
                disabled={savingEdits}
                className="px-3.5 py-1.5 bg-slate-800 hover:bg-slate-900 text-white text-xs font-bold rounded-xl flex items-center space-x-1.5 transition-colors cursor-pointer disabled:opacity-50"
              >
                <Save className="w-3.5 h-3.5" />
                <span>{savingEdits ? 'Saving...' : 'Save Draft'}</span>
              </button>

              <button
                type="button"
                onClick={handlePublish}
                disabled={publishing || preview.validation_errors.length > 0 || editableEntries.length === 0}
                className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-40 text-white text-xs font-extrabold rounded-xl shadow-md shadow-emerald-600/20 flex items-center space-x-1.5 transition-all cursor-pointer"
              >
                <Rocket className="w-4 h-4" />
                <span>{publishing ? 'Publishing...' : 'Approve & Publish'}</span>
              </button>
            </div>
          </div>

          {/* Validation Banner */}
          {preview.validation_errors.length > 0 ? (
            <div className="p-4 bg-red-50 border border-red-200 rounded-2xl text-red-800 text-xs space-y-1">
              <div className="font-bold flex items-center space-x-2 text-red-900">
                <AlertCircle className="w-4 h-4 text-red-600" />
                <span>Validation Errors (Must be resolved before publishing):</span>
              </div>
              <ul className="list-disc list-inside space-y-0.5 pl-1">
                {preview.validation_errors.map((err, i) => (
                  <li key={i}>{err}</li>
                ))}
              </ul>
            </div>
          ) : preview.validation_warnings.length > 0 ? (
            <div className="p-4 bg-amber-50 border border-amber-200 rounded-2xl text-amber-800 text-xs space-y-1">
              <div className="font-bold flex items-center space-x-2 text-amber-900">
                <AlertTriangle className="w-4 h-4 text-amber-600" />
                <span>Validation Warnings:</span>
              </div>
              <ul className="list-disc list-inside space-y-0.5 pl-1">
                {preview.validation_warnings.map((warn, i) => (
                  <li key={i}>{warn}</li>
                ))}
              </ul>
            </div>
          ) : (
            <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-800 text-xs font-semibold flex items-center space-x-2">
              <CheckCircle className="w-4 h-4 text-emerald-600" />
              <span>Timetable schema is valid and ready for publishing.</span>
            </div>
          )}

          {/* Editable Grid Table */}
          <div className="overflow-x-auto custom-scrollbar border border-slate-200 rounded-2xl max-h-[500px]">
            <table className="w-full text-left text-xs border-collapse">
              <thead className="sticky top-0 bg-slate-100 z-10 text-slate-500 font-bold uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="py-2.5 px-3">#</th>
                  <th className="py-2.5 px-3">Day</th>
                  <th className="py-2.5 px-3">Start</th>
                  <th className="py-2.5 px-3">End</th>
                  <th className="py-2.5 px-3">Dept</th>
                  <th className="py-2.5 px-3">Yr</th>
                  <th className="py-2.5 px-3">Sem</th>
                  <th className="py-2.5 px-3">Sec</th>
                  <th className="py-2.5 px-3 min-w-[200px]">Subject</th>
                  <th className="py-2.5 px-3">Code</th>
                  <th className="py-2.5 px-3 min-w-[150px]">Faculty</th>
                  <th className="py-2.5 px-3">Room</th>
                  <th className="py-2.5 px-3">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {editableEntries.map((entry, idx) => (
                  <tr key={idx} className="hover:bg-slate-50/80 transition-colors">
                    <td className="py-2 px-3 text-slate-400 font-mono text-[11px]">{idx + 1}</td>
                    <td className="py-2 px-3">
                      <select
                        value={entry.day}
                        onChange={(e) => handleEntryChange(idx, 'day', e.target.value)}
                        className="bg-transparent border-0 font-bold text-slate-800 focus:ring-1 focus:ring-blue-500 rounded p-1"
                      >
                        <option value="MONDAY">MONDAY</option>
                        <option value="TUESDAY">TUESDAY</option>
                        <option value="WEDNESDAY">WEDNESDAY</option>
                        <option value="THURSDAY">THURSDAY</option>
                        <option value="FRIDAY">FRIDAY</option>
                        <option value="SATURDAY">SATURDAY</option>
                      </select>
                    </td>
                    <td className="py-2 px-3 font-mono">
                      <input
                        type="text"
                        value={entry.start_time}
                        onChange={(e) => handleEntryChange(idx, 'start_time', e.target.value)}
                        className="w-16 border border-slate-200 rounded px-1.5 py-0.5 font-mono text-xs"
                      />
                    </td>
                    <td className="py-2 px-3 font-mono">
                      <input
                        type="text"
                        value={entry.end_time}
                        onChange={(e) => handleEntryChange(idx, 'end_time', e.target.value)}
                        className="w-16 border border-slate-200 rounded px-1.5 py-0.5 font-mono text-xs"
                      />
                    </td>
                    <td className="py-2 px-3">
                      <input
                        type="text"
                        value={entry.department}
                        onChange={(e) => handleEntryChange(idx, 'department', e.target.value)}
                        className="w-16 border border-slate-200 rounded px-1.5 py-0.5 uppercase font-bold"
                      />
                    </td>
                    <td className="py-2 px-3">
                      <input
                        type="number"
                        min={1}
                        max={4}
                        value={entry.year}
                        onChange={(e) => handleEntryChange(idx, 'year', Number(e.target.value))}
                        className="w-10 border border-slate-200 rounded px-1 py-0.5"
                      />
                    </td>
                    <td className="py-2 px-3">
                      <input
                        type="number"
                        min={1}
                        max={8}
                        value={entry.semester}
                        onChange={(e) => handleEntryChange(idx, 'semester', Number(e.target.value))}
                        className="w-10 border border-slate-200 rounded px-1 py-0.5"
                      />
                    </td>
                    <td className="py-2 px-3">
                      <input
                        type="text"
                        value={entry.section}
                        onChange={(e) => handleEntryChange(idx, 'section', e.target.value)}
                        className="w-12 border border-slate-200 rounded px-1.5 py-0.5 font-bold"
                      />
                    </td>
                    <td className="py-2 px-3">
                      <input
                        type="text"
                        value={entry.subject}
                        onChange={(e) => handleEntryChange(idx, 'subject', e.target.value)}
                        className="w-full border border-slate-200 rounded px-2 py-0.5 font-semibold text-slate-800"
                      />
                    </td>
                    <td className="py-2 px-3 font-mono">
                      <input
                        type="text"
                        value={entry.subject_code || ''}
                        onChange={(e) => handleEntryChange(idx, 'subject_code', e.target.value)}
                        className="w-20 border border-slate-200 rounded px-1.5 py-0.5 font-mono text-[11px]"
                      />
                    </td>
                    <td className="py-2 px-3">
                      <input
                        type="text"
                        value={entry.faculty || ''}
                        onChange={(e) => handleEntryChange(idx, 'faculty', e.target.value)}
                        className="w-full border border-slate-200 rounded px-2 py-0.5 text-slate-700"
                      />
                    </td>
                    <td className="py-2 px-3 font-mono">
                      <input
                        type="text"
                        value={entry.room || ''}
                        onChange={(e) => handleEntryChange(idx, 'room', e.target.value)}
                        className="w-24 border border-slate-200 rounded px-1.5 py-0.5 font-mono text-xs"
                      />
                    </td>
                    <td className="py-2 px-3 text-center">
                      <button
                        type="button"
                        onClick={() => handleDeleteEntry(idx)}
                        className="p-1 text-slate-400 hover:text-red-500 rounded cursor-pointer"
                        title="Remove row"
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
      )}
    </div>
  );
};
