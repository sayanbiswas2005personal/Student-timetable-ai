import { Clock3, DoorOpen, GraduationCap, UserRound } from 'lucide-react'

import type { ClassInfo } from '../../types/api'
import { classTitle, readableTime, timeRange } from '../../utils/presentation'
import { Badge } from '../common/Badge'

interface CurrentClassCardProps {
  currentClass: ClassInfo
}

/**
 * The period that is scheduled right now.
 *
 * Only facts from the timetable are shown. There is no wording anywhere on this card that could be
 * read as a statement about the student's whereabouts, because the data does not support one.
 */
export function CurrentClassCard({ currentClass }: CurrentClassCardProps) {
  return (
    <div className="card border-emerald-300 bg-emerald-50/60 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-emerald-800">Scheduled right now</p>
          <h3 className="mt-1 text-xl font-semibold text-slate-900">{classTitle(currentClass)}</h3>
          {currentClass.subjectCode && (
            <p className="mt-0.5 font-mono text-sm text-slate-600">{currentClass.subjectCode}</p>
          )}
        </div>
        <Badge tone="success">{currentClass.entryType === 'CLASS' ? 'Class' : currentClass.entryType}</Badge>
      </div>

      <dl className="mt-4 grid gap-3 sm:grid-cols-3">
        <Fact icon={Clock3} label="Time">
          {timeRange(currentClass)}
        </Fact>
        <Fact icon={UserRound} label="Faculty">
          {currentClass.facultyName ?? 'Not recorded in this timetable'}
        </Fact>
        <Fact icon={DoorOpen} label="Room">
          {currentClass.roomCode ?? 'Not recorded in this timetable'}
        </Fact>
      </dl>
    </div>
  )
}

interface NextClassCardProps {
  nextClass: ClassInfo
  onLaterDay: boolean
}

/** The next scheduled period, including when it is on a later weekday. */
export function NextClassCard({ nextClass, onLaterDay }: NextClassCardProps) {
  return (
    <div className="card p-5">
      <p className="text-sm font-semibold text-slate-500">
        Next scheduled {onLaterDay ? `class (${nextClass.dayName})` : 'class'}
      </p>
      <h3 className="mt-1 text-base font-semibold text-slate-900">{classTitle(nextClass)}</h3>
      <p className="mt-1 text-sm text-slate-600">
        {readableTime(nextClass.startTime)} to {readableTime(nextClass.endTime)}
        {nextClass.roomCode ? ` in ${nextClass.roomCode}` : ''}
      </p>
    </div>
  )
}

function Fact({
  icon: Icon,
  label,
  children,
}: {
  icon: typeof GraduationCap
  label: string
  children: React.ReactNode
}) {
  return (
    <div>
      <dt className="flex items-center gap-1.5 text-xs font-medium tracking-wide text-slate-500 uppercase">
        <Icon aria-hidden="true" className="size-3.5" />
        {label}
      </dt>
      <dd className="mt-0.5 text-sm font-medium text-slate-900">{children}</dd>
    </div>
  )
}