import { Area, AreaChart, CartesianGrid, Tooltip, XAxis, YAxis } from 'recharts'
import { ChartPanel } from './ChartPanel'
import { ChartTooltip } from './ChartTooltip'
import { CHART_COLORS, CHART_TICK_STYLE } from '@/lib/chartTheme'
import { formatAmount } from '@/lib/formatters'

export function VolumeTrendChart({ data, isLoading }) {
  return (
    <ChartPanel title="Volume Trend" subtitle="Transaction volume per hour, last 24h" isLoading={isLoading}>
      <AreaChart data={data}>
        <defs>
          <linearGradient id="volumeFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={CHART_COLORS.accent} stopOpacity={0.35} />
            <stop offset="100%" stopColor={CHART_COLORS.accent} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke={CHART_COLORS.border} vertical={false} />
        <XAxis dataKey="hourLabel" tick={CHART_TICK_STYLE} axisLine={{ stroke: CHART_COLORS.border }} tickLine={false} interval="preserveStartEnd" />
        <YAxis tick={CHART_TICK_STYLE} axisLine={false} tickLine={false} width={40} tickFormatter={(v) => formatAmount(v).replace(/\.00$/, '')} />
        <Tooltip content={<ChartTooltip formatter={formatAmount} />} cursor={{ stroke: CHART_COLORS.accent, strokeWidth: 1 }} />
        <Area type="monotone" dataKey="volume" name="Volume" stroke={CHART_COLORS.accent} fill="url(#volumeFill)" strokeWidth={1.5} />
      </AreaChart>
    </ChartPanel>
  )
}
