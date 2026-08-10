import { apiFetch } from './client'

/**
 * @returns {Promise<Array<{ringId: string, memberAccountIds: string[], sharedAttributes: string, aiExplanation: string, detectedAt: string}>>}
 */
export function fetchFraudRings(token) {
  return apiFetch('/fraud-rings', { token })
}
