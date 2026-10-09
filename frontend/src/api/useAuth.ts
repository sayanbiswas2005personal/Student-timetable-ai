import { useContext } from 'react'

import { AuthContext, type AuthContextValue } from './AuthContext'

/** Access to the signed in account. Throws if used outside {@link AuthContext}. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}