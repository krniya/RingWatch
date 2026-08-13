import { useMemo } from 'react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import { OutcomeTrendChart } from '@/components/charts/OutcomeTrendChart'
import { VolumeTrendChart } from '@/components/charts/VolumeTrendChart'
import { ThrottledAccountsLeaderboard } from '@/components/charts/ThrottledAccountsLeaderboard'
import { RingDetectionsChart } from '@/components/charts/RingDetectionsChart'
import { ReconciliationDriftChart } from '@/components/charts/ReconciliationDriftChart'
import { OutcomeCompositionPie } from '@/components/charts/OutcomeCompositionPie'
import { useTransactionTrends } from '@/hooks/useTransactionTrends'
import { useThrottledAccounts } from '@/hooks/useThrottledAccounts'
import { useFraudRings } from '@/hooks/useFraudRings'
import {
  bucketOutcomesByHour,
  bucketVolumeByHour,
  bucketRingDetectionsByHour,
  bucketDriftByHour,
  outcomeComposition,
} from '@/lib/analytics'

const TILES = [
  { key: 'total', label: 'Transactions (24h)', tone: 'text-text' },
  { key: 'blockRate', label: 'Block Rate', tone: 'text-block' },
  { key: 'driftRate', label: 'Drift Rate', tone: 'text-flag' },
  { key: 'throttled', label: 'Top Throttled Keys', tone: 'text-flag' },
  { key: 'rings', label: 'Rings Detected', tone: 'text-block' },
]

function StatTile({ label, value, tone, isLoading }) {
  return (
    <div className="flex-1 rounded-panel border border-border bg-panel px-3 py-2 shadow-elevation-2">
      <p className="font-mono text-[11px] tracking-wide text-text-faint uppercase">{label}</p>
      {isLoading ? (
        <Skeleton className="mt-1 h-5 w-16" />
      ) : (
        <p className={`font-mono text-lg ${tone}`}>{value}</p>
      )}
    </div>
  )
}

export function OverviewPage() {
  const {
    transactions,
    reconciliationEvents,
    isLoading: trendsLoading,
    isError: trendsError,
  } = useTransactionTrends()
  const { accounts, isLoading: throttledLoading, isError: throttledError } = useThrottledAccounts()
  const { detections, isLoading: ringsLoading, isError: ringsError } = useFraudRings()

  const outcomeData = useMemo(() => bucketOutcomesByHour(transactions), [transactions])
  const volumeData = useMemo(() => bucketVolumeByHour(transactions), [transactions])
  const ringData = useMemo(() => bucketRingDetectionsByHour(detections), [detections])
  const driftData = useMemo(() => bucketDriftByHour(reconciliationEvents), [reconciliationEvents])
  const compositionData = useMemo(() => outcomeComposition(transactions), [transactions])

  // reduceToTransactions groups every audit entry by transactionId, including RECONCILED entries
  // whose original CREATED/DECIDED entries fall outside this 24h window (reconciliation-service
  // intentionally re-checks decisions 1-7 days old) - that produces a phantom "transaction" row
  // with no `amount` (a field only CREATED's payload sets). Excluding those keeps the count and
  // block-rate denominator to transactions actually created in this window.
  const realTransactions = useMemo(() => transactions.filter((t) => t.amount !== undefined), [transactions])
  const blocked = realTransactions.filter((t) => t.outcome === 'BLOCK').length
  const blockRate =
    realTransactions.length > 0 ? `${((blocked / realTransactions.length) * 100).toFixed(1)}%` : '—'

  const drifted = reconciliationEvents.filter((e) => e.drifted).length
  const driftRate =
    reconciliationEvents.length > 0 ? `${((drifted / reconciliationEvents.length) * 100).toFixed(1)}%` : '—'

  const values = {
    total: realTransactions.length,
    blockRate,
    driftRate,
    throttled: accounts.length,
    rings: detections.length,
  }

  const isLoadingByTile = {
    total: trendsLoading,
    blockRate: trendsLoading,
    driftRate: trendsLoading,
    throttled: throttledLoading,
    rings: ringsLoading,
  }

  const errors = [
    trendsError && 'transaction trends',
    throttledError && 'throttled accounts',
    ringsError && 'fraud rings',
  ].filter(Boolean)

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <PageHeader title="Overview" subtitle="Trends across the last 24 hours" />

      {errors.length > 0 && (
        <p className="px-6 pt-2 font-mono text-xs text-block">
          Failed to load: {errors.join(', ')}. Showing the last known data where available.
        </p>
      )}

      <div className="flex gap-3 px-6 py-3">
        {TILES.map((tile) => (
          <StatTile
            key={tile.key}
            label={tile.label}
            value={values[tile.key]}
            tone={tile.tone}
            isLoading={isLoadingByTile[tile.key]}
          />
        ))}
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-2 grid-rows-3 gap-3 px-6 pb-4">
        <OutcomeTrendChart data={outcomeData} isLoading={trendsLoading} />
        <VolumeTrendChart data={volumeData} isLoading={trendsLoading} />
        <ThrottledAccountsLeaderboard accounts={accounts} isLoading={throttledLoading} />
        <RingDetectionsChart data={ringData} isLoading={ringsLoading} />
        <ReconciliationDriftChart data={driftData} isLoading={trendsLoading} />
        <OutcomeCompositionPie data={compositionData} isLoading={trendsLoading} />
      </div>
    </div>
  )
}
