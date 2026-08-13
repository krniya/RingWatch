import { CHART_COLORS } from '@/lib/chartTheme'

// recharts' default Tooltip renders a white box, which clashes with the app's dark palette - this
// replaces it with one styled like every other panel in the app (bg-panel, border, mono numbers).
export function ChartTooltip({ active, payload, label, formatter }) {
  if (!active || !payload?.length) return null

  return (
    <div
      className="rounded-panel border border-border px-3 py-2 font-mono text-xs shadow-elevation-3"
      style={{ backgroundColor: CHART_COLORS.panel }}
    >
      {label && <p className="mb-1 text-text-faint uppercase">{label}</p>}
      {payload.map((entry) => (
        <p key={entry.dataKey} style={{ color: entry.color }}>
          {entry.name}: {formatter ? formatter(entry.value) : entry.value}
        </p>
      ))}
    </div>
  )
}
