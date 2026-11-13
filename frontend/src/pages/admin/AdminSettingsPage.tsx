import React, { useState, useEffect } from 'react';
import { api, SettingsData } from '../../services/api';
import {
  Settings,
  Key,
  Sparkles,
  CheckCircle2,
  AlertCircle,
  Clock,
  Database,
  HardDrive,
  Eye,
  EyeOff,
  Send,
  RefreshCw,
  Terminal,
  ShieldCheck
} from 'lucide-react';

export const AdminSettingsPage: React.FC = () => {
  const [settings, setSettings] = useState<SettingsData | null>(null);
  const [loading, setLoading] = useState(true);

  // Form State
  const [apiKey, setApiKey] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [model, setModel] = useState('gemini-3.8-flash');
  const [timezone, setTimezone] = useState('Asia/Kolkata');

  // Action status
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<{
    success: boolean;
    message: string;
    model_tested?: string;
    response?: string;
  } | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const loadSettings = async () => {
    try {
      const data = await api.admin.getSettings();
      setSettings(data);
      setModel(data.gemini_model || 'gemini-3.8-flash');
      setTimezone(data.timezone || 'Asia/Kolkata');
    } catch (err: any) {
      console.error('Failed to load settings', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSettings();
  }, []);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSuccessMsg(null);
    setErrorMsg(null);
    setTestResult(null);

    try {
      const res = await api.admin.updateSettings({
        gemini_api_key: apiKey ? apiKey.trim() : undefined,
        gemini_model: model,
        timezone: timezone,
      });
      setSuccessMsg(res.message);
      setApiKey(''); // clear plain input after save
      await loadSettings();
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to save settings.');
    } finally {
      setSaving(false);
    }
  };

  const handleTestConnection = async () => {
    setTesting(true);
    setTestResult(null);
    setErrorMsg(null);
    setSuccessMsg(null);

    try {
      const res = await api.admin.testGeminiConnection({
        api_key: apiKey ? apiKey.trim() : undefined,
        model: model,
      });
      setTestResult(res);
    } catch (err: any) {
      setTestResult({
        success: false,
        message: err.message || 'Connection test failed. Check API key validity or network access.',
      });
    } finally {
      setTesting(false);
    }
  };

  if (loading) {
    return (
      <div className="py-16 flex justify-center items-center text-slate-400">
        <RefreshCw className="w-6 h-6 animate-spin mr-2" />
        <span>Loading Institutional Settings...</span>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* Title */}
      <div>
        <h1 className="text-2xl font-extrabold text-navy-900 tracking-tight">System Settings & API Configuration</h1>
        <p className="mt-1 text-sm text-slate-600">
          Manage Google Gemini API credentials for PDF timetable extraction, adjust model parameters, and configure institutional timezone.
        </p>
      </div>

      {successMsg && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm rounded-2xl flex items-center space-x-3">
          <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" />
          <span className="font-semibold">{successMsg}</span>
        </div>
      )}

      {errorMsg && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 text-sm rounded-2xl flex items-center space-x-3">
          <AlertCircle className="w-5 h-5 text-red-500 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Main Settings Form */}
      <form onSubmit={handleSave} className="space-y-6">
        {/* Card 1: Google Gemini API Configuration */}
        <div className="bg-white rounded-3xl border border-slate-200/80 shadow-sm p-6 sm:p-8 space-y-6">
          <div className="flex items-center justify-between pb-4 border-b border-slate-100">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center font-bold">
                <Sparkles className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-base font-bold text-navy-900">Google Gemini API Credentials</h2>
                <p className="text-xs text-slate-500">
                  Required for automated multi-page PDF timetable understanding and structured table extraction
                </p>
              </div>
            </div>

            {/* Status Pill */}
            {settings?.gemini_api_key_configured ? (
              <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
                <ShieldCheck className="w-3.5 h-3.5 mr-1 text-emerald-600" />
                API Key Active ({settings.gemini_api_key_masked})
              </span>
            ) : (
              <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-800 border border-amber-200">
                <AlertCircle className="w-3.5 h-3.5 mr-1 text-amber-600" />
                Not Configured (Demo Mode Active)
              </span>
            )}
          </div>

          <div className="space-y-4">
            {/* API Key Input */}
            <div>
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1.5 flex items-center justify-between">
                <span>Google Gemini API Key</span>
                {settings?.gemini_api_key_configured && (
                  <span className="text-[11px] font-normal text-slate-400">
                    Current: <code className="font-mono text-slate-600">{settings.gemini_api_key_masked}</code>
                  </span>
                )}
              </label>
              <div className="relative rounded-xl shadow-xs">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                  <Key className="w-4 h-4" />
                </div>
                <input
                  type={showKey ? 'text' : 'password'}
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder={settings?.gemini_api_key_configured ? 'Enter new key to update...' : 'AIzaSy...'}
                  className="w-full pl-10 pr-10 py-2.5 bg-slate-50/80 border border-slate-300 rounded-xl text-sm font-mono text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowKey(!showKey)}
                  className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 hover:text-slate-600 cursor-pointer"
                >
                  {showKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              <p className="mt-1.5 text-xs text-slate-500">
                Get an API key from Google AI Studio at{' '}
                <a
                  href="https://aistudio.google.com/apikey"
                  target="_blank"
                  rel="noreferrer"
                  className="text-blue-600 underline hover:text-blue-700 font-medium"
                >
                  aistudio.google.com/apikey
                </a>
                . The key is securely stored in your institutional environment and never sent to clients.
              </p>
            </div>

            {/* Model Name Selector */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1.5">
                  Gemini Model
                </label>
                <select
                  value={model}
                  onChange={(e) => setModel(e.target.value)}
                  className="w-full bg-slate-50/80 border border-slate-300 rounded-xl px-3.5 py-2.5 text-sm font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="gemini-3.8-flash">gemini-3.8-flash (Recommended: Ultra-fast multimodal 1M tokens)</option>
                  <option value="gemini-4-argon">gemini-4-argon (Maximum reasoning & extraction precision)</option>
                  <option value="gemini-3.5-flash-lite">gemini-3.5-flash-lite (Cost-efficient high-throughput)</option>
                </select>
              </div>

              {/* Campus Timezone */}
              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1.5">
                  Institutional Campus Timezone
                </label>
                <input
                  type="text"
                  value={timezone}
                  onChange={(e) => setTimezone(e.target.value)}
                  placeholder="Asia/Kolkata"
                  className="w-full bg-slate-50/80 border border-slate-300 rounded-xl px-3.5 py-2.5 text-sm font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono"
                />
              </div>
            </div>
          </div>

          {/* Test Connection Result Box */}
          {testResult && (
            <div
              className={`p-4 rounded-2xl border text-xs ${
                testResult.success
                  ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
                  : 'bg-red-50 border-red-200 text-red-900'
              }`}
            >
              <div className="font-bold flex items-center space-x-2">
                {testResult.success ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                ) : (
                  <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
                )}
                <span>{testResult.message}</span>
              </div>
              {testResult.response && (
                <div className="mt-2 p-2 bg-white/80 rounded-lg font-mono text-[11px] text-slate-700 border border-emerald-100">
                  Model Output: "{testResult.response.trim()}"
                </div>
              )}
            </div>
          )}

          {/* Action buttons */}
          <div className="flex flex-wrap items-center gap-3 pt-2">
            <button
              type="submit"
              disabled={saving}
              className="px-5 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl text-xs font-bold shadow-sm transition-all cursor-pointer flex items-center space-x-2"
            >
              {saving ? (
                <>
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  <span>Saving...</span>
                </>
              ) : (
                <>
                  <ShieldCheck className="w-3.5 h-3.5" />
                  <span>Save Configuration</span>
                </>
              )}
            </button>

            <button
              type="button"
              onClick={handleTestConnection}
              disabled={testing || (!apiKey && !settings?.gemini_api_key_configured)}
              className="px-4 py-2.5 bg-slate-100 hover:bg-slate-200 disabled:opacity-40 text-slate-700 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center space-x-2"
            >
              {testing ? (
                <>
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  <span>Testing Connectivity...</span>
                </>
              ) : (
                <>
                  <Send className="w-3.5 h-3.5 text-blue-600" />
                  <span>Test Gemini API Connection</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Card 2: Environment File Reference */}
        <div className="bg-slate-900 rounded-3xl p-6 sm:p-8 text-white space-y-4 shadow-md">
          <div className="flex items-center space-x-3">
            <Terminal className="w-5 h-5 text-blue-400" />
            <h3 className="text-sm font-bold tracking-tight">Direct Environment File (`.env`) Reference</h3>
          </div>
          <p className="text-xs text-slate-300 leading-relaxed">
            API keys and environment variables can also be set directly in the root{' '}
            <code className="text-blue-300 font-mono">.env</code> file. When you save settings from this dashboard,
            they are automatically synchronized to both active memory and the <code className="text-blue-300 font-mono">.env</code> file.
          </p>

          <div className="p-3.5 bg-slate-950/80 rounded-xl font-mono text-xs text-slate-300 border border-slate-800 space-y-1">
            <div className="text-slate-500"># Located at Student-timetable-ai/.env</div>
            <div><span className="text-blue-400">GEMINI_API_KEY</span>=<span className="text-emerald-400">{settings?.gemini_api_key_masked || 'your_google_gemini_api_key_here'}</span></div>
            <div><span className="text-blue-400">GEMINI_MODEL</span>=<span className="text-emerald-400">{model}</span></div>
            <div><span className="text-blue-400">TIMEZONE</span>=<span className="text-emerald-400">{timezone}</span></div>
            <div><span className="text-blue-400">DATABASE_URL</span>=<span className="text-emerald-400">sqlite:///./studentai.db</span></div>
          </div>
        </div>
      </form>
    </div>
  );
};

