import type { ClassInfo, LookupStatus } from '../types/api'

export interface StatusPresentation {
  /** Short label for the badge. */
  label: string
  /** One sentence a staff member can read at a glance. */
  description: string
  tone: 'neutral' | 'brand' | 'success' | 'warning' | 'danger'
  /** True when a class is actually running right now. */
  isInClass: boolean
}

/**
 * How each lookup status is presented.
 *
 * The wording is deliberately neutral throughout. A timetable says where a student is scheduled to
 * be, which is not the same as where they are, so nothing here uses words like "absent" or
 * "missing" no matter what the data says.
 */
const PRESENTATION: Record<LookupStatus, StatusPresentation> = {
  CLASS_IN_PROGRESS: {
    label: 'Class scheduled now',
    description: 'This section has a scheduled class at this time.',
    tone: 'success',
    isInClass: true,
  },
  NO_CLASS_NOW: {
    label: 'No class scheduled',
    description: 'The timetable has nothing scheduled at this time.',
    tone: 'neutral',
    isInClass: false,
  },
  BREAK: {
    label: 'Declared break',
    description: 'The timetable records a break at this time.',
    tone: 'brand',
    isInClass: false,
  },
  OTHER_IN_PROGRESS: {
    label: 'Scheduled activity',
    description: 'The timetable records another activity at this time.',
    tone: 'brand',
    isInClass: false,
  },
  NO_TIMETABLE: {
    label: 'No timetable available',
    description: 'No timetable has been entered for this section yet.',
    tone: 'warning',
    isInClass: false,
  },
  TIMETABLE_NOT_PUBLISHED: {
    label: 'Awaiting approval',
    description: 'A timetable exists for this section but is not published yet.',
    tone: 'warning',
    isInClass: false,
  },
  TIMETABLE_CONFLICT: {
    label: 'Timetable needs review',
    description:
      'The published timetable contains overlapping entries for this time. An administrator needs to correct it.',
    tone: 'danger',
    isInClass: false,
  },
  STUDENT_NOT_FOUND: {
    label: 'No matching student',
    description: 'No student record matches that registration number.',
    tone: 'warning',
    isInClass: false,
  },
  STUDENT_INACTIVE: {
    label: 'Record inactive',
    description: 'This student record is marked inactive in the college system.',
    tone: 'warning',
    isInClass: false,
  },
  AMBIGUOUS_SEARCH: {
    label: 'Choose a section',
    description: 'That search matches more than one section.',
    tone: 'warning',
    isInClass: false,
  },
}

const UNKNOWN_STATUS: StatusPresentation = {
  label: 'Status unavailable',
  description: 'The lookup returned a status this version of the app does not know about.',
  tone: 'neutral',
  isInClass: false,
}

export function statusPresentation(status: LookupStatus): StatusPresentation {
  return PRESENTATION[status] ?? UNKNOWN_STATUS
}

const DAY_NAMES = [
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
  'Sunday',
]

export function dayName(dayOfWeek: number): string {
  return DAY_NAMES[dayOfWeek - 1] ?? `Day ${dayOfWeek}`
}

/** "09:30" to "9:30 am" style label, used where space allows for readability. */
export function readableTime(time: string | null | undefined): string {
  if (!time) {
    return ''
  }
  const [hoursText, minutesText] = time.split(':')
  const hours = Number(hoursText)
  if (Number.isNaN(hours) || hoursText === undefined || minutesText === undefined) {
    return time
  }
  const suffix = hours >= 12 ? 'pm' : 'am'
  const display = hours % 12 === 0 ? 12 : hours % 12
  return `${display}:${minutesText} ${suffix}`
}

/** "09:30 - 10:25" */
export function timeRange(period: ClassInfo): string {
  return `${period.startTime} - ${period.endTime}`
}

/**
 * What to print as the class title. A subject name is preferred; a subject code alone is better
 * than an empty line.
 */
export function classTitle(period: ClassInfo): string {
  return period.subjectName ?? period.subjectCode ?? 'Unspecified subject'
}