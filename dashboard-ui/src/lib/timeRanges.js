import { HOUR_MS, DAY_MS } from './analytics'

export const TIME_RANGES = [
  { key: '1h', label: '1H', windowMs: HOUR_MS, rangeLabel: 'last 1 hour' },
  { key: '24h', label: '24H', windowMs: DAY_MS, rangeLabel: 'last 24 hours' },
  { key: '7d', label: '7D', windowMs: 7 * DAY_MS, rangeLabel: 'last 7 days' },
  { key: '30d', label: '30D', windowMs: 30 * DAY_MS, rangeLabel: 'last 30 days' },
]

export const DEFAULT_TIME_RANGE_KEY = '24h'

export function timeRangeFor(key) {
  return TIME_RANGES.find((range) => range.key === key) ?? TIME_RANGES.find((range) => range.key === DEFAULT_TIME_RANGE_KEY)
}
