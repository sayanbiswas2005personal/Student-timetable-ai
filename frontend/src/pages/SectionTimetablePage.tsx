import { useParams } from 'react-router-dom'

import { useAsync } from '../hooks/useAsync'
import { lookupApi } from '../api/endpoints'
import { Alert } from '../components/common/Alert'
import { EmptyState } from '../components/common/EmptyState'
import { Spinner } from '../components/common/Spinner'
import { classTitle, dayName, timeRange } from '../utils/presentation'
import type { ClassInfo } from '../types/api'

/**
 * The whole week for one section.
 *
 * The current time indicator is only drawn when the published timetable actually covers today,
 * so the highlight can never suggest a class on a day the timetable does not cover.
 */
export default function SectionTimetablePage() {
  const { sectionId } = useParams<{ sectionId: string }>()
  const numericId = Number(sectionId)

  const { data, error, loading } = useAsync(
    () => lookupApi.sectionWeek(numericId),
    [numericId],
    { enabled: Number.isFinite(numericId) },
  )

  if (loading) {
    return <Spinner label="Loading the weekly timetable" />
  }

  if (error) {
    return (
      <Alert tone="error" title="Could not load the timetable">
        {error.message}
      </Alert>
    )
  }

  if (!data) {
    return <EmptyState title="Section not found" />
  }

  const today = data.todayName
  const currentMinutes = toMinutes(data.currentTime)

  return (
    <div className="space-y-6">
      <section className="card p-5">
        <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Weekly timetable</p>
        <h1 className="mt-1 text-xl font-semibold text-slate-900">
          {data.programName}, semester {data.semesterNumber}, section {data.sectionName}
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          Academic year {data.academicYear}
          {data.timetable
            ? ` - version ${data.timetable.version}, effective from ${data.timetable.effectiveFrom ?? 'not set'}`
            : ''}
        </p>
      </section>

      {!data.timetable && (
        <Alert tone="warning" title="No published timetable">
          {data.statusMessage}
        </Alert>
      )}

      <div className="space-y-4">
        {data.days.map((day) => {
          const isToday = day.dayName === today
          return (
            <section
              key={day.dayOfWeek}
              aria-labelledby={`day-${day.dayOfWeek}`}
              className={`card overflow-hidden ${isToday ? 'ring-2 ring-brand-200' : ''}`}
            >
              <header className="flex items-center justify-between gap-3 border-b border-slate-200 bg-slate-50 px-5 py-3">
                <h2 id={`day-${day.dayOfWeek}`} className="text-sm font-semibold text-slate-900">
                  {dayName(day.dayOfWeek)}
                </h2>
                {isToday && (
                  <span className="rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-semibold text-brand-700">
                    Today
                  </span>
                )}
              </header>

              {day.periods.length === 0 ? (
                <p className="px-5 py-4 text-sm text-slate-500">
                  Nothing scheduled on this day in the published timetable.
                </p>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {day.periods.map((period) => (
                    <PeriodRow
                      key={`${day.dayOfWeek}-${period.startTime}-${period.subjectCode ?? period.subjectName ?? ''}`}
                      period={period}
                      highlight={
                        isToday && isRunningNow(currentMinutes, period.startTime, period.endTime)
                      }
                    />
                  ))}
                </ul>
              )}
            </section>
          )
        })}
      </div>
    </div>
  )
}

function PeriodRow({ period, highlight }: { period: ClassInfo; highlight: boolean }) {
  return (
    <li
      className={`grid gap-2 px-5 py-4 sm:grid-cols-[7rem_1fr] sm:gap-4 ${
        highlight ? 'bg-emerald-50/70' : ''
      }`}
    >
      <p className="font-mono text-sm tabular-nums font-semibold text-slate-700">
        {timeRange(period)}
      </p>
      <div className="min-w-0">
        <p className="text-sm font-semibold text-slate-900">{classTitle(period)}</p>
        <p className="mt-0.5 text-sm text-slate-600">
          {[
            period.subjectCode,
            period.facultyName ?? undefined,
            period.roomCode ?? undefined,
          ]
            .filter(Boolean)
            .join(' - ') || 'No further detail recorded'}
        </p>
        {period.entryType !== 'CLASS' && (
          <span className="mt-1 inline-block rounded bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-600">
            {period.entryType}
          </span>
        )}
      </div>
    </li>
  )
}

/** True only when the college clock sits inside this period's half open interval. */
function isRunningNow(currentMinutes: number | null, start: string, end: string): boolean {
  const from = toMinutes(start)
  const to = toMinutes(end)
  if (currentMinutes === null || from === null || to === null) {
    return false
  }
  return currentMinutes >= from && currentMinutes < to
}

/** "09:30" to minutes since midnight, for comparing against the current time. */
function toMinutes(time: string | null): number | null {
  if (!time) {
    return null
  }
  const [hoursText, minutesText] = time.split(':')
  const hours = Number(hoursText)
  const minutes = Number(minutesText)
  if (Number.isNaN(hours) || Number.isNaN(minutes)) {
    return null
  }
  return hours * 60 + minutes
}