import type { StudentSummary } from '../../types/api'
import { Badge } from '../common/Badge'

interface StudentIdentityCardProps {
  student: StudentSummary
}

/**
 * Who the record belongs to and which verified section it maps to.
 *
 * The section shown here comes from the database, never from anything embedded in the
 * registration number.
 */
export function StudentIdentityCard({ student }: StudentIdentityCardProps) {
  return (
    <div className="card p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Student</p>
          <p className="mt-0.5 font-mono text-base font-semibold break-all text-slate-900">
            {student.registrationNumber}
          </p>
          {student.fullName && <p className="mt-0.5 text-sm text-slate-700">{student.fullName}</p>}
        </div>
        <Badge tone={student.active ? 'success' : 'warning'}>
          {student.active ? 'Active' : 'Inactive'}
        </Badge>
      </div>

      <dl className="mt-4 grid gap-3 border-t border-slate-100 pt-4 sm:grid-cols-2 lg:grid-cols-4">
        <Detail label="Department" value={student.departmentName ?? 'Not recorded'} />
        <Detail label="Programme" value={student.programName} />
        <Detail
          label="Semester"
          value={`Semester ${student.semesterNumber} (${student.academicYear})`}
        />
        <Detail label="Section" value={student.sectionName} />
      </dl>
    </div>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase">{label}</dt>
      <dd className="mt-0.5 text-sm font-medium text-slate-900">{value}</dd>
    </div>
  )
}
