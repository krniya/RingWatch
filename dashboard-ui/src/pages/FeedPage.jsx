import { useLiveTransactions } from '@/hooks/useLiveTransactions'
import { TransactionFeedTable } from '@/components/feed/TransactionFeedTable'

export function FeedPage() {
  const { transactions, isLoading, isError } = useLiveTransactions()

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-border px-6 py-4">
        <h1 className="text-lg font-medium">Live Transaction Feed</h1>
        <p className="font-mono text-xs text-text-muted">Polling every 5s via /audit</p>
      </div>
      <div className="flex-1 overflow-y-auto">
        <TransactionFeedTable transactions={transactions} isLoading={isLoading} isError={isError} />
      </div>
    </div>
  )
}
