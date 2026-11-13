// API Service for StudentAI.com

const API_BASE = '/api';

export interface User {
  id: number;
  name: string;
  email: string;
  role: 'ADMIN' | 'FACULTY';
  department?: string;
  is_active: boolean;
  created_at: string;
}

export interface AuthResponse {
  access_token: string;
  token_type: string;
  user: User;
}

export interface TimetableEntry {
  day: string;
  start_time: string;
  end_time: string;
  department: string;
  year: number;
  semester: number;
  section: string;
  subject_code?: string;
  subject: string;
  faculty?: string;
  room?: string;
  notes?: string;
}

export interface TimetableData {
  schema_version: string;
  institution: string;
  timezone: string;
  academic_year: string;
  department?: string;
  valid_from?: string;
  valid_until?: string;
  entries: TimetableEntry[];
}

export interface FacultySearchResult {
  current_status: string;
  status_label: string;
  status_badge_color: 'green' | 'blue' | 'yellow' | 'red' | 'gray';
  message: string;
  disclaimer: string;
  department: string;
  year: number;
  semester: number;
  section: string;
  query_date: string;
  query_day: string;
  query_time: string;
  is_simulated: boolean;
  current_class?: TimetableEntry;
  next_class?: TimetableEntry;
  override_applied?: {
    type: string;
    reason: string;
    faculty?: string;
    subject?: string;
    room?: string;
  };
  today_schedule: TimetableEntry[];
  warnings: string[];
  has_conflict: boolean;
  conflicting_entries: TimetableEntry[];
}

export interface DepartmentItem {
  id: number;
  name: string;
  code: string;
  is_active: boolean;
}

export interface AcademicStructureItem {
  id: number;
  department_code: string;
  academic_year: string;
  year: number;
  semester: number;
  section: string;
}

export interface TimetableListItem {
  id: number;
  original_filename: string;
  academic_year?: string;
  institution?: string;
  uploaded_at: string;
  processing_status: string;
  is_published: boolean;
  version: number;
  total_entries: number;
  extraction_provider?: string;
}

export interface TimetablePreview {
  id: number;
  original_filename: string;
  academic_year?: string;
  institution?: string;
  processing_status: string;
  processing_error?: string;
  extraction_provider?: string;
  is_published: boolean;
  version: number;
  total_entries: number;
  data?: TimetableData;
  validation_errors: string[];
  validation_warnings: string[];
}

export interface ScheduleOverrideItem {
  id: number;
  date: string;
  department: string;
  year: number;
  semester: number;
  section: string;
  override_type: 'CANCELLED' | 'SUBSTITUTION' | 'RESCHEDULED' | 'SPECIAL_CLASS' | 'HOLIDAY';
  start_time?: string;
  end_time?: string;
  subject?: string;
  faculty?: string;
  room?: string;
  reason: string;
  created_at: string;
}

export interface AuditLogItem {
  id: number;
  user_id?: number;
  user_email?: string;
  action: string;
  details?: string;
  timestamp: string;
}

export interface SettingsData {
  gemini_api_key_configured: boolean;
  gemini_api_key_masked: string;
  gemini_model: string;
  timezone: string;
  database_url: string;
  storage_dir: string;
  max_upload_size_mb: number;
}

// Request Helper
async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('studentai_token');
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  // Set json content type unless FormData
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    localStorage.removeItem('studentai_token');
    localStorage.removeItem('studentai_user');
    if (!window.location.pathname.includes('/login')) {
      window.location.href = '/login';
    }
  }

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const errorMsg = data?.detail || data?.message || `HTTP Error ${response.status}`;
    throw new Error(typeof errorMsg === 'string' ? errorMsg : JSON.stringify(errorMsg));
  }

  return data as T;
}

