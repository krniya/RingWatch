import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'

export function LoginPage() {
  const { login, isAuthenticated } = useAuth()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to={location.state?.from ?? '/overview'} replace />
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await login(username, password)
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 401
          ? 'Invalid username or password.'
          : 'Sign-in failed. Please try again.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex h-full items-center justify-center bg-bg">
      <form onSubmit={handleSubmit} className="w-80 border border-border bg-panel p-6">
        <p className="mb-1 font-mono text-xs tracking-wider text-text-faint uppercase">RingWatch</p>
        <h1 className="mb-6 text-lg font-medium">Analyst sign-in</h1>

        <label className="mb-1 block text-xs text-text-muted" htmlFor="username">
          Username
        </label>
        <input
          id="username"
          className="mb-4 w-full border border-border-strong bg-panel-raised px-3 py-2 font-mono text-sm text-text outline-none focus:border-accent"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          autoComplete="username"
          required
        />

        <label className="mb-1 block text-xs text-text-muted" htmlFor="password">
          Password
        </label>
        <input
          id="password"
          type="password"
          className="mb-6 w-full border border-border-strong bg-panel-raised px-3 py-2 font-mono text-sm text-text outline-none focus:border-accent"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
          required
        />

        {error && <p className="mb-4 font-mono text-xs text-block">{error}</p>}

        <Button type="submit" className="w-full justify-center" disabled={isSubmitting}>
          {isSubmitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </div>
  )
}
