import { apiFetch } from './client'

/**
 * @param {{outcome: 'APPROVE'|'FLAG'|'BLOCK', reason: string}} override
 * @returns {Promise<{transactionId: string, outcome: string, reason: string, overriddenBy: string, overrideReason: string, createdAt: string}>}
 */
export function overrideDecision(transactionId, override, token) {
  return apiFetch(`/transactions/${transactionId}/override`, { method: 'POST', body: override, token })
}
