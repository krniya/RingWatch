import { useQuery } from '@tanstack/react-query'
import { fetchAuditLog } from '@/api/auditApi'
import { useAuth } from '@/context/AuthContext'
import { reduceToTransactions } from '@/lib/transactions'

const POLL_INTERVAL_MS = 60000

// windowMs is unlike useLiveTransactions' fixed 6h "live feed" window: this data is aggregated
// into hourly/daily buckets, not read row by row, so the extra payload size per poll (even at the
// 30-day range) is fine at 60s intervals instead of 5s.
export function useTransactionTrends(windowMs) {
  const { session } = useAuth()

  const query = useQuery({
    // Array form, not a single 'audit-log-trends' string, so useOverrideDecision's
    // invalidateQueries({ queryKey: ['audit-log'] }) sweeps this up via prefix matching - the same
    // convention useReviewQueue's ['audit-log', 'review'] key already relies on. windowMs is part
    // of the key so switching the selected range refetches instead of reusing a shorter window's
    // cached page. Without the ['audit-log', ...] prefix, an override wouldn't be reflected here
    // until the next 60s poll.
    queryKey: ['audit-log', 'trends', windowMs],
    queryFn: () => {
      const from = new Date(Date.now() - windowMs).toISOString()
      return fetchAuditLog({ from }, session?.token)
    },
    enabled: Boolean(session?.token),
    refetchInterval: POLL_INTERVAL_MS,
  })

  // RECONCILED entries are read separately from the reduced `transactions` view: reconciliation
  // intentionally re-checks decisions 1-7 days old, so a RECONCILED entry frequently lands inside
  // the selected window (its own recordedAt is recent) while the transaction's original
  // CREATED/DECIDED entries do not (see bucketDriftByPeriod in lib/analytics.js). Reusing the same
  // query here (same queryKey/queryFn as the transactions fetch above) lets react-query share one
  // network request.
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
