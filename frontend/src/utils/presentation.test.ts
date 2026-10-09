import { classTitle, dayName, readableTime, statusPresentation } from './presentation'

describe('statusPresentation', () => {
  it('treats a running class as a class, not as an incident', () => {
    const presentation = statusPresentation('CLASS_IN_PROGRESS')

    expect(presentation.isInClass).toBe(true)
    expect(presentation.tone).toBe('success')
    expect(presentation.label).toBe('Class scheduled now')
  })

  it('describes a free period neutrally', () => {
    const presentation = statusPresentation('NO_CLASS_NOW')

    expect(presentation.isInClass).toBe(false)
    expect(presentation.description.toLowerCase()).not.toContain('absent')
    expect(presentation.description.toLowerCase()).not.toContain('missing')
  })

  it('never uses accusatory wording for any status', () => {
    const statuses = [
      'CLASS_IN_PROGRESS',
      'NO_CLASS_NOW',
      'BREAK',
      'NO_TIMETABLE',
      'TIMETABLE_NOT_PUBLISHED',
      'STUDENT_NOT_FOUND',
      'STUDENT_INACTIVE',
      'AMBIGUOUS_SEARCH',
      'TIMETABLE_CONFLICT',
      'OTHER_IN_PROGRESS',
    ] as const

    for (const status of statuses) {
      const presentation = statusPresentation(status)
      const words = `${presentation.label} ${presentation.description}`.toLowerCase()
      expect(words).not.toContain('bunk')
      expect(words).not.toContain('absent')
      expect(words).not.toContain('violat')
      expect(words).not.toContain('penal')
    }
  })

  it('falls back safely for a status this build does not know', () => {
    const presentation = statusPresentation('SOMETHING_NEW' as never)

    expect(presentation.isInClass).toBe(false)
    expect(presentation.label).toBe('Status unavailable')
  })
})

describe('dayName', () => {
  it('maps ISO weekday numbers to names', () => {
    expect(dayName(1)).toBe('Monday')
    expect(dayName(5)).toBe('Friday')
    expect(dayName(7)).toBe('Sunday')
  })

  it('degrades gracefully for an unexpected value', () => {
    expect(dayName(0)).toBe('Day 0')
  })
})

describe('readableTime', () => {
  it('renders a 24 hour time in 12 hour form', () => {
    expect(readableTime('09:30')).toBe('9:30 am')
    expect(readableTime('13:05')).toBe('1:05 pm')
    expect(readableTime('00:00')).toBe('12:00 am')
    expect(readableTime('12:00')).toBe('12:00 pm')
  })

  it('returns an empty string for a missing time', () => {
    expect(readableTime(null)).toBe('')
  })
})

describe('classTitle', () => {
  it('prefers the subject name, then the code, and never an empty line', () => {
    const base = {
      subjectCode: 'CSE11036',
      subjectName: 'Cloud Computing',
      facultyName: null,
      roomCode: null,
      roomBuilding: null,
      dayOfWeek: 1,
      dayName: 'MONDAY',
      startTime: '09:30',
      endTime: '10:25',
      entryType: 'CLASS' as const,
      timetableId: 1,
      timetableVersion: 1,
    }

    expect(classTitle(base)).toBe('Cloud Computing')
    expect(classTitle({ ...base, subjectName: null })).toBe('CSE11036')
    expect(classTitle({ ...base, subjectName: null, subjectCode: null })).toBe('Unspecified subject')
  })
})