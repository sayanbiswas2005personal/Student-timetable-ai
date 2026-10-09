import { ExternalLink } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { LookupResponse } from '../../types/api'
import { statusPresentation } from '../../utils/presentation'
import { Alert } from '../common/Alert'
import { Badge } from '../common/Badge'

interface LookupResultProps {
  result: LookupResponse
  /** Rendered under the status, for example a link to the full weekly timetable. */
  footer?: React.ReactNode
}

/**
 * Renders a lookup result.
 *
 * The status line is the headline, because that is the question being asked. Everything else is
 * supporting detail from the published timetable.
 */
export function LookupResult({ result, footer }: LookupResultProps) {
  const presentation = statusPresentation(result.status)

  return (
    <div className="space-y-4">
      <div className="card p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <Badge tone={presentation.tone}>{presentation.label}</Badge>
            <p className="mt-2 text-base text-slate-800">{result.message}</p>
          </div>
          {result.collegeTime && (
            <p className="text-right text-sm tabular-nums text-slate-600">
              <span className="block font-semibold text-slate-900">
                {result.dayName?.slice(0, 3)}, {result.collegeTime}
              </span>
              <span className="text-xs">{result.collegeDate}</span>
            </p>
          )}
        </div>

        {result.timetable && (
          <p className="mt-3 border-t border-slate-100 pt-3 text-xs text-slate-500">
            From timetable version {result.timetable.version}
            {result.timetable.effectiveFrom ? `, effective ${result.timetable.effectiveFrom}` : ''}
            {result.timetable.sourceFilename ? ` (${result.timetable.sourceFilename})` : ''}
          </p>
        )}

        {footer}
      </div>

      {result.notices.length > 0 && (
        <Alert tone="warning" title="Data quality notes">
          <ul className="list-disc space-y-1 pl-4">
            {result.notices.map((notice) => (
              <li key={notice}>{notice}</li>
            ))}
          </ul>
        </Alert>
      )}
    </div>
  )
}

/** Explains that the answer could not be determined and links to the section timetable. */
export function SectionTimetableLink({ sectionId }: { sectionId: number }) {
  return (
    <Link to={`/sections/${sectionId}/timetable`} className="btn-secondary mt-4">
      View the whole week
      <ExternalLink aria-hidden="true" className="size-4" />
    </Link>
  )
}