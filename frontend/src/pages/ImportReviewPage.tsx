import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { catalogApi, importApi } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { Alert } from '../components/common/Alert'
import { Badge } from '../components/common/Badge'
import { Button } from '../components/common/Button'
import { EmptyState } from '../components/common/EmptyState'
import { Spinner } from '../components/common/Spinner'
import type { ImportCandidateDto } from '../types/api'

/**
 * Review of the rows an importer extracted.
 *
 * This screen exists because automated extraction is not trusted. Every row keeps the raw text it
 * came from, so the reviewer can compare the machine's reading against the page. Nothing reaches a
 * timetable until a row has been explicitly accepted or corrected here.
 */
export default function ImportReviewPage() {
  const { jobId } = useParams<{ jobId: string }>()
  const numericId = Number(jobId)
  const navigate = useNavigate()

  const job = useAsync(() => importApi.get(numericId), [numericId], {
    enabled: Number.isFinite(numericId),
  })
  const candidates = useAsync(() => importApi.candidates(numericId), [numericId], {
    enabled: Number.isFinite(numericId),
  })
  const sections = useAsync(() => catalogApi.sections(), [])

  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const [busy, setBusy] = useState<number | null>(null)
  const [sectionId, setSectionId] = useState('')
  const [effectiveFrom, setEffectiveFrom] = useState(today())

  const update = async (candidate: ImportCandidateDto, body: Record<string, unknown>) => {
    setBusy(candidate.id)
    setNotice(null)
    try {
      await importApi.updateCandidate(numericId, candidate.id, body)
      candidates.reload()
      job.reload()
    } catch (cause) {
      setNotice({
        tone: 'error',
        text: cause instanceof Error ? cause.message : 'The row could not be updated.',
      })
    } finally {
      setBusy(null)
    }
  }

  const approve = async () => {
    if (!sectionId) {
      setNotice({ tone: 'error', text: 'Choose the section these rows belong to.' })
      return
    }
    try {
      const result = await importApi.approve(numericId, {
        sectionId: Number(sectionId),
        effectiveFrom,
      })
      navigate(`/admin/timetables`)
      setNotice({ tone: 'success', text: result.message })
    } catch (cause) {
      setNotice({
        tone: 'error',
        text: cause instanceof Error ? cause.message : 'The import could not be approved.',
      })
    }
  }

  if (job.loading || candidates.loading) {
    return <Spinner label="Loading extracted rows" />
  }

  if (job.error) {
    return (
      <Alert tone="error" title="Could not load this import">
        {job.error.message}
      </Alert>
    )
  }

  if (!job.data || candidates.data === null) {
    return <EmptyState title="Import not found" />
  }

  const rows = candidates.data
  const pending = rows.filter((row) => row.reviewStatus === 'PENDING').length

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-semibold text-slate-900">Review extracted rows</h1>
        <p className="mt-1 text-sm text-slate-600">
          {job.data.filename} - {rows.length} row(s) found, {pending} still need a decision. Check
          each row against the source page before accepting it.
        </p>
      </header>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

      {job.data.status === 'FAILED' && (
        <Alert tone="warning" title="This import did not complete">
          {job.data.errorSummary ?? 'No rows were produced.'}
        </Alert>
      )}

      {rows.length === 0 ? (
        <EmptyState
          title="No rows were extracted"
          description="Nothing was read from this PDF. If the pages are scans, run OCR first and upload the result."
        />
      ) : (
        <ul className="space-y-3">
          {rows.map((row) => (
            <CandidateRow
              key={row.id}
              row={row}
              busy={busy === row.id}
              onApprove={() => void update(row, { reviewStatus: 'APPROVED' })}
              onReject={() => void update(row, { reviewStatus: 'REJECTED' })}
              onSave={(body) => void update(row, { ...body, reviewStatus: 'EDITED' })}
            />
          ))}
        </ul>
      )}

      {rows.some((row) => row.reviewStatus !== 'PENDING' && row.reviewStatus !== 'REJECTED') && (
        <section className="card space-y-4 p-5">
          <h2 className="text-base font-semibold text-slate-900">Create a draft timetable</h2>
          <p className="text-sm text-slate-600">
            This builds a draft from the accepted rows. It is not published yet: you review the
            result on the timetables screen and publish it separately.
          </p>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="approve-section" className="field-label">
                Section
              </label>
              <select
                id="approve-section"
                value={sectionId}
                onChange={(event) => setSectionId(event.target.value)}
                className="field-input"
              >
                <option value="">Choose a section</option>
                {(sections.data ?? []).map((section) => (
                  <option key={section.id} value={section.id}>
                    {section.programCode} - semester {section.semesterNumber} - section{' '}
                    {section.sectionName}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="effective-from" className="field-label">
                Effective from
              </label>
              <input
                id="effective-from"
                type="date"
                value={effectiveFrom}
                onChange={(event) => setEffectiveFrom(event.target.value)}
                className="field-input"
              />
            </div>
          </div>

          <Button onClick={() => void approve()}>Create draft timetable</Button>
        </section>
      )}
    </div>
  )
}

function CandidateRow({
  row,
  busy,
  onApprove,
  onReject,
  onSave,
}: {
  row: ImportCandidateDto
  busy: boolean
  onApprove: () => void
  onReject: () => void
  onSave: (body: Record<string, unknown>) => void
}) {
  const [notes, setNotes] = useState('')
  const [subjectId, setSubjectId] = useState<string>(row.subjectId ? String(row.subjectId) : '')
  const subjects = useAsync(() => catalogApi.subjects(), [])

  return (
    <li className="card p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-slate-900">
            {row.dayOfWeek ? dayLabel(row.dayOfWeek) : 'No day'} {row.startTime ?? '??:??'} -{' '}
            {row.endTime ?? '??:??'}
          </p>
          <p className="mt-0.5 text-sm text-slate-700">{row.subjectLabel ?? 'No subject recognised'}</p>
          <p className="mt-0.5 text-xs text-slate-500">
            {[row.facultyLabel, row.roomLabel].filter(Boolean).join(' - ') || 'No faculty or room'}
          </p>
          {row.rawText && (
            <details className="mt-2">
              <summary className="cursor-pointer text-xs font-medium text-slate-600">
                Source text from page {row.pageNumber}
              </summary>
              <pre className="mt-1 overflow-x-auto rounded bg-slate-50 p-2 font-mono text-xs whitespace-pre-wrap text-slate-700">
                {row.rawText}
              </pre>
            </details>
          )}
          {row.validationErrors && (
            <p className="mt-2 text-xs text-amber-800">{row.validationErrors}</p>
          )}
        </div>
        <div className="flex flex-col items-end gap-2">
          <Badge tone={reviewTone(row.reviewStatus)}>{row.reviewStatus}</Badge>
          {row.confidence !== null && (
            <span className="text-xs tabular-nums text-slate-500">
              confidence {(row.confidence * 100).toFixed(0)}%
            </span>
          )}
        </div>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <div>
          <label htmlFor={`subject-${row.id}`} className="field-label">
            Map to subject
          </label>
          <select
            id={`subject-${row.id}`}
            value={subjectId}
            onChange={(event) => setSubjectId(event.target.value)}
            className="field-input"
          >
            <option value="">Not mapped</option>
            {(subjects.data ?? []).map((subject) => (
              <option key={subject.id} value={subject.id}>
                {subject.subjectCode} - {subject.subjectName}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor={`notes-${row.id}`} className="field-label">
            Reviewer note
          </label>
          <input
            id={`notes-${row.id}`}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            className="field-input"
            placeholder="What you checked against the page"
          />
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        <Button
          loading={busy}
          onClick={() =>
            onSave({
              subjectId: subjectId ? Number(subjectId) : null,
              reviewerNotes: notes || null,
            })
          }
        >
          Save correction
        </Button>
        <Button variant="secondary" loading={busy} onClick={onApprove}>
          Accept as extracted
        </Button>
        <Button variant="danger" loading={busy} onClick={onReject}>
          Discard
        </Button>
      </div>
    </li>
  )
}

function dayLabel(dayOfWeek: number): string {
  return ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][
    dayOfWeek - 1
  ] ?? `Day ${dayOfWeek}`
}

function reviewTone(status: ImportCandidateDto['reviewStatus']) {
  switch (status) {
    case 'APPROVED':
      return 'success' as const
    case 'EDITED':
      return 'brand' as const
    case 'REJECTED':
      return 'danger' as const
    default:
      return 'warning' as const
  }
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}