import { useMemo, useState } from 'react'
import { useReviewQueue } from '@/hooks/useReviewQueue'
import { ReviewQueueTable } from '@/components/review/ReviewQueueTable'
import { PageHeader } from '@/components/shared/PageHeader'
import { SearchInput } from '@/components/shared/SearchInput'
import { AuditTrailDrawer } from '@/components/audit/AuditTrailDrawer'
import { matchesQuery } from '@/lib/transactions'

export function ReviewQueuePage() {
  const { transactions, isLoading, isError } = useReviewQueue()
  const [query, setQuery] = useState('')
  const [selectedTransactionId, setSelectedTransactionId] = useState(null)

  const filtered = useMemo(
    () => transactions.filter((transaction) => matchesQuery(transaction, query)),
    [transactions, query],
  )

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-y-auto">
        <PageHeader title="Review Queue" subtitle="Flagged and blocked transactions — last 24h">
          <SearchInput value={query} onChange={setQuery} placeholder="Search transaction or account…" />
        </PageHeader>

        <ReviewQueueTable
          transactions={filtered}
          isLoading={isLoading}
          isError={isError}
          onRowClick={(transaction) => setSelectedTransactionId(transaction.transactionId)}
        />
      </div>

      <AuditTrailDrawer
        transactionId={selectedTransactionId}
        open={Boolean(selectedTransactionId)}
        onOpenChange={(open) => {
          if (!open) setSelectedTransactionId(null)
        }}
      />
    </div>
  )
}
