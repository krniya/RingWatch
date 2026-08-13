import { Cell, Pie, PieChart, Tooltip } from 'recharts'
import { ChartPanel } from './ChartPanel'
import { ChartTooltip } from './ChartTooltip'
import { CHART_COLORS } from '@/lib/chartTheme'
import { EmptyState } from '@/components/shared/EmptyState'
import { PieChart as PieChartIcon } from 'lucide-react'

const SLICE_COLOR = { APPROVE: CHART_COLORS.approve, FLAG: CHART_COLORS.flag, BLOCK: CHART_COLORS.block }

export function OutcomeCompositionPie({ data, isLoading }) {
  if (!isLoading && data.length === 0) {
    return (
      <div className="flex h-full flex-col rounded-panel border border-border bg-panel px-4 py-3 shadow-elevation-2">
        <p className="shrink-0 font-mono text-[11px] tracking-wide text-text-faint uppercase">Outcome Composition</p>
        <EmptyState icon={PieChartIcon} heading="No decisions yet" description="Outcome share will appear here once transactions are decided." />
      </div>
    )
  }

  return (
    <ChartPanel title="Outcome Composition" subtitle="Share of decisions, last 24h" isLoading={isLoading}>
      <PieChart>
        <Tooltip content={<ChartTooltip />} />
        <Pie data={data} dataKey="value" nameKey="name" innerRadius="55%" outerRadius="85%" paddingAngle={2} strokeWidth={0}>
          {data.map((entry) => (
            <Cell key={entry.outcome} fill={SLICE_COLOR[entry.outcome]} />
          ))}
        </Pie>
      </PieChart>
    </ChartPanel>
  )
}
