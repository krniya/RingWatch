import { Button } from '@/components/ui/button'
import { TIME_RANGES } from '@/lib/timeRanges'

export function TimeRangeSelector({ value, onChange }) {
  return (
    <div className="flex gap-1 rounded-panel border border-border bg-panel p-1">
      {TIME_RANGES.map((range) => (
        <Button
          key={range.key}
          type="button"
          size="xs"
          variant={range.key === value ? 'secondary' : 'ghost'}
          className="font-mono"
          onClick={() => onChange(range.key)}
        >
          {range.label}
        </Button>
      ))}
    </div>
  )
}
