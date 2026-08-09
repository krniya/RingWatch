import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { fetchCurrentAccount, login as loginRequest } from '@/api/authApi'
import { onUnauthorized } from '@/api/client'

const STORAGE_KEY = 'ringwatch.session'

const AuthContext = createContext(null)

function readStoredSession() {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    const session = JSON.parse(raw)
    return session.expiresAt > Date.now() ? session : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(readStoredSession)

  const logout = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY)
    setSession(null)
  }, [])

  const login = useCallback(async (username, password) => {
    const { token, expiresInSeconds } = await loginRequest(username, password)
    const account = await fetchCurrentAccount(token)
    const next = {
      token,
      expiresAt: Date.now() + expiresInSeconds * 1000,
      username: account.username,
      role: account.role,
    }
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    setSession(next)
    return next
  }, [])

  useEffect(() => onUnauthorized(logout), [logout])

  const value = useMemo(
    () => ({
      session,
      isAuthenticated: Boolean(session),
      login,
      logout,
    }),
    [session, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
