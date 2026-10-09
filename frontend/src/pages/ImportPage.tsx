import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { importApi } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { Alert } from '../components/common/Alert'
import { Badge } from '../components/common/Badge'
import { Button } from '../components/common/Button'
import { EmptyState } from '../components/common/EmptyState'
import { Spinner } from '../components/common/Spinner'

const MAX_BYTES = 25 * 1024 * 1024

/**
 * PDF upload and import history.
 *
 * The upload result states plainly how many rows were found and how many still need a decision,
 * because a parser that finds nothing is a different situation from a parser that found rows the
 * administrator has not approved yet.
 */
export default function ImportPage() {
  const [file, setFile] = useState<File | null>(null)
  const [progress, setProgress] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const jobs = useAsync(() => importApi.list(), [])

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    if (!file) {
      setError('Choose a PDF file first.')
      return
    }
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      setError('Only PDF files are accepted.')
      return
    }
    if (file.size > MAX_BYTES) {
      setError('That file is larger than the 25 MB limit.')
      return
    }
    setProgress(0)
    setUploading(true)
    try {
      const result = await importApi.upload(file, setProgress)
      jobs.reload()
      setFile(null)
      if (inputRef.current) {
        inputRef.current.value = ''
      }
      setProgress(null)
      if (result.status === 'FAILED') {
        setError(result.errorSummary ?? 'The PDF could not be read.')
      }
    } catch (cause) {
      setProgress(null)
      setError(cause instanceof Error ? cause.message : 'The upload failed.')
    }
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-semibold text-slate-900">Timetable PDF import</h1>
        <p className="mt-1 max-w-2xl text-sm text-slate-600">
          Upload a published timetable PDF. Extracted rows are reviewed before anything is used, and
          a timetable is only published after a separate, explicit step.
        </p>
      </header>

      <form onSubmit={submit} className="card space-y-4 p-5">
        {error && <Alert tone="error">{error}</Alert>}

        <div>
          <label htmlFor="timetable-file" className="field-label">
            Timetable PDF
          </label>
          <input
            id="timetable-file"
            ref={inputRef}
            type="file"
            accept="application/pdf,.pdf"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            aria-describedby="timetable-file-hint"
            className="field-input file:mr-3 file:rounded file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-slate-700"
          />
          <p id="timetable-file-hint" className="mt-1.5 text-xs text-slate-500">
            PDF only, up to 25 MB and 100 pages. Scanned pages are reported as needing OCR rather
            than being guessed at.
          </p>
        </div>

        {progress !== null && (
          <div>
            <label htmlFor="upload-progress" className="field-label">
              Uploading {progress}%
            </label>
            <progress
              id="upload-progress"
              max={100}
              value={progress}
              className="h-2 w-full"
            />
          </div>
        )}

        <Button type="submit" loading={uploading}>
          Upload and extract
        </Button>
      </form>

      <section>
        <h2 className="mb-3 text-base font-semibold text-slate-900">Import history</h2>
        {jobs.loading && <Spinner label="Loading import history" />}
        {jobs.error && (
          <Alert tone="error" title="Could not load the import history">
            {jobs.error.message}
          </Alert>
        )}
        {jobs.data && jobs.data.length === 0 && (
          <EmptyState title="No imports yet" description="Uploaded PDFs and their results appear here." />
        )}

        <ul className="space-y-3">
          {(jobs.data ?? []).map((job) => (
            <li key={job.id} className="card p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-medium text-slate-900">{job.filename}</p>
                  <p className="mt-0.5 text-sm text-slate-600">
                    {job.totalPages ?? 0} page(s)
                    {job.pagesNeedingOcr ? `, ${job.pagesNeedingOcr} needing OCR` : ''}
                    {job.candidateCount ? `, ${job.candidateCount} row(s) found` : ''}
                    {job.pendingCount ? `, ${job.pendingCount} still to review` : ''}
                  </p>
                  {job.errorSummary && (
                    <p className="mt-1 text-sm text-amber-800">{job.errorSummary}</p>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={jobTone(job.status)}>{job.status.replace(/_/g, ' ')}</Badge>
                  {job.pendingCount > 0 && (
                    <Link to={`/admin/imports/${job.id}`} className="btn-secondary px-3 py-1.5 text-xs">
                      Review rows
                    </Link>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

function jobTone(status: string): 'neutral' | 'brand' | 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'PUBLISHED':
    case 'APPROVED':
      return 'success'
    case 'AWAITING_REVIEW':
    case 'OCR_PENDING':
      return 'warning'
    case 'FAILED':
    case 'CANCELLED':
      return 'danger'
    case 'PARSING':
    case 'EXTRACTING':
      return 'brand'
    default:
      return 'neutral'
  }
}