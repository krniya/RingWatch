import { useMemo, useState } from 'react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import { OutcomeTrendChart } from '@/components/charts/OutcomeTrendChart'
import { VolumeTrendChart } from '@/components/charts/VolumeTrendChart'
import { ThrottledAccountsLeaderboard } from '@/components/charts/ThrottledAccountsLeaderboard'
import { RingDetectionsChart } from '@/components/charts/RingDetectionsChart'
import { ReconciliationDriftChart } from '@/components/charts/ReconciliationDriftChart'
import { OutcomeCompositionPie } from '@/components/charts/OutcomeCompositionPie'
import { TimeRangeSelector } from '@/components/charts/TimeRangeSelector'
import { useTransactionTrends } from '@/hooks/useTransactionTrends'
import { useThrottledAccounts } from '@/hooks/useThrottledAccounts'
import { useFraudRings } from '@/hooks/useFraudRings'
import {
  bucketOutcomesByPeriod,
  bucketVolumeByPeriod,
  bucketRingDetectionsByPeriod,
  bucketDriftByPeriod,
  outcomeComposition,
  periodUnitFor,
} from '@/lib/analytics'
import { DEFAULT_TIME_RANGE_KEY, timeRangeFor } from '@/lib/timeRanges'

const TILES = [
  { key: 'total', label: 'Transactions', tone: 'text-text' },
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
  const [rangeKey, setRangeKey] = useState(DEFAULT_TIME_RANGE_KEY)
  const range = timeRangeFor(rangeKey)
  const periodUnit = periodUnitFor(range.windowMs)

  const {
    transactions,
    reconciliationEvents,
    isLoading: trendsLoading,
    isError: trendsError,
  } = useTransactionTrends(range.windowMs)
  const { accounts, isLoading: throttledLoading, isError: throttledError } = useThrottledAccounts()
  const { detections, isLoading: ringsLoading, isError: ringsError } = useFraudRings()

  const outcomeData = useMemo(() => bucketOutcomesByPeriod(transactions, range.windowMs), [transactions, range.windowMs])
  const volumeData = useMemo(() => bucketVolumeByPeriod(transactions, range.windowMs), [transactions, range.windowMs])
  const ringData = useMemo(() => bucketRingDetectionsByPeriod(detections, range.windowMs), [detections, range.windowMs])
  const driftData = useMemo(() => bucketDriftByPeriod(reconciliationEvents, range.windowMs), [reconciliationEvents, range.windowMs])
  const compositionData = useMemo(() => outcomeComposition(transactions), [transactions])

  // reduceToTransactions groups every audit entry by transactionId, including RECONCILED entries
  // whose original CREATED/DECIDED entries fall outside the selected window (reconciliation-service
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

  // detections is a live, unwindowed feed (useFraudRings has no `from` param), so the tile - like
  // every other one on this row - filters to the selected range instead of showing all-time count.
  const ringsInRange = useMemo(() => {
    const cutoff = Date.now() - range.windowMs
    return detections.filter((d) => d.detectedAt && new Date(d.detectedAt).getTime() >= cutoff).length
  }, [detections, range.windowMs])

  const values = {
    total: realTransactions.length,
    blockRate,
    driftRate,
    throttled: accounts.length,
    rings: ringsInRange,
  }

  const isLoadingByTile = {
    total: trendsLoading,
    blockRate: trendsLoading,
    driftRate: trendsLoading,
    throttled: throttledLoading,
    rings: ringsLoading,
  }

  const labelsByTile = {
    total: `Transactions (${range.label})`,
    blockRate: `Block Rate (${range.label})`,
    driftRate: `Drift Rate (${range.label})`,
    rings: `Rings Detected (${range.label})`,
  }

  const errors = [
    trendsError && 'transaction trends',
    throttledError && 'throttled accounts',
    ringsError && 'fraud rings',
  ].filter(Boolean)

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <PageHeader title="Overview" subtitle={`Trends across the ${range.rangeLabel}`}>
        <TimeRangeSelector value={rangeKey} onChange={setRangeKey} />
      </PageHeader>

      {errors.length > 0 && (
        <p className="px-6 pt-2 font-mono text-xs text-block">
          Failed to load: {errors.join(', ')}. Showing the last known data where available.
        </p>
      )}

      <div className="flex gap-3 px-6 py-3">
        {TILES.map((tile) => (
          <StatTile
            key={tile.key}
            label={labelsByTile[tile.key] ?? tile.label}
            value={values[tile.key]}
            tone={tile.tone}
            isLoading={isLoadingByTile[tile.key]}
          />
        ))}
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-2 grid-rows-3 gap-3 px-6 pb-4">
        <OutcomeTrendChart data={outcomeData} isLoading={trendsLoading} periodUnit={periodUnit} rangeLabel={range.rangeLabel} />
        <VolumeTrendChart data={volumeData} isLoading={trendsLoading} periodUnit={periodUnit} rangeLabel={range.rangeLabel} />
        <ThrottledAccountsLeaderboard accounts={accounts} isLoading={throttledLoading} />
        <RingDetectionsChart data={ringData} isLoading={ringsLoading} periodUnit={periodUnit} rangeLabel={range.rangeLabel} />
        <ReconciliationDriftChart data={driftData} isLoading={trendsLoading} periodUnit={periodUnit} rangeLabel={range.rangeLabel} />
        <OutcomeCompositionPie data={compositionData} isLoading={trendsLoading} rangeLabel={range.rangeLabel} />
      </div>
    </div>
  )
}
