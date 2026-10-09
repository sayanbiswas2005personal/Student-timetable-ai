import { Link } from 'react-router-dom'

import type { AmbiguityOption } from '../../types/api'
import { Alert } from '../common/Alert'

interface AmbiguityPickerProps {
  options: AmbiguityOption[]
}

/**
 * Shown when a search matches more than one verified section.
 *
 * The system refuses to choose. Picking the wrong section would show the timetable of a different
 * group of students, which is worse than asking one extra question.
 */
export function AmbiguityPicker({ options }: AmbiguityPickerProps) {
  if (options.length === 0) {
    return (
      <Alert tone="warning" title="No matching section">
        No course, semester and section in the verified course list matches that search. Check the
        spelling, or ask an administrator to confirm the course name.
      </Alert>
    )
  }

  return (
    <div className="card p-5">
      <h2 className="text-base font-semibold text-slate-900">Choose a section</h2>
      <p className="mt-1 text-sm text-slate-600">
        That search matches {options.length} sections. Pick the right one to see its timetable.
      </p>
      <ul className="mt-4 space-y-2">
        {options.map((option) => (
          <li key={option.sectionId}>
            <Link
              to={`/sections/${option.sectionId}/timetable`}
              className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-4 py-3 transition-colors duration-150 hover:border-brand-300 hover:bg-brand-50"
            >
              <span className="min-w-0">
                <span className="block truncate text-sm font-semibold text-slate-900">
                  {option.programName}
                </span>
                <span className="block text-sm text-slate-600">
                  {option.academicYear}, semester {option.semesterNumber}, section {option.sectionName}
                </span>
              </span>
              <span className="shrink-0 font-mono text-xs text-slate-500">{option.programCode}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
