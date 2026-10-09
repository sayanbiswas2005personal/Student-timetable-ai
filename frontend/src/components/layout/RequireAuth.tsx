import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from '../../api/useAuth'
import { Spinner } from '../common/Spinner'

/**
 * Sends anyone without a session to the login page.
 *
 * The original location is remembered so the user lands back where they were after signing in.
 */
export default function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spinner label="Checking your session" />
      </div>
    )
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }

  return <Outlet />
}
