const HOUR_MS = 60 * 60 * 1000

// Buckets are labeled by their start hour ("14:00") in the browser's local time, oldest first,
// with every hour present even if empty - a chart with gaps where no events happened would read as
// missing data rather than genuinely zero.
function hourBuckets(windowMs, now = Date.now()) {
  const hours = Math.ceil(windowMs / HOUR_MS)
  const buckets = []
  for (let i = hours - 1; i >= 0; i--) {
    const bucketStart = new Date(now - i * HOUR_MS)
    bucketStart.setMinutes(0, 0, 0)
    buckets.push(bucketStart)
  }
  return buckets
}

function hourLabel(date) {
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
}

function bucketKey(date) {
  return new Date(date).setMinutes(0, 0, 0)
}

// Shared shape behind every bucketOn*ByHour below: seed each hour with `seed()`, fold matching
// items into their hour via `apply`, skipping items whose `dateOf` is missing or falls in an hour
// outside the window (the Map lookup miss handles that - no separate cutoff check needed).
function bucketByHour(items, { dateOf, seed, apply }, windowMs = 24 * HOUR_MS) {
  const buckets = hourBuckets(windowMs)
  const valuesByBucket = new Map(buckets.map((bucket) => [bucket.getTime(), seed()]))

  for (const item of items) {
    const date = dateOf(item)
    if (!date) continue
    const values = valuesByBucket.get(bucketKey(date))
    if (values) apply(values, item)
  }

  return buckets.map((bucket) => ({ hourLabel: hourLabel(bucket), ...valuesByBucket.get(bucket.getTime()) }))
}

export function bucketOutcomesByHour(transactions, windowMs = 24 * HOUR_MS) {
  return bucketByHour(
    transactions,
    {
      dateOf: (t) => t.lastEventAt,
      seed: () => ({ APPROVE: 0, FLAG: 0, BLOCK: 0 }),
      apply: (counts, t) => {
        if (t.outcome in counts) counts[t.outcome] += 1
      },
    },
    windowMs,
  )
}

export function bucketVolumeByHour(transactions, windowMs = 24 * HOUR_MS) {
  return bucketByHour(
    transactions,
    {
      dateOf: (t) => t.lastEventAt,
      seed: () => ({ volume: 0 }),
      apply: (values, t) => {
        values.volume += Number(t.amount ?? 0)
      },
    },
    windowMs,
  )
}

export function bucketRingDetectionsByHour(detections, windowMs = 24 * HOUR_MS) {
  return bucketByHour(
    detections,
    {
      dateOf: (d) => d.detectedAt,
      seed: () => ({ count: 0 }),
      apply: (values) => {
        values.count += 1
      },
    },
    windowMs,
  )
}

// reconciliationEvents are ReconciliationResultEvent payloads ({ drifted, checkedAt, ... }) pulled
// straight off RECONCILED audit entries - bucketed by checkedAt (when the drift check actually
// ran), not the original decision's timestamp, since reconciliation-service intentionally
// re-checks decisions that are 1-7 days old (RECONCILIATION_MIN_AGE_MS/MAX_AGE_MS) - the recheck
// itself is what's recent, not the transaction being rechecked.
export function bucketDriftByHour(reconciliationEvents, windowMs = 24 * HOUR_MS) {
  return bucketByHour(
    reconciliationEvents,
    {
      dateOf: (e) => e.checkedAt,
      seed: () => ({ drifted: 0, matched: 0 }),
      apply: (counts, e) => {
        if (e.drifted) counts.drifted += 1
        else counts.matched += 1
      },
    },
    windowMs,
  )
}

const OUTCOME_LABELS = { APPROVE: 'Approved', FLAG: 'Flagged', BLOCK: 'Blocked' }

export function outcomeComposition(transactions) {
  const counts = { APPROVE: 0, FLAG: 0, BLOCK: 0 }
  for (const transaction of transactions) {
    if (transaction.outcome in counts) counts[transaction.outcome] += 1
  }
  return Object.entries(counts)
    .map(([outcome, value]) => ({ outcome, name: OUTCOME_LABELS[outcome], value }))
    .filter((entry) => entry.value > 0)
}
