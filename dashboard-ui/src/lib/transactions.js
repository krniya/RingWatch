// Flattens the audit log (one entry per lifecycle event) into one row per
// transaction, keyed on its most recent state. Each event's payload is a
// superset of the previous one (Decision extends Scored extends Enriched
// extends Raw), so folding payloads chronologically accumulates every field
// a transaction has picked up so far.
export function reduceToTransactions(auditLog) {
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

export function filterByOutcome(transactions, outcomes) {
  return transactions.filter((transaction) => outcomes.includes(transaction.outcome))
}

export function matchesQuery(transaction, query) {
  if (!query) return true
  const needle = query.trim().toLowerCase()
  if (!needle) return true
  return [transaction.transactionId, transaction.senderAccountId, transaction.receiverAccountId]
    .filter(Boolean)
    .some((field) => field.toLowerCase().includes(needle))
}

export function sortByRiskDesc(transactions) {
  return [...transactions].sort((a, b) => {
    const riskDiff = (b.riskScore ?? 0) - (a.riskScore ?? 0)
    if (riskDiff !== 0) return riskDiff
    return new Date(b.lastEventAt).getTime() - new Date(a.lastEventAt).getTime()
  })
}
