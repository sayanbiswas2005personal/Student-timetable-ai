import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import RequireRole from './RequireRole'
import type { CurrentUser, Role } from '../../types/api'

/**
 * The role gate is a user experience measure, not the security boundary: the API refuses the same
 * requests independently. These tests make sure a staff member is told why an administrator screen
 * is unavailable, instead of landing on a blank page.
 */

const currentUser = { user: null as CurrentUser | null }

vi.mock('../../api/useAuth', () => ({
  useAuth: () => ({
    user: currentUser.user,
    status: 'authenticated',
    isAdmin: currentUser.user?.role === 'ADMIN',
    login: async () => {},
    logout: async () => {},
  }),
}))

function renderGate(role: Role) {
  currentUser.user = {
    id: 1,
    username: 'tester',
    displayName: 'Tester',
    role,
  }

  return render(
    <MemoryRouter initialEntries={['/admin/students']}>
      <Routes>
        <Route element={<RequireRole role="ADMIN" />}>
          <Route path="/admin/students" element={<p>Student administration</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireRole', () => {
  beforeEach(() => {
    currentUser.user = null
  })

  it('lets an administrator through', () => {
    renderGate('ADMIN')
    expect(screen.getByText('Student administration')).toBeInTheDocument()
  })

  it('explains to a staff member that the screen is not available for their role', () => {
    renderGate('STAFF')
    expect(screen.getByText(/not available for your role/i)).toBeInTheDocument()
    expect(screen.queryByText('Student administration')).not.toBeInTheDocument()
  })

  it('says who to ask rather than leaving the user stuck', () => {
    renderGate('STAFF')
    expect(screen.getByText(/ask an administrator/i)).toBeInTheDocument()
  })
})