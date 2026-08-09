import { TransactionRow } from './TransactionRow'

const COLUMNS = ['Time', 'Transaction', 'Sender', 'Receiver', 'Amount', 'Risk', 'Status']

export function TransactionFeedTable({ transactions, isLoading, isError }) {
  if (isError) {
    return <p className="p-6 font-mono text-sm text-block">Failed to load the transaction feed.</p>
  }

  if (isLoading) {
    return <p className="p-6 font-mono text-sm text-text-muted">Loading feed…</p>
  }

  if (transactions.length === 0) {
    return <p className="p-6 font-mono text-sm text-text-muted">No transactions yet.</p>
  }

  return (
    <table className="w-full border-collapse">
      <thead>
        <tr className="border-b border-border-strong text-left">
          {COLUMNS.map((column) => (
            <th key={column} className="px-3 py-2 font-mono text-[11px] tracking-wide text-text-faint uppercase">
              {column}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {transactions.map((transaction) => (
          <TransactionRow key={transaction.transactionId} transaction={transaction} />
        ))}
      </tbody>
    </table>
  )
}
