import { useState } from 'react'

import { timetableApi, catalogApi } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { Alert } from '../components/common/Alert'
import { Badge } from '../components/common/Badge'
import { Button } from '../components/common/Button'
import { EmptyState } from '../components/common/EmptyState'
import { Spinner } from '../components/common/Spinner'
import type { TimetableDto } from '../types/api'

/**
 * Timetable versions, publishing and rollback.
 *
 * Publishing is the point at which data becomes visible to staff, so the screen shows exactly what
 * is blocking it and asks for confirmation before anything is published.
 */
export default function TimetableAdminPage() {
  const [sectionFilter, setSectionFilter] = useState<number | undefined>(undefined)
  const sections = useAsync(() => catalogApi.sections(), [])
  const timetables = useAsync(() => timetableApi.list(sectionFilter), [sectionFilter])
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const publish = async (id: number) => {
    setBusyId(id)
    setNotice(null)
    try {
      await timetableApi.publish(id)
      setNotice({ tone: 'success', text: `Timetable version ${id} is now published.` })
      timetables.reload()
    } catch (cause) {
      setNotice({
        tone: 'error',
        text: cause instanceof Error ? cause.message : 'The timetable could not be published.',
      })
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-semibold text-slate-900">Timetables</h1>
        <p className="mt-1 max-w-2xl text-sm text-slate-600">
          Only published versions are used by the lookup engine. Earlier versions are kept so a
          historical date still answers correctly.
        </p>
      </header>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

      <section className="card p-5">
        <label htmlFor="section-filter" className="field-label">
          Filter by section
        </label>
        <select
          id="section-filter"
          value={sectionFilter ?? ''}
          onChange={(event) => setSectionFilter(event.target.value ? Number(event.target.value) : undefined)}
          className="field-input max-w-xl"
        >
          <option value="">All sections</option>
          {(sections.data ?? []).map((section) => (
            <option key={section.id} value={section.id}>
              {section.programCode} - semester {section.semesterNumber} - section {section.sectionName}
            </option>
          ))}
        </select>
      </section>

      {sections.loading && <Spinner label="Loading sections" />}
      {timetables.loading && <Spinner label="Loading timetables" />}

      {timetables.data && timetables.data.length === 0 && (
        <EmptyState
          title="No timetables yet"
          description="Create one from the PDF import screen, or by entering periods on a draft."
        />
      )}

      <div className="space-y-4">
        {(timetables.data ?? []).map((timetable) => (
          <TimetableCard
            key={timetable.id}
            timetable={timetable}
            busy={busyId === timetable.id}
            onPublish={() => void publish(timetable.id)}
          />
        ))}
      </div>
    </div>
  )
}

function TimetableCard({
  timetable,
  busy,
  onPublish,
}: {
  timetable: TimetableDto
  busy: boolean
  onPublish: () => void
}) {
  const { data: report, reload } = useAsync(() => timetableApi.validate(timetable.id), [timetable.id])

  const blocking = (report?.issues ?? []).filter((issue) => issue.severity === 'ERROR')

  return (
    <article className="card p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-base font-semibold text-slate-900">
            {timetable.programName}, semester {timetable.semesterNumber}, section{' '}
            {timetable.sectionName}
          </h2>
          <p className="mt-0.5 text-sm text-slate-600">
            Version {timetable.version} - effective {timetable.effectiveFrom ?? 'not set'}
            {timetable.effectiveTo ? ` to ${timetable.effectiveTo}` : ' onwards'}
            {timetable.sourceFilename ? ` - from ${timetable.sourceFilename}` : ''}
          </p>
        </div>
        <Badge tone={statusTone(timetable.status)}>{timetable.status}</Badge>
      </div>

      {report && blocking.length > 0 && (
        <div className="mt-4">
          <Alert tone="error" title={`${blocking.length} problem(s) must be fixed before publishing`}>
            <ul className="list-disc space-y-1 pl-4">
              {blocking.map((issue, index) => (
                <li key={`${issue.entryId ?? 'x'}-${index}`}>{issue.message}</li>
              ))}
            </ul>
          </Alert>
        </div>
      )}

      {report && blocking.length === 0 && (
        <p className="mt-4 text-sm text-emerald-700">
          No blocking problems found. This version is ready to publish.
        </p>
      )}

      <div className="mt-4 flex flex-wrap gap-2">
        <Button
          variant="secondary"
          onClick={() => {
            reload()
          }}
        >
          Re-check
        </Button>
        {timetable.status !== 'PUBLISHED' && (
          <Button onClick={onPublish} loading={busy} disabled={(report?.issues.length ?? 0) > 0 && blocking.length > 0}>
            Publish
          </Button>
        )}
      </div>
    </article>
  )
}

function statusTone(status: TimetableDto['status']): 'neutral' | 'brand' | 'success' | 'warning' {
  switch (status) {
    case 'PUBLISHED':
      return 'success'
    case 'UNDER_REVIEW':
      return 'warning'
    case 'SUPERSEDED':
      return 'neutral'
    default:
      return 'brand'
  }
}