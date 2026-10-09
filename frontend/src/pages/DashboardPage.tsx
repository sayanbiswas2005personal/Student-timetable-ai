import { useState } from 'react'

import { ApiRequestError } from '../api/client'
import { lookupApi } from '../api/endpoints'
import { useRecentLookups } from '../hooks/useRecentLookups'
import { CourseSearchForm } from '../components/search/CourseSearchForm'
import { RegistrationSearchForm } from '../components/search/RegistrationSearchForm'
import { ResultPanel } from '../components/timetable/ResultPanel'
import { Alert } from '../components/common/Alert'
import { Button } from '../components/common/Button'
import { Spinner } from '../components/common/Spinner'
import { useCollegeClock } from '../hooks/useCollegeClock'
import type { LookupResponse } from '../types/api'

/**
 * The staff dashboard: the two searches, the college clock, and recent lookups.
 *
 * Recent lookups live in this browser's session storage only. Nothing about who has been looked up
 * is sent to the server, because that history is student data and no screen here needs it shared.
 */
export default function DashboardPage() {
  const clock = useCollegeClock()
  const [result, setResult] = useState<LookupResponse | null>(null)
  const [error, setError] = useState<ApiRequestError | null>(null)
  const [loading, setLoading] = useState(false)
  const { entries: recent, remember, clear } = useRecentLookups()

  const run = async (action: () => Promise<LookupResponse>) => {
    setLoading(true)
    setError(null)
    try {
      const response = await action()
      setResult(response)
      if (response.student) {
        remember(response)
      }
    } catch (cause) {
      setResult(null)
      setError(cause as ApiRequestError)
    } finally {
      setLoading(false)
    }
  }

  const lookupByRegistration = (registrationNumber: string) =>
    void run(() => lookupApi.byRegistrationNumber(registrationNumber))

  const lookupByCourse = (q: string) => void run(() => lookupApi.section({ q }))

  return (
    <div className="space-y-6">
      <section className="card p-5 sm:p-6">
        <h1 className="text-xl font-semibold text-slate-900">Where should this student be?</h1>
        <p className="mt-1 max-w-2xl text-sm text-slate-600">
          Look up the class a student is scheduled to attend at this moment. This shows the
          timetable, not attendance: it cannot tell you whether a student is present.
        </p>

        {clock && (
          <p className="mt-3 text-sm font-medium tabular-nums text-slate-700">
            College time: {clock.dayName}, {clock.date} {clock.time} ({clock.timezone})
          </p>
        )}

        <div className="mt-5 grid gap-6 lg:grid-cols-2">
          <RegistrationSearchForm onSubmit={lookupByRegistration} loading={loading} />
          <CourseSearchForm onSubmit={lookupByCourse} loading={loading} />
        </div>
      </section>

      {error && (
        <Alert tone="error" title="The lookup could not be completed">
          {error.message}
          {error.details.length > 0 && (
            <ul className="mt-1 list-disc pl-4">
              {error.details.map((detail) => (
                <li key={detail}>{detail}</li>
              ))}
            </ul>
          )}
          <p className="mt-2">
            <Button variant="secondary" onClick={() => setError(null)}>
              Dismiss
            </Button>
          </p>
        </Alert>
      )}

      {loading && <Spinner label="Checking the timetable" />}

      {result && <ResultPanel result={result} />}

      {recent.length > 0 && (
        <section className="card p-5">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-slate-900">Recent lookups</h2>
            <Button variant="secondary" onClick={clear}>
              Clear
            </Button>
          </div>
          <p className="mt-1 text-xs text-slate-500">
            Kept in this browser only, for this session. Nothing is stored on the server.
          </p>
          <ul className="mt-3 flex flex-wrap gap-2">
            {recent.map((entry) => (
              <li key={entry.registrationNumber}>
                <button
                  type="button"
                  className="rounded-lg border border-slate-200 px-3 py-2 text-left text-sm transition-colors duration-150 hover:border-brand-300 hover:bg-brand-50"
                  onClick={() => lookupByRegistration(entry.registrationNumber)}
                >
                  <span className="block font-mono text-xs text-slate-900">
                    {entry.registrationNumber}
                  </span>
                  <span className="block text-xs text-slate-500">{entry.label}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
