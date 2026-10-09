import { useId } from 'react'
import type { ReactNode } from 'react'

interface FieldProps {
  label: string
  error?: string
  hint?: string
  required?: boolean
  children: (props: { id: string; describedBy: string | undefined; invalid: boolean }) => ReactNode
}

/**
 * Wires a label, an error message and a hint to one input with the correct aria attributes.
 *
 * Using a render prop keeps the markup in the caller while the ids and the wiring stay here, so
 * every form in the app is labelled the same way.
 */
export function Field({ label, error, hint, required, children }: FieldProps) {
  const id = useId()
  const errorId = `${id}-error`
  const hintId = `${id}-hint`
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ') || undefined

  return (
    <div>
      <label htmlFor={id} className="field-label">
        {label}
        {required && (
          <span className="ml-0.5 text-red-700" aria-hidden="true">
            *
          </span>
        )}
        {required && <span className="sr-only"> (required)</span>}
      </label>
      {children({ id, describedBy, invalid: Boolean(error) })}
      {hint && (
        <p id={hintId} className="mt-1.5 text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
