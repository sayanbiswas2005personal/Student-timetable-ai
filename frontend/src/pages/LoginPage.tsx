import { zodResolver } from '@hookform/resolvers/zod'
import { CalendarClock } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { ApiRequestError } from '../api/client'
import { useAuth } from '../api/useAuth'
import { Alert } from '../components/common/Alert'
import { Button } from '../components/common/Button'
import { Field } from '../components/common/Field'

const schema = z.object({
  username: z.string().trim().min(1, 'Enter your username.').max(60),
  password: z.string().min(1, 'Enter your password.').max(200),
})

type Values = z.infer<typeof schema>

/**
 * Sign in.
 *
 * There is no registration link on purpose: accounts are created by an administrator, so a
 * password reset or self sign up flow would be a way for anyone to reach student data.
 */
export default function LoginPage() {
  const { login, status } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<ApiRequestError | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { username: '', password: '' } })

  if (status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from ?? '/'} replace />
  }

  const onSubmit = handleSubmit(async (values) => {
    setError(null)
    try {
      await login(values.username, values.password)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/', { replace: true })
    } catch (cause) {
      setError(cause as ApiRequestError)
    }
  })

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-6 flex items-center justify-center gap-3">
          <span
            aria-hidden="true"
            className="flex size-11 items-center justify-center rounded-xl bg-brand-600 text-white"
          >
            <CalendarClock className="size-6" />
          </span>
          <div>
            <h1 className="text-lg font-semibold text-slate-900">College Timetable Lookup</h1>
            <p className="text-sm text-slate-600">Staff and administrator sign in</p>
          </div>
        </div>

        <div className="card p-6">
          <form noValidate onSubmit={onSubmit} className="space-y-4">
            {error && (
              <Alert tone="error" title="Could not sign in">
                {error.message}
                {error.isRateLimited && (
                  <span className="mt-1 block">Wait a moment before trying again.</span>
                )}
              </Alert>
            )}

            <Field label="Username" error={errors.username?.message} required>
              {({ id, describedBy, invalid }) => (
                <input
                  id={id}
                  type="text"
                  autoComplete="username"
                  autoFocus
                  aria-describedby={describedBy}
                  aria-invalid={invalid}
                  className="field-input"
                  {...register('username')}
                />
              )}
            </Field>

            <Field label="Password" error={errors.password?.message} required>
              {({ id, describedBy, invalid }) => (
                <input
                  id={id}
                  type="password"
                  autoComplete="current-password"
                  aria-describedby={describedBy}
                  aria-invalid={invalid}
                  className="field-input"
                  {...register('password')}
                />
              )}
            </Field>

            <Button type="submit" loading={isSubmitting} className="w-full">
              Sign in
            </Button>
          </form>
        </div>

        <p className="mt-4 text-center text-xs text-slate-500">
          Accounts are issued by the college. Contact an administrator if you cannot sign in.
        </p>
      </div>
    </div>
  )
}
