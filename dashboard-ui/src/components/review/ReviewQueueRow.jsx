import { motion, useReducedMotion } from 'framer-motion'
import { StatusBadge } from '@/components/feed/StatusBadge'
import { Button } from '@/components/ui/button'
import { formatAmount, formatRiskScore, formatTimestamp, shortenId } from '@/lib/formatters'
import { rowVariants, staticRowVariants } from '@/lib/motion'
import { useClickableRow } from '@/hooks/useClickableRow'

export function ReviewQueueRow({ transaction, onClick }) {
  const prefersReducedMotion = useReducedMotion()
  const rowProps = useClickableRow(transaction, onClick)

  return (
    <motion.tr
      variants={prefersReducedMotion ? staticRowVariants : rowVariants}
      {...rowProps}
      className={`border-b border-border transition-colors hover:bg-panel-raised ${
        onClick ? 'cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-accent' : ''
      }`}
    >
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
      <td className="max-w-xs truncate px-3 py-2 text-xs text-text-muted" title={transaction.reason ?? ''}>
        {transaction.reason ?? '—'}
      </td>
      <td className="px-3 py-2">
        <StatusBadge outcome={transaction.outcome} />
      </td>
      <td className="px-3 py-2">
        <Button variant="outline" size="sm" disabled title="Coming in a follow-up slice">
          Override
        </Button>
      </td>
    </motion.tr>
  )
}
