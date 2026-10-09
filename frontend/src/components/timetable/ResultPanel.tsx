import { AmbiguityPicker } from './AmbiguityPicker'
import { CurrentClassCard, NextClassCard } from './CurrentClassCard'
import { LookupResult, SectionTimetableLink } from './LookupResult'
import { StudentIdentityCard } from './StudentIdentityCard'
import { Alert } from '../common/Alert'
import { classTitle, readableTime } from '../../utils/presentation'
import type { LookupResponse } from '../../types/api'

/**
 * Shared result rendering, so the dashboard and the dedicated lookup screen can never drift
 * apart in wording or in what they choose to show.
 */
export function ResultPanel({ result }: { result: LookupResponse }) {
  if (result.status === 'AMBIGUOUS_SEARCH') {
    return <AmbiguityPicker options={result.options} />
  }

  return (
    <div className="space-y-4">
      {result.student && <StudentIdentityCard student={result.student} />}
      {result.currentClass && <CurrentClassCard currentClass={result.currentClass} />}
      <LookupResult
        result={result}
        footer={
          result.student ? <SectionTimetableLink sectionId={result.student.sectionId} /> : null
        }
      />
      {result.nextClass && (
        <NextClassCard nextClass={result.nextClass} onLaterDay={result.nextClassOnLaterDay} />
      )}
      {result.conflictingClasses.length > 0 && (
        <Alert tone="error" title="Overlapping entries">
          <ul className="list-disc space-y-1 pl-4">
            {result.conflictingClasses.map((entry, index) => (
              <li key={`${entry.timetableId}-${index}`}>
                {classTitle(entry)} ({readableTime(entry.startTime)} to {readableTime(entry.endTime)})
              </li>
            ))}
          </ul>
        </Alert>
      )}
    </div>
  )
}
