const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  constructor(status, body) {
    super(`API request failed with status ${status}`)
    this.status = status
    this.body = body
  }
}

let unauthorizedHandler = null

export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}

export async function apiFetch(path, { method = 'GET', body, token, signal } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
    signal,
  })

  if (response.status === 401) {
    unauthorizedHandler?.()
    throw new ApiError(401, await safeJson(response))
  }

  if (!response.ok) {
    throw new ApiError(response.status, await safeJson(response))
  }

  if (response.status === 204) return null
  return safeJson(response)
}

async function safeJson(response) {
  try {
    return await response.json()
  } catch {
    return null
  }
}
