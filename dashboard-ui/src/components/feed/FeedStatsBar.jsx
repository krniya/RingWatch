import { useMemo } from 'react'
import { Skeleton } from '@/components/ui/skeleton'
import { useCountUp } from '@/hooks/useCountUp'
import { formatAmount } from '@/lib/formatters'

const TILES = [
  { key: 'APPROVE', label: 'Approved', tone: 'text-approve' },
  { key: 'FLAG', label: 'Flagged', tone: 'text-flag' },
  { key: 'BLOCK', label: 'Blocked', tone: 'text-block' },
  { key: 'PENDING', label: 'Pending', tone: 'text-text-muted' },
]

function StatTile({ label, value, tone }) {
  const animated = useCountUp(value)
  return (
    <div className="flex-1 rounded-panel border border-border bg-panel px-4 py-3 shadow-elevation-2">
      <p className="font-mono text-[11px] tracking-wide text-text-faint uppercase">{label}</p>
      <p className={`font-mono text-xl ${tone}`}>{animated}</p>
    </div>
  )
}

export function FeedStatsBar({ transactions, isLoading }) {
  const { counts, volume } = useMemo(() => {
    const nextCounts = { APPROVE: 0, FLAG: 0, BLOCK: 0, PENDING: 0 }
    let totalVolume = 0
    for (const transaction of transactions) {
      const outcome = transaction.outcome ?? 'PENDING'
      nextCounts[outcome] = (nextCounts[outcome] ?? 0) + 1
      // Assumes a single currency across the window (true for every transaction this app has
      // ever produced) - a genuinely multi-currency feed would need per-currency subtotals
      // instead of one summed number.
      totalVolume += Number(transaction.amount ?? 0)
    }
    return { counts: nextCounts, volume: totalVolume }
  }, [transactions])

  if (isLoading) {
    return (
      <div className="flex gap-3 px-6 py-4">
        {[...Array(5)].map((_, index) => (
          <Skeleton key={index} className="h-16 flex-1 rounded-panel" />
        ))}
      </div>
    )
  }

  return (
    <div className="flex gap-3 px-6 py-4">
      {TILES.map((tile) => (
        <StatTile key={tile.key} label={tile.label} value={counts[tile.key]} tone={tile.tone} />
      ))}
      <div className="flex-1 rounded-panel border border-border bg-panel px-4 py-3 shadow-elevation-2">
        <p className="font-mono text-[11px] tracking-wide text-text-faint uppercase">Volume</p>
        <p className="font-mono text-xl text-text">{formatAmount(volume, 'USD')}</p>
      </div>
    </div>
  )
}
