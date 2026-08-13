import { useQuery } from '@tanstack/react-query'
import { fetchAuditLog } from '@/api/auditApi'
import { useAuth } from '@/context/AuthContext'
import { reduceToTransactions } from '@/lib/transactions'

const POLL_INTERVAL_MS = 60000
// Wide enough to plot a meaningful 24-hour trend; unlike useLiveTransactions' 6h "live feed"
// window, this data is aggregated into hourly buckets, not read row by row, so the extra payload
// size per poll is fine at 60s intervals instead of 5s.
export const TRENDS_WINDOW_MS = 24 * 60 * 60 * 1000

export function useTransactionTrends() {
  const { session } = useAuth()

  const query = useQuery({
    // Array form, not a single 'audit-log-trends' string, so useOverrideDecision's
    // invalidateQueries({ queryKey: ['audit-log'] }) sweeps this up via prefix matching - the same
    // convention useReviewQueue's ['audit-log', 'review'] key already relies on. Without this, an
    // override wouldn't be reflected here until the next 60s poll.
    queryKey: ['audit-log', 'trends'],
    queryFn: () => {
      const from = new Date(Date.now() - TRENDS_WINDOW_MS).toISOString()
      return fetchAuditLog({ from }, session?.token)
    },
    enabled: Boolean(session?.token),
    refetchInterval: POLL_INTERVAL_MS,
  })

  // RECONCILED entries are read separately from the reduced `transactions` view: reconciliation
  // intentionally re-checks decisions 1-7 days old, so a RECONCILED entry frequently lands in this
  // 24h window (its own recordedAt is recent) while the transaction's original CREATED/DECIDED
  // entries do not (see bucketDriftByHour in lib/analytics.js). Reusing the same query here (same
  // queryKey/queryFn as the transactions fetch above) lets react-query share one network request.
  const reconciliationEvents = query.data
    ? query.data.filter((entry) => entry.eventType === 'RECONCILED').map((entry) => entry.payload)
    : []

  return {
    transactions: query.data ? reduceToTransactions(query.data) : [],
    reconciliationEvents,
    isLoading: query.isLoading,
    isError: query.isError && !query.data,
    error: query.error,
  }
}
