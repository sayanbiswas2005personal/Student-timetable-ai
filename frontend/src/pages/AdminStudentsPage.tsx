import { useState } from 'react'

import { catalogApi, studentApi } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { Alert } from '../components/common/Alert'
import { Badge } from '../components/common/Badge'
import { Button } from '../components/common/Button'
import { EmptyState } from '../components/common/EmptyState'
import { Field } from '../components/common/Field'
import { Spinner } from '../components/common/Spinner'
import type { SectionDto, StudentResponse } from '../types/api'

/**
 * Student administration.
 *
 * A student's section is always chosen from the list of sections that exist in the database. The
 * form never guesses a section from a registration number, because there is no verified rule that
 * would let it.
 */
export default function AdminStudentsPage() {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [showCreate, setShowCreate] = useState(false)

  const sections = useAsync(() => catalogApi.sections(), [])
  const students = useAsync(
    () => studentApi.search(query, page, 25),
    [query, page],
    { enabled: query.trim().length >= 2 },
  )

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Students</h1>
          <p className="mt-1 text-sm text-slate-600">
            Search by registration number, or enrol a student into a verified section.
          </p>
        </div>
        <Button onClick={() => setShowCreate((value) => !value)}>
          {showCreate ? 'Close' : 'Enrol a student'}
        </Button>
      </header>

      {showCreate && sections.data && (
        <CreateStudentForm sections={sections.data} onCreated={() => students.reload()} />
      )}

      <section className="card p-5">
        <Field label="Search by registration number" hint="At least two characters.">
          {({ id, describedBy }) => (
            <div className="flex flex-col gap-2 sm:flex-row">
              <input
                id={id}
                type="search"
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value)
                  setPage(0)
                }}
                placeholder="BTCSEAIML/2023"
                aria-describedby={describedBy}
                className="field-input font-mono"
              />
            </div>
          )}
        </Field>
      </section>

      {students.error && (
        <Alert tone="error" title="The search failed">
          {students.error.message}
        </Alert>
      )}
      {students.loading && <Spinner label="Searching" />}

      {students.data && students.data.content.length === 0 && (
        <EmptyState title="No matching students" description="Try part of a registration number." />
      )}

      {students.data && students.data.content.length > 0 && (
        <>
          <div className="card overflow-hidden">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <caption className="sr-only">
                Students matching {query}, showing {students.data.totalElements} in total
              </caption>
              <thead className="bg-slate-50">
                <tr>
                  <Th>Registration number</Th>
                  <Th>Name</Th>
                  <Th>Programme</Th>
                  <Th>Semester</Th>
                  <Th>Section</Th>
                  <Th>Status</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {students.data.content.map((student) => (
                  <StudentRow
                    key={student.id}
                    student={student}
                    onToggle={() => {
                      void studentApi
                        .changeStatus(student.id, !student.active)
                        .then(() => students.reload())
                    }}
                  />
                ))}
              </tbody>
            </table>
          </div>

          {students.data.totalPages > 1 && (
            <nav aria-label="Pagination" className="flex items-center justify-between">
              <Button
                variant="secondary"
                disabled={page === 0}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
              >
                Previous
              </Button>
              <p className="text-sm text-slate-600">
                Page {students.data.page + 1} of {students.data.totalPages}
              </p>
              <Button
                variant="secondary"
                disabled={page + 1 >= students.data.totalPages}
                onClick={() => setPage((value) => value + 1)}
              >
                Next
              </Button>
            </nav>
          )}
        </>
      )}
    </div>
  )
}

function StudentRow({
  student,
  onToggle,
}: {
  student: StudentResponse
  onToggle: () => void
}) {
  return (
    <tr>
      <Td className="font-mono">{student.registrationNumber}</Td>
      <Td>{student.fullName ?? '-'}</Td>
      <Td>{student.programCode}</Td>
      <Td>{student.semesterNumber}</Td>
      <Td>{student.sectionName}</Td>
      <Td>
        <div className="flex items-center gap-2">
          <Badge tone={student.active ? 'success' : 'warning'}>
            {student.active ? 'Active' : 'Inactive'}
          </Badge>
          <Button variant="secondary" onClick={onToggle} className="px-2 py-1 text-xs">
            {student.active ? 'Deactivate' : 'Activate'}
          </Button>
        </div>
      </Td>
    </tr>
  )
}

function CreateStudentForm({
  sections,
  onCreated,
}: {
  sections: SectionDto[]
  onCreated: () => void
}) {
  const [registrationNumber, setRegistrationNumber] = useState('')
  const [fullName, setFullName] = useState('')
  const [sectionId, setSectionId] = useState<string>('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [created, setCreated] = useState<string | null>(null)

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    setCreated(null)

    if (registrationNumber.trim().length < 3) {
      setError('Enter the registration number exactly as the college prints it.')
      return
    }
    if (!sectionId) {
      setError('Choose the section this student belongs to.')
      return
    }

    setSaving(true)
    try {
      const student = await studentApi.create({
        registrationNumber: registrationNumber.trim(),
        fullName: fullName.trim() || undefined,
        sectionId: Number(sectionId),
      })
      setCreated(`${student.registrationNumber} enrolled in section ${student.sectionName}.`)
      setRegistrationNumber('')
      setFullName('')
      setSectionId('')
      onCreated()
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : 'The student could not be created.'
      setError(message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className="card space-y-4 p-5">
      <h2 className="text-base font-semibold text-slate-900">Enrol a student</h2>

      {error && <Alert tone="error">{error}</Alert>}
      {created && <Alert tone="success">{created}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Registration number" required>
          {({ id }) => (
            <input
              id={id}
              value={registrationNumber}
              onChange={(event) => setRegistrationNumber(event.target.value)}
              className="field-input font-mono"
              placeholder="UG/02/BTCSEAIML/2023/024"
            />
          )}
        </Field>
        <Field label="Full name" hint="Optional. Only recorded if the college holds it.">
          {({ id }) => (
            <input
              id={id}
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
              className="field-input"
            />
          )}
        </Field>
      </div>

      <Field label="Section" required hint="The verified group, never derived from the number.">
        {({ id }) => (
          <select
            id={id}
            value={sectionId}
            onChange={(event) => setSectionId(event.target.value)}
            className="field-input"
          >
            <option value="">Choose a section</option>
            {sections.map((section) => (
              <option key={section.id} value={section.id}>
                {section.programCode} - semester {section.semesterNumber} ({section.academicYear}) -
                section {section.sectionName}
              </option>
            ))}
          </select>
        )}
      </Field>

      <Button type="submit" loading={saving}>
        Enrol student
      </Button>
    </form>
  )
}

function Th({ children }: { children: React.ReactNode }) {
  return (
    <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase">
      {children}
    </th>
  )
}

function Td({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <td className={`px-4 py-3 text-slate-800 ${className}`}>{children}</td>
}
