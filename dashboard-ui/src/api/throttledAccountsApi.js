import { apiFetch } from './client'

/**
 * FR17's custom actuator endpoint, on api-gateway itself rather than behind a Gateway Route -
 * unauthenticated by design (see ThrottledAccountsEndpoint's Javadoc), but the token is passed
 * anyway for consistency with every other API call; the endpoint just ignores it.
 * @returns {Promise<Array<{key: string, count: number}>>}
 */
export function fetchThrottledAccounts(token) {
  return apiFetch('/actuator/throttledAccounts', { token })
}
