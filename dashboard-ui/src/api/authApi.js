import { apiFetch } from './client'

/** @returns {Promise<{token: string, expiresInSeconds: number}>} */
export function login(username, password) {
  return apiFetch('/auth/login', { method: 'POST', body: { username, password } })
}

/** @returns {Promise<{id: string, username: string, role: 'ADMIN'|'ANALYST', createdAt: string}>} */
export function fetchCurrentAccount(token) {
  return apiFetch('/auth/me', { token })
}
