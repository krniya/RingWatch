import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
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
  const queryClient = useQueryClient()

  const logout = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY)
    setSession(null)
    // Otherwise a re-login (same tab, same or different analyst) can render the
    // outgoing session's cached feed data before the first refetch resolves.
    queryClient.clear()
  }, [queryClient])

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

  useEffect(() => {
    onUnauthorized(logout)
    return () => onUnauthorized(null)
  }, [logout])

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
