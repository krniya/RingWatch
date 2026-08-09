import { motion, useReducedMotion } from 'framer-motion'
import { ShieldAlert } from 'lucide-react'
import { ReviewQueueRow } from './ReviewQueueRow'
import { Skeleton } from '@/components/ui/skeleton'
import { EmptyState } from '@/components/shared/EmptyState'
import { rowContainerVariants, staticRowVariants } from '@/lib/motion'

const COLUMNS = ['Time', 'Transaction', 'Sender', 'Receiver', 'Amount', 'Risk', 'Reason', 'Status', 'Override']

function ReviewTableSkeleton() {
  return (
    <div className="space-y-2 p-3">
      {[...Array(4)].map((_, index) => (
        <Skeleton key={index} className="h-8 w-full" />
      ))}
    </div>
  )
}

export function ReviewQueueTable({ transactions, isLoading, isError, onRowClick }) {
  const prefersReducedMotion = useReducedMotion()

  if (isError) {
    return <p className="p-6 font-mono text-sm text-block">Failed to load the review queue.</p>
  }

  if (isLoading) {
    return <ReviewTableSkeleton />
  }

  if (transactions.length === 0) {
    return (
      <EmptyState
        icon={ShieldAlert}
        heading="Nothing to review"
        description="Flagged and blocked transactions from the last 24h will appear here."
      />
    )
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
      <motion.tbody
        initial="hidden"
        animate="visible"
        variants={prefersReducedMotion ? staticRowVariants : rowContainerVariants}
      >
        {transactions.map((transaction) => (
          <ReviewQueueRow key={transaction.transactionId} transaction={transaction} onClick={onRowClick} />
        ))}
      </motion.tbody>
    </table>
  )
}
