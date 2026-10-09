import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { ApiRequestError } from '../api/client'
import { lookupApi } from '../api/endpoints'
import { CourseSearchForm } from '../components/search/CourseSearchForm'
import { RegistrationSearchForm } from '../components/search/RegistrationSearchForm'
import { EmptyState } from '../components/common/EmptyState'
import { Spinner } from '../components/common/Spinner'
import type { LookupResponse } from '../types/api'
import { ResultPanel } from '../components/timetable/ResultPanel'

/**
 * Dedicated lookup screen.
 *
 * Registration numbers can also arrive in the URL as `?reg=...`, which is how the search results
 * from another screen open straight into an answer.
 */
export default function StudentLookupPage() {
  const [params, setParams] = useSearchParams()
  const [result, setResult] = useState<LookupResponse | null>(null)
  const [error, setError] = useState<ApiRequestError | null>(null)
  const [loading, setLoading] = useState(false)

  const run = async (action: () => Promise<LookupResponse>) => {
    setLoading(true)
    setError(null)
    try {
      setResult(await action())
    } catch (cause) {
      setResult(null)
      setError(cause as ApiRequestError)
    } finally {
      setLoading(false)
    }
  }

  const lookupByRegistration = (registrationNumber: string) => {
    setParams({ reg: registrationNumber }, { replace: true })
    void run(() => lookupApi.byRegistrationNumber(registrationNumber))
  }

  const lookupByCourse = (q: string) => {
    setParams({ q }, { replace: true })
    void run(() => lookupApi.section({ q }))
  }

  return (
    <div className="space-y-6">
      <section className="card p-5 sm:p-6">
        <h1 className="text-xl font-semibold text-slate-900">Student lookup</h1>
        <p className="mt-1 max-w-2xl text-sm text-slate-600">
          Search by registration number, or describe the course, semester and section. A section is
          only chosen for you when the match is unambiguous.
        </p>
        <div className="mt-5 grid gap-6 lg:grid-cols-2">
          <RegistrationSearchForm
            onSubmit={lookupByRegistration}
            loading={loading}
            defaultValue={params.get('reg') ?? ''}
          />
          <CourseSearchForm onSubmit={lookupByCourse} loading={loading} />
        </div>
      </section>

      {error && (
        <div className="card p-5">
          <p className="text-sm font-semibold text-red-800">The lookup could not be completed</p>
          <p className="mt-1 text-sm text-slate-700">{error.message}</p>
        </div>
      )}

      {loading && <Spinner label="Checking the timetable" />}

      {!loading && !result && !error && (
        <EmptyState
          title="No lookup yet"
          description="Enter a registration number above, or describe the course, semester and section."
        />
      )}

      {result && <ResultPanel result={result} />}
    </div>
  )
}