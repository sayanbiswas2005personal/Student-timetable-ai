import { zodResolver } from '@hookform/resolvers/zod'
import { Search } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '../common/Button'
import { Field } from '../common/Field'

const schema = z.object({
  query: z
    .string()
    .trim()
    .min(3, 'Enter at least 3 characters.')
    .max(120, 'That is longer than any course description needs to be.'),
})

export type StudentSearchValues = z.infer<typeof schema>

interface RegistrationSearchFormProps {
  onSubmit: (registrationNumber: string) => void
  loading?: boolean
  defaultValue?: string
  /** Labels the field differently on the dashboard, where two forms share the screen. */
  variant?: 'compact' | 'full'
}

/**
 * Registration number search.
 *
 * The form only checks that something plausible was typed; every decision about what it means is
 * made by the backend against verified records, never guessed here.
 */
export function RegistrationSearchForm({
  onSubmit,
  loading = false,
  defaultValue = '',
  variant = 'full',
}: RegistrationSearchFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<StudentSearchValues>({
    resolver: zodResolver(schema),
    defaultValues: { query: defaultValue },
  })

  return (
    <form
      noValidate
      onSubmit={handleSubmit((values) => onSubmit(values.query.trim()))}
      className={variant === 'compact' ? 'w-full' : 'w-full max-w-xl'}
    >
      <Field label="Registration number" error={errors.query?.message} required>
        {({ id, describedBy, invalid }) => (
          <>
            <div className="flex flex-col gap-2 sm:flex-row">
              <input
                id={id}
                type="search"
                autoComplete="off"
                spellCheck={false}
                placeholder="UG/02/BTCSEAIML/2023/024"
                aria-describedby={describedBy}
                aria-invalid={invalid}
                className="field-input font-mono"
                {...register('query')}
              />
              <Button type="submit" loading={loading || isSubmitting} className="sm:w-40">
                <Search aria-hidden="true" className="size-4" />
                Find student
              </Button>
            </div>
          </>
        )}
      </Field>
    </form>
  )
}