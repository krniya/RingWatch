const currencyFormatterCache = new Map()

export function formatAmount(amount, currency) {
  if (amount == null) return '—'
  const key = currency ?? 'USD'
  let formatter = currencyFormatterCache.get(key)
  if (!formatter) {
    formatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: key })
    currencyFormatterCache.set(key, formatter)
  }
  return formatter.format(Number(amount))
}

export function formatTimestamp(isoString) {
  if (!isoString) return '—'
  return new Date(isoString).toLocaleTimeString('en-US', { hour12: false })
}

export function formatRiskScore(score) {
  if (score == null) return '—'
  return Number(score).toFixed(2)
}

export function shortenId(id, length = 8) {
  if (!id) return '—'
  return id.length > length ? `${id.slice(0, length)}…` : id
}
