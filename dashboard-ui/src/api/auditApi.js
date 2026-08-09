import { apiFetch } from './client'

/**
 * @param {{userId?: string, from?: string, to?: string}} filters
 * @returns {Promise<Array<{eventId: string, transactionId: string, eventType: 'CREATED'|'SCORED'|'DECIDED'|'OVERRIDDEN', payload: unknown, userId: string|null, recordedAt: string}>>}
 */
export function fetchAuditLog(filters = {}, token) {
  const params = new URLSearchParams()
  if (filters.userId) params.set('userId', filters.userId)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)

  const query = params.toString()
  return apiFetch(`/audit${query ? `?${query}` : ''}`, { token })
}
