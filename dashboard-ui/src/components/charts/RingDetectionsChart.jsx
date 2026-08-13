import { Bar, BarChart, CartesianGrid, Tooltip, XAxis, YAxis } from 'recharts'
import { ChartPanel } from './ChartPanel'
import { ChartTooltip } from './ChartTooltip'
import { CHART_COLORS, CHART_TICK_STYLE } from '@/lib/chartTheme'

export function RingDetectionsChart({ data, isLoading }) {
  return (
    <ChartPanel title="Fraud Rings Detected" subtitle="Detections per hour, last 24h" isLoading={isLoading}>
      <BarChart data={data}>
        <CartesianGrid stroke={CHART_COLORS.border} vertical={false} />
        <XAxis dataKey="hourLabel" tick={CHART_TICK_STYLE} axisLine={{ stroke: CHART_COLORS.border }} tickLine={false} interval="preserveStartEnd" />
        <YAxis tick={CHART_TICK_STYLE} axisLine={false} tickLine={false} allowDecimals={false} width={28} />
        <Tooltip content={<ChartTooltip />} cursor={{ fill: CHART_COLORS.border, opacity: 0.3 }} />
        <Bar dataKey="count" name="Rings detected" fill={CHART_COLORS.block} radius={[2, 2, 0, 0]} />
      </BarChart>
    </ChartPanel>
  )
}
