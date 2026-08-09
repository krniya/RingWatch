import { useQuery } from '@tanstack/react-query'
import { fetchAuditLog } from '@/api/auditApi'
import { useAuth } from '@/context/AuthContext'
import { reduceToTransactions } from '@/lib/transactions'

const POLL_INTERVAL_MS = 5000
// "Live feed" means recent activity, not the full audit history - the backend has no
// pagination, so bounding the window keeps payload size and client-side folding flat
// as total transaction volume grows over the system's lifetime.
const FEED_WINDOW_MS = 6 * 60 * 60 * 1000

// Polling today; swapping to a WebSocket/SSE push source later only means
// changing what's inside this hook; consumers only see {transactions,
// isLoading, isError, error}.
export function useLiveTransactions() {
  const { session } = useAuth()

  const query = useQuery({
    queryKey: ['audit-log'],
    queryFn: () => {
      const from = new Date(Date.now() - FEED_WINDOW_MS).toISOString()
      return fetchAuditLog({ from }, session?.token)
    },
    enabled: Boolean(session?.token),
    refetchInterval: POLL_INTERVAL_MS,
  })

  return {
    transactions: query.data ? reduceToTransactions(query.data) : [],
    // A transient poll failure shouldn't blank out a feed that already has good data -
    // only surface the error state when there's nothing to show in its place.
    isLoading: query.isLoading,
    isError: query.isError && !query.data,
    error: query.error,
  }
}
