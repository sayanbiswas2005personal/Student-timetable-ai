import { api, primeCsrfCookie, toApiError } from './client'

import type {
  AcademicTermDto,
  AppUserDto,
  AuditLogDto,
  CollegeClock,
  CurrentUser,
  DepartmentDto,
  FacultyDto,
  ImportCandidateDto,
  ImportJobDto,
  LookupResponse,
  NextClassResponse,
  PageResponse,
  ProgramDto,
  RoomDto,
  SectionDto,
  SectionTimetableResponse,
  StudentResponse,
  SubjectDto,
  TimetableDto,
  TimetableValidationReport,
} from '../types/api'

/** Unwraps the response or throws an {@link ApiRequestError} the UI can render directly. */
async function unwrap<T>(promise: Promise<{ data: T }>): Promise<T> {
  try {
    const response = await promise
    return response.data
  } catch (error) {
    throw toApiError(error)
  }
}

export const authApi = {
  me: () => unwrap<CurrentUser>(api.get('/auth/me')),

  login: async (username: string, password: string) => {
    // The first write of a session needs a CSRF cookie to echo back, so fetch one first.
    await primeCsrfCookie()
    return unwrap<{ user: CurrentUser; message: string }>(
      api.post('/auth/login', { username, password }),
    )
  },

  logout: async () => {
    await primeCsrfCookie()
    return unwrap<void>(api.post('/auth/logout'))
  },
}

export const lookupApi = {
  clock: () => unwrap<CollegeClock>(api.get('/lookup/clock')),

  /**
   * Registration numbers contain slashes, so they travel as a query parameter rather than as a
   * path segment.
   */
  byRegistrationNumber: (registrationNumber: string, at?: string) =>
    unwrap<LookupResponse>(
      api.get('/lookup/student', { params: { reg: registrationNumber, at } }),
    ),

  section: (params: {
    sectionId?: number
    programId?: number
    semester?: number
    section?: string
    academicYear?: string
    q?: string
    at?: string
  }) => unwrap<LookupResponse>(api.get('/lookup/section', { params })),

  sectionWeek: (sectionId: number, at?: string) =>
    unwrap<SectionTimetableResponse>(
      api.get(`/lookup/section/${sectionId}/week`, { params: { at } }),
    ),

  nextClass: (params: { reg?: string; sectionId?: number; at?: string }) =>
    unwrap<NextClassResponse>(api.get('/lookup/next-class', { params })),
}

export const catalogApi = {
  departments: () => unwrap<DepartmentDto[]>(api.get('/departments')),
  programs: (departmentId?: number) =>
    unwrap<ProgramDto[]>(api.get('/programs', { params: { departmentId } })),
  academicTerms: () => unwrap<AcademicTermDto[]>(api.get('/academic-terms')),
  sections: (params: { programId?: number; academicTermId?: number } = {}) =>
    unwrap<SectionDto[]>(api.get('/sections', { params })),
  subjects: () => unwrap<SubjectDto[]>(api.get('/subjects')),
  faculties: () => unwrap<FacultyDto[]>(api.get('/faculties')),
  rooms: () => unwrap<RoomDto[]>(api.get('/rooms')),
}

export const studentApi = {
  search: (q: string, page = 0, size = 25) =>
    unwrap<PageResponse<StudentResponse>>(api.get('/students/search', { params: { q, page, size } })),

  byRegistrationNumber: (registrationNumber: string) =>
    unwrap<StudentResponse>(
      api.get('/students/by-registration', { params: { reg: registrationNumber } }),
    ),

  bySection: (sectionId: number) =>
    unwrap<StudentResponse[]>(api.get(`/students/section/${sectionId}`)),

  create: (body: {
    registrationNumber: string
    fullName?: string
    sectionId: number
    active?: boolean
  }) => unwrap<StudentResponse>(api.post('/students', body)),

  update: (
    id: number,
    body: { registrationNumber: string; fullName?: string; sectionId: number; active: boolean },
  ) => unwrap<StudentResponse>(api.put(`/students/${id}`, body)),

  changeStatus: (id: number, active: boolean) =>
    unwrap<StudentResponse>(api.patch(`/students/${id}/status`, { active })),
}

