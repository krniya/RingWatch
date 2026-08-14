export const HOUR_MS = 60 * 60 * 1000
export const DAY_MS = 24 * HOUR_MS
const FIVE_MIN_MS = 5 * 60 * 1000

// Windows longer than this are bucketed by day instead of hour - a 30-day trend rendered as 720
// hourly bars would be unreadable, and hourly precision isn't the point at that timescale anyway.
const DAY_BUCKET_THRESHOLD_MS = 2 * DAY_MS
// Windows this short or shorter get 5-minute buckets instead of hourly ones - at hourly
// granularity the "1H" range would collapse to a single bucket (ceil(1h / 1h) = 1), turning a
// trend chart into one flat bar.
const FIVE_MIN_BUCKET_THRESHOLD_MS = 3 * HOUR_MS

function periodMsFor(windowMs) {
  if (windowMs > DAY_BUCKET_THRESHOLD_MS) return DAY_MS
  if (windowMs <= FIVE_MIN_BUCKET_THRESHOLD_MS) return FIVE_MIN_MS
  return HOUR_MS
}

export function periodUnitFor(windowMs) {
  const periodMs = periodMsFor(windowMs)
  if (periodMs === DAY_MS) return 'day'
  if (periodMs === HOUR_MS) return 'hour'
  return '5 minutes'
}

// Rounds a date down to the start of the period it falls in, in place - shared by periodBuckets
// (building the empty bucket scaffold) and periodKey (placing an item into one of those buckets),
// so the two can never disagree about where a boundary falls.
function roundDownToPeriod(date, periodMs) {
  if (periodMs >= DAY_MS) {
    date.setHours(0, 0, 0, 0)
    return date
  }
  if (periodMs >= HOUR_MS) {
    date.setMinutes(0, 0, 0)
    return date
  }
  const periodMinutes = periodMs / 60000
  date.setMinutes(date.getMinutes() - (date.getMinutes() % periodMinutes), 0, 0)
  return date
}

// Buckets are labeled by their start (oldest first), with every period present even if empty - a
// chart with gaps where no events happened would read as missing data rather than genuinely zero.
function periodBuckets(windowMs, now = Date.now()) {
  const periodMs = periodMsFor(windowMs)
  const count = Math.ceil(windowMs / periodMs)
  const buckets = []
  for (let i = count - 1; i >= 0; i--) {
    buckets.push(roundDownToPeriod(new Date(now - i * periodMs), periodMs))
  }
  return buckets
}

function periodLabel(date, periodMs) {
  return periodMs === DAY_MS
    ? date.toLocaleDateString([], { month: 'short', day: 'numeric' })
    : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
}

function periodKey(date, periodMs) {
  return roundDownToPeriod(new Date(date), periodMs).getTime()
}

// Shared shape behind every bucketOn*ByPeriod below: seed each period with `seed()`, fold matching
// items into their period via `apply`, skipping items whose `dateOf` is missing or falls in a
// period outside the window (the Map lookup miss handles that - no separate cutoff check needed).
function bucketByPeriod(items, { dateOf, seed, apply }, windowMs = DAY_MS) {
  const periodMs = periodMsFor(windowMs)
  const buckets = periodBuckets(windowMs)
  const valuesByBucket = new Map(buckets.map((bucket) => [bucket.getTime(), seed()]))

  for (const item of items) {
    const date = dateOf(item)
    if (!date) continue
    const values = valuesByBucket.get(periodKey(date, periodMs))
    if (values) apply(values, item)
  }

  return buckets.map((bucket) => ({ label: periodLabel(bucket, periodMs), ...valuesByBucket.get(bucket.getTime()) }))
}

export function bucketOutcomesByPeriod(transactions, windowMs = DAY_MS) {
  return bucketByPeriod(
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

export function bucketVolumeByPeriod(transactions, windowMs = DAY_MS) {
  return bucketByPeriod(
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

export function bucketRingDetectionsByPeriod(detections, windowMs = DAY_MS) {
  return bucketByPeriod(
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
export function bucketDriftByPeriod(reconciliationEvents, windowMs = DAY_MS) {
  return bucketByPeriod(
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
