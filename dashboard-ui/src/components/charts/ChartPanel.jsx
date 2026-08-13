import { ResponsiveContainer } from 'recharts'
import { Skeleton } from '@/components/ui/skeleton'

// Shared chrome for every chart on the Overview page - same panel styling FeedStatsBar's StatTile
// already establishes (rounded-panel border bg-panel shadow-elevation-2), centralized here instead
// of repeated per chart. Fills its grid cell (h-full) rather than a fixed pixel height, so the
// chart grid uses all the space the page grants it instead of leaving a gap below a fixed size.
export function ChartPanel({ title, subtitle, isLoading, children }) {
  return (
    <div className="flex h-full flex-col rounded-panel border border-border bg-panel px-3 py-2 shadow-elevation-2">
      <p className="shrink-0 font-mono text-[11px] tracking-wide text-text-faint uppercase">{title}</p>
      {subtitle && <p className="mb-1 shrink-0 font-mono text-[10px] text-text-faint">{subtitle}</p>}

      {isLoading ? (
        <Skeleton className="mt-1 min-h-0 w-full flex-1 rounded-panel" />
      ) : (
        <div className="mt-1 min-h-0 flex-1">
          <ResponsiveContainer width="100%" height="100%">
            {children}
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}
