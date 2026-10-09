import { Outlet } from 'react-router-dom'

import { useAuth } from '../../api/useAuth'
import { EmptyState } from '../common/EmptyState'
import type { Role } from '../../types/api'

/**
 * Restricts a branch of the routes to one role.
 *
 * A staff member who reaches an administrator URL by typing it gets an explanation rather than a
 * blank page. The API enforces the same rule independently; this is a user experience measure, not
 * the security boundary.
 */
export default function RequireRole({ role }: { role: Role }) {
  const { user } = useAuth()

  if (user?.role !== role) {
    return (
      <EmptyState
        title="Not available for your role"
        description={`This screen is limited to ${role.toLowerCase()} accounts. Ask an administrator if you need access.`}
      />
    )
  }

  return <Outlet />
}
