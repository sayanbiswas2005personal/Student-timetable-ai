import { Search } from 'lucide-react'
import { useState } from 'react'

import { Button } from '../common/Button'
import { Field } from '../common/Field'

export interface CourseSearchValues {
  query: string
}

interface CourseSearchFormProps {
  onSubmit: (freeText: string) => void
  loading?: boolean
}

/**
 * Free text search for a course, semester and section.
 *
 * The examples are real input shapes the backend understands. The form deliberately does not
 * validate the phrasing: "B.Tech CSE, 5th semester, section D" and "BTCSEAIML 5 D" are both
 * legitimate, and the backend resolves them against verified records and asks the user to choose
 * when a phrase is ambiguous.
 */
export function CourseSearchForm({ onSubmit, loading = false }: CourseSearchFormProps) {
  const [query, setQuery] = useState('')
  const [error, setError] = useState<string | undefined>(undefined)

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    const trimmed = query.trim()
    if (trimmed.length < 3) {
      setError('Enter a course, semester or section to search.')
      return
    }
    setError(undefined)
    onSubmit(trimmed)
  }

  return (
    <form noValidate onSubmit={submit} className="w-full max-w-xl">
      <Field
        label="Course, semester and section"
        error={error}
        hint="For example: B.Tech CSE AI-ML, semester 5, section D"
      >
        {({ id, describedBy, invalid }) => (
          <>
            <div className="flex flex-col gap-2 sm:flex-row">
              <input
                id={id}
                type="search"
                value={query}
                autoComplete="off"
                onChange={(event) => setQuery(event.target.value)}
                placeholder="B.Tech CSE, 5th semester, section D"
                aria-describedby={describedBy}
                aria-invalid={invalid}
                className="field-input"
              />
              <Button type="submit" loading={loading} className="sm:w-40">
                <Search aria-hidden="true" className="size-4" />
                Find section
              </Button>
            </div>
          </>
        )}
      </Field>
    </form>
  )
}