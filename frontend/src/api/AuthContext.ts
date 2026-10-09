import { createContext } from 'react'

import type { CurrentUser } from '../types/api'

/** What every screen can learn about the session without asking the server again. */
export interface AuthContextValue {
  user: CurrentUser | null
  status: 'loading' | 'authenticated' | 'anonymous'
  isAdmin: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
