import { useQuery } from '@tanstack/react-query'
import { fetchAuditTrail } from '@/api/auditApi'
import { useAuth } from '@/context/AuthContext'

// On-demand and historical (no refetchInterval) - unlike the live feed, a past transaction's
// trail doesn't change once fetched, so there's nothing to poll.
export function useAuditTrail(transactionId) {
  const { session } = useAuth()

  const query = useQuery({
    queryKey: ['audit-trail', transactionId],
    queryFn: () => fetchAuditTrail(transactionId, session?.token),
    enabled: Boolean(session?.token) && Boolean(transactionId),
  })

  const events = query.data
    ? [...query.data].sort((a, b) => new Date(a.recordedAt).getTime() - new Date(b.recordedAt).getTime())
    : []

  return {
    events,
    isLoading: query.isLoading,
    // Same stale-while-error guard as useLiveTransactions/useReviewQueue - a background
    // refetch failure shouldn't blank out an already-loaded trail.
    isError: query.isError && !query.data,
    error: query.error,
  }
}
