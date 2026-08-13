import { Bar, BarChart, CartesianGrid, Tooltip, XAxis, YAxis } from 'recharts'
import { ChartPanel } from './ChartPanel'
import { ChartTooltip } from './ChartTooltip'
import { CHART_COLORS, CHART_TICK_STYLE } from '@/lib/chartTheme'

export function OutcomeTrendChart({ data, isLoading }) {
  return (
    <ChartPanel title="Outcome Trend" subtitle="Decisions per hour, last 24h" isLoading={isLoading}>
      <BarChart data={data}>
        <CartesianGrid stroke={CHART_COLORS.border} vertical={false} />
        <XAxis dataKey="hourLabel" tick={CHART_TICK_STYLE} axisLine={{ stroke: CHART_COLORS.border }} tickLine={false} interval="preserveStartEnd" />
        <YAxis tick={CHART_TICK_STYLE} axisLine={false} tickLine={false} allowDecimals={false} width={28} />
        <Tooltip content={<ChartTooltip />} cursor={{ fill: CHART_COLORS.border, opacity: 0.3 }} />
        <Bar dataKey="APPROVE" name="Approved" stackId="outcome" fill={CHART_COLORS.approve} />
        <Bar dataKey="FLAG" name="Flagged" stackId="outcome" fill={CHART_COLORS.flag} />
        <Bar dataKey="BLOCK" name="Blocked" stackId="outcome" fill={CHART_COLORS.block} radius={[2, 2, 0, 0]} />
      </BarChart>
    </ChartPanel>
  )
}
