import { useQuery } from '@tanstack/react-query'
import { fetchAuditLog } from '@/api/auditApi'
import { useAuth } from '@/context/AuthContext'
import { reduceToTransactions, filterByOutcome, sortByRiskDesc } from '@/lib/transactions'

const POLL_INTERVAL_MS = 15000
// A shift-based triage tool, not a live ticker - the window needs to survive overnight flags
// into a morning review, not just "recent" like the live feed's 6h window.
const REVIEW_WINDOW_MS = 24 * 60 * 60 * 1000

export function useReviewQueue() {
  const { session } = useAuth()

  const query = useQuery({
    // Distinct from useLiveTransactions's ['audit-log'] key - different window and filter,
    // must not share a cache entry with the live feed.
    queryKey: ['audit-log', 'review'],
    queryFn: () => {
      const from = new Date(Date.now() - REVIEW_WINDOW_MS).toISOString()
      return fetchAuditLog({ from }, session?.token)
    },
    enabled: Boolean(session?.token),
    refetchInterval: POLL_INTERVAL_MS,
  })

  const transactions = query.data
    ? sortByRiskDesc(filterByOutcome(reduceToTransactions(query.data), ['FLAG', 'BLOCK']))
    : []

  return {
    transactions,
    isLoading: query.isLoading,
    isError: query.isError && !query.data,
    error: query.error,
  }
}
