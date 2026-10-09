import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DashboardPage from './DashboardPage'
import { ApiRequestError } from '../api/client'
import type { LookupResponse } from '../types/api'

/**
 * The API module is replaced wholesale, so no test in this file touches a network. `vi.hoisted`
 * creates the mocks before the factory below runs, which is what the hoisted `vi.mock` needs.
 */
const mocks = vi.hoisted(() => ({
  clock: vi.fn(),
  byRegistrationNumber: vi.fn(),
  section: vi.fn(),
}))

vi.mock('../api/endpoints', () => ({
  lookupApi: mocks,
}))

function baseResponse(overrides: Partial<LookupResponse> = {}): LookupResponse {
  return {
    status: 'CLASS_IN_PROGRESS',
    message: 'Scheduled: Cloud Computing from 09:30 to 10:25.',
    evaluatedAt: '2026-10-12T04:15:00Z',
    collegeTimezone: 'Asia/Kolkata',
    collegeDate: '2026-10-12',
    collegeTime: '09:45',
    dayName: 'MONDAY',
    student: {
      registrationNumber: 'UG/02/BTCSEAIML/2023/024',
      sectionId: 7,
      fullName: null,
      departmentName: 'Computer Science and Engineering',
      programName: 'B.Tech CSE AI-ML',
      programCode: 'BTCSEAIML',
      semesterNumber: 5,
      academicYear: '2025-26',
      sectionName: 'D',
      active: true,
    },
    currentClass: {
      subjectCode: 'CSE11036',
      subjectName: 'Cloud Computing',
      facultyName: 'Prof. A Sen',
      roomCode: 'AU6-4304',
      roomBuilding: 'Academic Block 6',
      dayOfWeek: 1,
      dayName: 'MONDAY',
      startTime: '09:30',
      endTime: '10:25',
      entryType: 'CLASS',
      timetableId: 1,
      timetableVersion: 1,
    },
    nextClass: null,
    nextClassOnLaterDay: false,
    timetable: {
      id: 1,
      version: 1,
      status: 'PUBLISHED',
      effectiveFrom: '2026-08-01',
      effectiveTo: null,
      sourceFilename: 'semester-5.pdf',
    },
    notices: [],
    options: [],
    conflictingClasses: [],
    ...overrides,
  }
}

