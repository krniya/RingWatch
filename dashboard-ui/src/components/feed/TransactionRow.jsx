import { StatusBadge } from './StatusBadge'
import { formatAmount, formatRiskScore, formatTimestamp, shortenId } from '@/lib/formatters'

export function TransactionRow({ transaction }) {
  return (
    <tr className="border-b border-border hover:bg-panel-raised">
      <td className="px-3 py-2 font-mono text-xs text-text-muted">
        {formatTimestamp(transaction.lastEventAt)}
      </td>
      <td className="px-3 py-2 font-mono text-xs text-text">{shortenId(transaction.transactionId)}</td>
      <td className="px-3 py-2 font-mono text-xs text-text-muted">
        {shortenId(transaction.senderAccountId)}
      </td>
      <td className="px-3 py-2 font-mono text-xs text-text-muted">
        {shortenId(transaction.receiverAccountId)}
      </td>
      <td className="px-3 py-2 text-right font-mono text-xs text-text">
        {formatAmount(transaction.amount, transaction.currency)}
      </td>
      <td className="px-3 py-2 text-right font-mono text-xs text-text-muted">
        {formatRiskScore(transaction.riskScore)}
      </td>
      <td className="px-3 py-2">
        <StatusBadge outcome={transaction.outcome} />
      </td>
    </tr>
  )
}
