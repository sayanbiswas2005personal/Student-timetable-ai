/**
 * Shapes returned by the backend. These mirror the DTOs in
 * `com.college.timetable.dto`; nothing is declared loosely, so a backend change that breaks the
 * contract fails the TypeScript build rather than a user's lookup.
 */

/** Outcome of a lookup. Every value has its own calm, factual presentation in the UI. */
export type LookupStatus =
  | 'CLASS_IN_PROGRESS'
  | 'NO_CLASS_NOW'
  | 'BREAK'
  | 'NO_TIMETABLE'
  | 'TIMETABLE_NOT_PUBLISHED'
  | 'STUDENT_NOT_FOUND'
  | 'STUDENT_INACTIVE'
  | 'AMBIGUOUS_SEARCH'
  | 'TIMETABLE_CONFLICT'
  | 'OTHER_IN_PROGRESS'

export type Role = 'STAFF' | 'ADMIN'

export type EntryType = 'CLASS' | 'BREAK' | 'OTHER'

export interface ApiError {
  code: string
  message: string
  details: string[]
  path: string
  timestamp: string
}

export interface CurrentUser {
  id: number
  username: string
  displayName: string | null
  role: Role
}

export interface CollegeClock {
  date: string
  time: string
  dayName: string
  timezone: string
  instant: string
}

export interface StudentSummary {
  registrationNumber: string
  sectionId: number
  fullName: string | null
  departmentName: string | null
  programName: string
  programCode: string
  semesterNumber: number
  academicYear: string
  sectionName: string
  active: boolean
}

export interface ClassInfo {
  subjectCode: string | null
  subjectName: string | null
  facultyName: string | null
  roomCode: string | null
  roomBuilding: string | null
  dayOfWeek: number
  dayName: string
  startTime: string
  endTime: string
  entryType: EntryType
  timetableId: number | null
  timetableVersion: number
}

export interface TimetableInfo {
  id: number
  version: number
  status: 'DRAFT' | 'UNDER_REVIEW' | 'PUBLISHED' | 'SUPERSEDED'
  effectiveFrom: string | null
  effectiveTo: string | null
  sourceFilename: string | null
}

export interface AmbiguityOption {
  sectionId: number
  label: string
  departmentName: string | null
  programName: string
  programCode: string
  academicYear: string
  semesterNumber: number
  sectionName: string
}

export interface LookupResponse {
  status: LookupStatus
  message: string
  evaluatedAt: string
  collegeTimezone: string | null
  collegeDate: string | null
  collegeTime: string | null
  dayName: string | null
  student: StudentSummary | null
  currentClass: ClassInfo | null
  nextClass: ClassInfo | null
  nextClassOnLaterDay: boolean
  timetable: TimetableInfo | null
  notices: string[]
  options: AmbiguityOption[]
  conflictingClasses: ClassInfo[]
}

export interface DaySchedule {
  dayOfWeek: number
  dayName: string
  periods: ClassInfo[]
}

export interface SectionTimetableResponse {
  sectionId: number
  programName: string
  academicYear: string
  semesterNumber: number
  sectionName: string
  timetable: TimetableInfo | null
  collegeTimezone: string
  today: string
  todayName: string
  currentTime: string
  days: DaySchedule[]
  status: LookupStatus
  statusMessage: string
}

export interface NextClassResponse {
  status: LookupStatus
  nextClass: ClassInfo | null
  onLaterDay: boolean
  student: StudentSummary | null
}

export interface StudentResponse {
  id: number
  registrationNumber: string
  fullName: string | null
  active: boolean
  sectionId: number
  sectionName: string
  programName: string
  programCode: string
  departmentName: string | null
  academicYear: string
  semesterNumber: number
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface DepartmentDto {
  id: number
  name: string
  code: string
  active: boolean
}

export interface ProgramDto {
  id: number
  departmentId: number
  departmentName: string
  name: string
  code: string
  degreeType: string | null
  active: boolean
}

export interface AcademicTermDto {
  id: number
  academicYear: string
  semesterNumber: number
  startDate: string
  endDate: string
  active: boolean
}

export interface SectionDto {
  id: number
  programId: number
  programName: string
  programCode: string
  academicTermId: number
  academicYear: string
  semesterNumber: number
  sectionName: string
  active: boolean
}

export interface SubjectDto {
  id: number
  subjectCode: string
  subjectName: string
  programId: number | null
  active: boolean
}

export interface FacultyDto {
  id: number
  facultyName: string
  facultyCode: string | null
  active: boolean
}

export interface RoomDto {
  id: number
  roomCode: string
  building: string | null
  floor: string | null
}

export interface TimetableEntryDto {
  id: number
  timetableId: number
  dayOfWeek: number
  dayName: string
  startTime: string
  endTime: string
  subjectId: number | null
  subjectCode: string | null
  subjectName: string | null
  facultyId: number | null
  facultyName: string | null
  roomId: number | null
  roomCode: string | null
  entryType: EntryType
  verificationStatus: string
  rawSourceText: string | null
  sourcePageNumber: number | null
}

export interface TimetableDto {
  id: number
  sectionId: number
  sectionName: string
  programName: string
  academicTermId: number
  academicYear: string
  semesterNumber: number
  effectiveFrom: string | null
  effectiveTo: string | null
  status: TimetableInfo['status']
  sourceFilename: string | null
  version: number
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  entries: TimetableEntryDto[]
}

export interface ValidationIssue {
  severity: 'ERROR' | 'WARNING'
  message: string
  entryId: number | null
}

export interface TimetableValidationReport {
  publishable: boolean
  issues: ValidationIssue[]
}

export interface ImportJobDto {
  id: number
  filename: string
  status: string
  totalPages: number | null
  pagesNeedingOcr: number | null
  ocrServiceUsed: boolean
  uploadedBy: number | null
  uploadedByUsername: string | null
  startedAt: string | null
  completedAt: string | null
  errorSummary: string | null
  resultTimetableId: number | null
  candidateCount: number
  pendingCount: number
  createdAt: string
}

export interface ImportCandidateDto {
  id: number
  importJobId: number
  pageNumber: number
  rawText: string | null
  extractedFields: string | null
  confidence: number | null
  reviewStatus: 'PENDING' | 'APPROVED' | 'EDITED' | 'REJECTED'
  reviewerNotes: string | null
  sectionLabel: string | null
  sectionId: number | null
  subjectLabel: string | null
  subjectId: number | null
  facultyLabel: string | null
  facultyId: number | null
  roomLabel: string | null
  roomId: number | null
  dayOfWeek: number | null
  startTime: string | null
  endTime: string | null
  entryType: EntryType | null
  validationErrors: string | null
}

export interface AuditLogDto {
  id: number
  username: string | null
  action: string
  entityType: string | null
  entityId: number | null
  occurredAt: string
  details: string | null
  ipAddress: string | null
}

export interface AppUserDto {
  id: number
  username: string
  displayName: string | null
  role: Role
  active: boolean
  lastLoginAt: string | null
  createdAt: string
}