function renderDashboard() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    mocks.clock.mockResolvedValue({
      date: '2026-10-12',
      time: '09:45',
      dayName: 'MONDAY',
      timezone: 'Asia/Kolkata',
      instant: '2026-10-12T04:15:00Z',
    })
  })

  it('shows the college clock, not the browser clock', async () => {
    mocks.byRegistrationNumber.mockResolvedValue(baseResponse())
    renderDashboard()

    expect(await screen.findByText(/college time: monday, 2026-10-12 09:45/i)).toBeInTheDocument()
  })

  it('reports a class in progress with subject, faculty, room and time', async () => {
    const user = userEvent.setup()
    mocks.byRegistrationNumber.mockResolvedValue(baseResponse())
    renderDashboard()

    await user.type(screen.getByLabelText(/registration number/i), 'UG/02/BTCSEAIML/2023/024')
    await user.click(screen.getByRole('button', { name: /find student/i }))

    expect(await screen.findByText('Scheduled right now')).toBeInTheDocument()
    expect(screen.getAllByText('Cloud Computing').length).toBeGreaterThan(0)
    expect(screen.getByText('Prof. A Sen')).toBeInTheDocument()
    expect(screen.getByText('AU6-4304')).toBeInTheDocument()
    expect(screen.getByText('09:30 - 10:25')).toBeInTheDocument()
    expect(screen.getByText('Class scheduled now')).toBeInTheDocument()
  })

  it('shows the verified section, never one guessed from the registration number', async () => {
    const user = userEvent.setup()
    mocks.byRegistrationNumber.mockResolvedValue(baseResponse())
    renderDashboard()

    await user.type(screen.getByLabelText(/registration number/i), 'UG/02/BTCSEAIML/2023/024')
    await user.click(screen.getByRole('button', { name: /find student/i }))

    expect(await screen.findByText('B.Tech CSE AI-ML')).toBeInTheDocument()
    expect(screen.getByText('Semester 5 (2025-26)')).toBeInTheDocument()
    expect(screen.getByText('D')).toBeInTheDocument()
  })

  it('says plainly when no class is scheduled, without implying wrongdoing', async () => {
    const user = userEvent.setup()
    mocks.byRegistrationNumber.mockResolvedValue(
      baseResponse({
        status: 'NO_CLASS_NOW',
        message: 'No class is scheduled for this section at 12:00.',
        currentClass: null,
      }),
    )
    renderDashboard()

    await user.type(screen.getByLabelText(/registration number/i), 'UG/02/BTCSEAIML/2023/024')
    await user.click(screen.getByRole('button', { name: /find student/i }))

    expect(await screen.findByText('No class scheduled')).toBeInTheDocument()
    expect(screen.getByText(/no class is scheduled for this section/i)).toBeInTheDocument()
    expect(screen.queryByText('Scheduled right now')).not.toBeInTheDocument()
  })

  it('reports a registration number that matches nothing', async () => {
    const user = userEvent.setup()
    mocks.byRegistrationNumber.mockResolvedValue(
      baseResponse({
        status: 'STUDENT_NOT_FOUND',
        message: 'No student record matches that registration number.',
        student: null,
        currentClass: null,
        timetable: null,
      }),
    )
    renderDashboard()

    await user.type(screen.getByLabelText(/registration number/i), 'UG/02/NOPE/2023/999')
    await user.click(screen.getByRole('button', { name: /find student/i }))

    expect(await screen.findByText('No matching student')).toBeInTheDocument()
    expect(screen.queryByText('Scheduled right now')).not.toBeInTheDocument()
  })

  it('asks the user to choose when a search is ambiguous instead of guessing', async () => {
    const user = userEvent.setup()
    mocks.section.mockResolvedValue(
      baseResponse({
        status: 'AMBIGUOUS_SEARCH',
        message: 'That search matches 2 sections. Please choose one.',
        student: null,
        currentClass: null,
        timetable: null,
        options: [
          {
            sectionId: 1,
            label: 'B.Tech CSE AI-ML section D',
            departmentName: 'CSE',
            programName: 'B.Tech CSE AI-ML',
            programCode: 'BTCSEAIML',
            academicYear: '2025-26',
            semesterNumber: 5,
            sectionName: 'D',
          },
          {
            sectionId: 2,
            label: 'B.Tech CSE section D',
            departmentName: 'CSE',
            programName: 'B.Tech CSE',
            programCode: 'BTCSECSE',
            academicYear: '2025-26',
            semesterNumber: 5,
            sectionName: 'D',
          },
        ],
      }),
    )
    renderDashboard()

    await user.type(
      screen.getByLabelText(/course, semester and section/i),
      'B.Tech CSE, 5th semester, section D',
    )
    await user.click(screen.getByRole('button', { name: /find section/i }))

    expect(await screen.findByText(/choose a section/i)).toBeInTheDocument()
    expect(screen.getByText('B.Tech CSE AI-ML')).toBeInTheDocument()
    expect(screen.getByText('B.Tech CSE')).toBeInTheDocument()
  })

  it('shows an understandable message when the request fails', async () => {
    const user = userEvent.setup()
    mocks.byRegistrationNumber.mockRejectedValue(
      new ApiRequestError(0, null, 'Could not reach the server. Check your connection and try again.'),
    )
    renderDashboard()

    await user.type(screen.getByLabelText(/registration number/i), 'UG/02/BTCSEAIML/2023/024')
    await user.click(screen.getByRole('button', { name: /find student/i }))

    expect(await screen.findByText(/could not reach the server/i)).toBeInTheDocument()
  })

  it('shows a loading indicator while the lookup is in flight', async () => {
    const user = userEvent.setup()
    let release: (value: LookupResponse) => void = () => {}
    mocks.byRegistrationNumber.mockReturnValue(
      new Promise<LookupResponse>((resolve) => {
        release = resolve
      }),
    )
    renderDashboard()

    await user.type(screen.getByLabelText(/registration number/i), 'UG/02/BTCSEAIML/2023/024')
    await user.click(screen.getByRole('button', { name: /find student/i }))

    expect(await screen.findByText(/checking the timetable/i)).toBeInTheDocument()
    release(baseResponse())
    await waitFor(() => expect(screen.queryByText(/checking the timetable/i)).not.toBeInTheDocument())
  })

  it('records a successful lookup in this browser session only', async () => {
    const user = userEvent.setup()
    mocks.byRegistrationNumber.mockResolvedValue(baseResponse())
    renderDashboard()

    await user.type(screen.getByLabelText(/registration number/i), 'UG/02/BTCSEAIML/2023/024')
    await user.click(screen.getByRole('button', { name: /find student/i }))

    expect(await screen.findByText(/recent lookups/i)).toBeInTheDocument()
    const stored = window.sessionStorage.getItem('timetable.recent-lookups')
    expect(stored).toContain('UG/02/BTCSEAIML/2023/024')
  })
})