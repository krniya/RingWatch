import { useQuery } from '@tanstack/react-query'
import { fetchAuditLog } from '@/api/auditApi'
import { useAuth } from '@/context/AuthContext'

const POLL_INTERVAL_MS = 5000

// Flattens the audit log (one entry per lifecycle event) into one row per
// transaction, keyed on its most recent state. Each event's payload is a
// superset of the previous one (Decision extends Scored extends Enriched
// extends Raw), so folding payloads chronologically accumulates every field
// a transaction has picked up so far.
function reduceToTransactions(auditLog) {
  const byTransactionId = new Map()

  const chronological = [...auditLog].sort(
    (a, b) => new Date(a.recordedAt).getTime() - new Date(b.recordedAt).getTime(),
  )

  for (const entry of chronological) {
    const existing = byTransactionId.get(entry.transactionId) ?? {}
    byTransactionId.set(entry.transactionId, {
      ...existing,
      ...entry.payload,
      transactionId: entry.transactionId,
      lastEventType: entry.eventType,
      lastEventAt: entry.recordedAt,
    })
  }

  return [...byTransactionId.values()].sort(
    (a, b) => new Date(b.lastEventAt).getTime() - new Date(a.lastEventAt).getTime(),
  )
}

// Polling today; swapping to a WebSocket/SSE push source later only means
// changing what's inside this hook; consumers only see {transactions,
// isLoading, isError, error}.
export function useLiveTransactions() {
  const { session } = useAuth()

  const query = useQuery({
    queryKey: ['audit-log'],
    queryFn: () => fetchAuditLog({}, session?.token),
    enabled: Boolean(session?.token),
    refetchInterval: POLL_INTERVAL_MS,
  })

  return {
    transactions: query.data ? reduceToTransactions(query.data) : [],
    isLoading: query.isLoading,
    isError: query.isError,
    error: query.error,
  }
}
