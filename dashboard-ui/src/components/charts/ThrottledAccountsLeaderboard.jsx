import { Bar, BarChart, CartesianGrid, Tooltip, XAxis, YAxis } from 'recharts'
import { ChartPanel } from './ChartPanel'
import { ChartTooltip } from './ChartTooltip'
import { EmptyState } from '@/components/shared/EmptyState'
import { CHART_COLORS, CHART_TICK_STYLE } from '@/lib/chartTheme'
import { shortenId } from '@/lib/formatters'
import { ShieldAlert } from 'lucide-react'

export function ThrottledAccountsLeaderboard({ accounts, isLoading }) {
  if (!isLoading && accounts.length === 0) {
    return (
      <div className="flex h-full flex-col rounded-panel border border-border bg-panel px-4 py-3 shadow-elevation-2">
        <p className="shrink-0 font-mono text-[11px] tracking-wide text-text-faint uppercase">Top Throttled Accounts</p>
        <EmptyState icon={ShieldAlert} heading="No throttling yet" description="Keys hitting the rate limiter will rank here." />
      </div>
    )
  }

  const data = accounts.map((entry) => ({ ...entry, label: shortenId(entry.key, 14) }))

  return (
    <ChartPanel title="Top Throttled Accounts" subtitle="Rejected requests by key" isLoading={isLoading}>
      <BarChart data={data} layout="vertical" margin={{ left: 8 }}>
        <CartesianGrid stroke={CHART_COLORS.border} horizontal={false} />
        <XAxis type="number" tick={CHART_TICK_STYLE} axisLine={false} tickLine={false} allowDecimals={false} />
        <YAxis type="category" dataKey="label" tick={CHART_TICK_STYLE} axisLine={false} tickLine={false} width={110} />
        <Tooltip content={<ChartTooltip />} cursor={{ fill: CHART_COLORS.border, opacity: 0.3 }} />
        <Bar dataKey="count" name="Rejections" fill={CHART_COLORS.flag} radius={[0, 2, 2, 0]} />
      </BarChart>
    </ChartPanel>
  )
}
