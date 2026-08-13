import { useState } from 'react'
import { motion, useReducedMotion } from 'framer-motion'
import { StatusBadge } from '@/components/feed/StatusBadge'
import { Button } from '@/components/ui/button'
import { OverrideDialog } from './OverrideDialog'
import { formatAmount, formatRiskScore, formatTimestamp } from '@/lib/formatters'
import { rowVariants, staticRowVariants } from '@/lib/motion'
import { useClickableRow } from '@/hooks/useClickableRow'
import { TruncatedIdCell } from '@/components/shared/TruncatedIdCell'

export function ReviewQueueRow({ transaction, onClick }) {
  const prefersReducedMotion = useReducedMotion()
  const rowProps = useClickableRow(transaction, onClick)
  const [overrideOpen, setOverrideOpen] = useState(false)

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
      <TruncatedIdCell value={transaction.transactionId} maxWidth={180} tone="text-text" />
      <TruncatedIdCell value={transaction.senderAccountId} />
      <TruncatedIdCell value={transaction.receiverAccountId} />
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
      <td
        className="px-3 py-2"
        onClick={(event) => event.stopPropagation()}
        onKeyDown={(event) => event.stopPropagation()}
      >
        <Button variant="outline" size="sm" onClick={() => setOverrideOpen(true)}>
          Override
        </Button>
        {overrideOpen && (
          <OverrideDialog transaction={transaction} open={overrideOpen} onOpenChange={setOverrideOpen} />
        )}
      </td>
    </motion.tr>
  )
}