export const timetableApi = {
  list: (sectionId?: number) =>
    unwrap<TimetableDto[]>(api.get('/timetables', { params: { sectionId } })),
  get: (id: number) => unwrap<TimetableDto>(api.get(`/timetables/${id}`)),
  validate: (id: number) =>
    unwrap<TimetableValidationReport>(api.get(`/timetables/${id}/validate`)),

  create: (body: {
    sectionId: number
    effectiveFrom: string
    effectiveTo?: string | null
    sourceFilename?: string | null
  }) => unwrap<TimetableDto>(api.post('/timetables', body)),

  upsertEntry: (
    timetableId: number,
    entryId: number | null,
    body: {
      dayOfWeek: number
      startTime: string
      endTime: string
      subjectId?: number | null
      facultyId?: number | null
      roomId?: number | null
      entryType?: string
      rawSourceText?: string | null
    },
  ) =>
    entryId === null
      ? unwrap(api.post(`/timetables/${timetableId}/entries`, body))
      : unwrap(api.put(`/timetables/${timetableId}/entries/${entryId}`, body)),

  deleteEntry: (timetableId: number, entryId: number) =>
    unwrap<void>(api.delete(`/timetables/${timetableId}/entries/${entryId}`)),

  publish: (id: number, body?: { effectiveFrom?: string; effectiveTo?: string | null }) =>
    unwrap<TimetableDto>(api.post(`/timetables/${id}/publish`, body ?? {})),

  rollback: (id: number, body?: { effectiveFrom?: string; effectiveTo?: string | null }) =>
    unwrap<TimetableDto>(api.post(`/timetables/${id}/rollback`, body ?? {})),
}

export const importApi = {
  list: () => unwrap<ImportJobDto[]>(api.get('/imports')),
  get: (id: number) => unwrap<ImportJobDto>(api.get(`/imports/${id}`)),
  candidates: (id: number) =>
    unwrap<ImportCandidateDto[]>(api.get(`/imports/${id}/candidates`)),

  upload: (file: File, onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    return unwrap<ImportJobDto>(
      api.post('/imports', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (event) => {
          if (onProgress && event.total) {
            onProgress(Math.round((event.loaded / event.total) * 100))
          }
        },
      }),
    )
  },

  updateCandidate: (
    jobId: number,
    candidateId: number,
    body: Record<string, unknown>,
  ) => unwrap<ImportCandidateDto>(api.put(`/imports/${jobId}/candidates/${candidateId}`, body)),

  deleteCandidate: (jobId: number, candidateId: number) =>
    unwrap<void>(api.delete(`/imports/${jobId}/candidates/${candidateId}`)),

  approve: (jobId: number, body: { sectionId: number; effectiveFrom: string; effectiveTo?: string | null }) =>
    unwrap<{ timetableId: number; kind: string; message: string }>(
      api.post(`/imports/${jobId}/approve`, body),
    ),

  publish: (jobId: number, timetableId: number) =>
    unwrap<TimetableDto>(api.post(`/imports/${jobId}/publish`, null, { params: { timetableId } })),
}

export const adminApi = {
  users: () => unwrap<AppUserDto[]>(api.get('/admin/users')),
  auditLogs: (limit = 100) => unwrap<AuditLogDto[]>(api.get('/admin/audit-logs', { params: { limit } })),
  createUser: (body: {
    username: string
    password: string
    displayName?: string
    role: 'STAFF' | 'ADMIN'
  }) => unwrap<AppUserDto>(api.post('/admin/users', body)),
  setUserStatus: (id: number, active: boolean) =>
    unwrap<AppUserDto>(api.patch(`/admin/users/${id}/status`, { active })),
}