// Auth API
export const api = {
  auth: {
    login: (credentials: { email: string; password: string }) =>
      request<AuthResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify(credentials),
      }),
    logout: () => request<{ message: string }>('/auth/logout', { method: 'POST' }),
    me: () => request<User>('/auth/me'),
  },

  search: {
    getCurrentClass: (params: {
      department: string;
      year: number;
      semester: number;
      section: string;
      simulated_date?: string;
      simulated_time?: string;
    }) => {
      const q = new URLSearchParams({
        department: params.department,
        year: params.year.toString(),
        semester: params.semester.toString(),
        section: params.section,
        ...(params.simulated_date ? { simulated_date: params.simulated_date } : {}),
        ...(params.simulated_time ? { simulated_time: params.simulated_time } : {}),
      });
      return request<FacultySearchResult>(`/search/current-class?${q.toString()}`);
    },
    getServerTime: () =>
      request<{
        timezone: string;
        iso_timestamp: string;
        date: string;
        time: string;
        day: string;
        formatted: string;
      }>('/search/server-time'),
    getDepartments: () => request<DepartmentItem[]>('/search/departments'),
    getAcademicStructures: (dept?: string) =>
      request<AcademicStructureItem[]>(`/search/academic-structures${dept ? `?department=${dept}` : ''}`),
  },

  admin: {
    getTimetables: () => request<TimetableListItem[]>('/admin/timetables'),
    uploadPdf: (formData: FormData) =>
      request<{ id: number; original_filename: string; processing_status: string; message: string }>(
        '/admin/timetables/upload',
        {
          method: 'POST',
          body: formData,
        }
      ),
    extractTimetable: (id: number, provider?: string, pageLimit?: number) => {
      const q = new URLSearchParams();
      if (provider) q.append('provider', provider);
      if (pageLimit) q.append('page_limit', pageLimit.toString());
      return request<{
        id: number;
        processing_status: string;
        extraction_provider: string;
        total_entries: number;
        validation_errors: string[];
        validation_warnings: string[];
        message: string;
      }>(`/admin/timetables/${id}/extract?${q.toString()}`, { method: 'POST' });
    },
    getPreview: (id: number) => request<TimetablePreview>(`/admin/timetables/${id}/preview`),
    updateTimetable: (id: number, data: { academic_year?: string; valid_from?: string; valid_until?: string; entries: TimetableEntry[] }) =>
      request<{ message: string; total_entries: number; validation_warnings: string[] }>(`/admin/timetables/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    publishTimetable: (id: number) =>
      request<{ message: string; timetable_id: number; version: number; is_published: boolean; total_entries: number }>(
        `/admin/timetables/${id}/publish`,
        { method: 'POST' }
      ),
    deactivateTimetable: (id: number) =>
      request<{ message: string; is_published: boolean }>(`/admin/timetables/${id}/deactivate`, { method: 'POST' }),
    deleteTimetable: (id: number) =>
      request<{ message: string }>(`/admin/timetables/${id}`, { method: 'DELETE' }),

    // Departments
    getDepartments: () => request<DepartmentItem[]>('/admin/departments'),
    createDepartment: (dept: { name: string; code: string }) =>
      request<DepartmentItem>('/admin/departments', { method: 'POST', body: JSON.stringify(dept) }),
    deleteDepartment: (id: number) =>
      request<{ message: string }>(`/admin/departments/${id}`, { method: 'DELETE' }),

    // Academic Structures
    getStructures: () => request<AcademicStructureItem[]>('/admin/academic-structures'),
    createStructure: (struct: { department_code: string; academic_year: string; year: number; semester: number; section: string }) =>
      request<AcademicStructureItem>('/admin/academic-structures', { method: 'POST', body: JSON.stringify(struct) }),
    deleteStructure: (id: number) =>
      request<{ message: string }>(`/admin/academic-structures/${id}`, { method: 'DELETE' }),

    // Users
    getUsers: () => request<User[]>('/admin/users'),
    createUser: (user: Partial<User> & { password: string }) =>
      request<User>('/admin/users', { method: 'POST', body: JSON.stringify(user) }),
    updateUser: (id: number, user: Partial<User> & { password?: string }) =>
      request<User>(`/admin/users/${id}`, { method: 'PUT', body: JSON.stringify(user) }),
    deleteUser: (id: number) =>
      request<{ message: string }>(`/admin/users/${id}`, { method: 'DELETE' }),

    // Overrides
    getOverrides: () => request<ScheduleOverrideItem[]>('/admin/overrides'),
    createOverride: (override: Omit<ScheduleOverrideItem, 'id' | 'created_at'>) =>
      request<ScheduleOverrideItem>('/admin/overrides', { method: 'POST', body: JSON.stringify(override) }),
    deleteOverride: (id: number) =>
      request<{ message: string }>(`/admin/overrides/${id}`, { method: 'DELETE' }),

    // Audit logs
    getAuditLogs: () => request<AuditLogItem[]>('/admin/audit-logs'),

    // Settings & API Configuration
    getSettings: () => request<SettingsData>('/admin/settings'),
    updateSettings: (settings: { gemini_api_key?: string; gemini_model?: string; timezone?: string }) =>
      request<{
        message: string;
        gemini_api_key_configured: boolean;
        gemini_api_key_masked: string;
        gemini_model: string;
        timezone: string;
      }>('/admin/settings', { method: 'POST', body: JSON.stringify(settings) }),
    testGeminiConnection: (payload?: { api_key?: string; model?: string }) =>
      request<{
        success: boolean;
        message: string;
        model_tested: string;
        response: string;
      }>('/admin/settings/test-gemini', {
        method: 'POST',
        body: JSON.stringify(payload || {}),
      }),
  },
};
