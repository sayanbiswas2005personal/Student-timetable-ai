import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { ApiRequestError, primeCsrfCookie } from './client'
import { AuthContext, type AuthContextValue } from './AuthContext'
import { authApi } from './endpoints'
import type { CurrentUser } from '../types/api'

/**
 * Holds the signed in account.
 *
 * The session lives in an HttpOnly cookie, so the browser never holds the credential itself: this
 * provider only remembers who is signed in, and asks the server on every page load. That is why
 * a reload is the source of truth rather than anything cached here.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [status, setStatus] = useState<AuthContextValue['status']>('loading')

  useEffect(() => {
    let cancelled = false
    void (async () => {
      await primeCsrfCookie()
      try {
        const current = await authApi.me()
        if (!cancelled) {
          setUser(current)
          setStatus('authenticated')
        }
      } catch {
        if (!cancelled) {
          setUser(null)
          setStatus('anonymous')
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const result = await authApi.login(username, password)
    setUser(result.user)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch (error) {
      // A failed logout must still clear the local session; the cookie is the server's business.
      if (!(error instanceof ApiRequestError)) {
        throw error
      }
    }
    setUser(null)
    setStatus('anonymous')
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, status, isAdmin: user?.role === 'ADMIN', login, logout }),
    [user, status, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
