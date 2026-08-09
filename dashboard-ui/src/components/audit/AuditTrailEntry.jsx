import { StatusBadge } from '@/components/feed/StatusBadge'
import { formatAmount, formatRiskScore, formatTimestamp } from '@/lib/formatters'

const EVENT_LABELS = {
  CREATED: 'Created',
  SCORED: 'Scored',
  DECIDED: 'Decided',
  OVERRIDDEN: 'Overridden',
}

function EventDetails({ eventType, payload }) {
  switch (eventType) {
    case 'CREATED':
      return (
        <dl className="grid grid-cols-2 gap-x-4 gap-y-1 font-mono text-xs text-text-muted">
          <dt className="text-text-faint">Amount</dt>
          <dd>{formatAmount(payload.amount, payload.currency)}</dd>
          <dt className="text-text-faint">Device</dt>
          <dd>{payload.deviceId ?? '—'}</dd>
          <dt className="text-text-faint">IP</dt>
          <dd>{payload.ipAddress ?? '—'}</dd>
        </dl>
      )
    case 'SCORED':
      return (
        <div className="space-y-1">
          <p className="font-mono text-xs text-text-muted">
            Risk score <span className="text-text">{formatRiskScore(payload.riskScore)}</span> (
            {payload.scoringMethod ?? '—'})
          </p>
          {payload.explanation && <p className="text-xs text-text-muted">{payload.explanation}</p>}
        </div>
      )
    case 'DECIDED':
      return (
        <div className="space-y-1">
          <StatusBadge outcome={payload.outcome} />
          {payload.reason && <p className="text-xs text-text-muted">{payload.reason}</p>}
        </div>
      )
    case 'OVERRIDDEN':
      return (
        <div className="space-y-1">
          <p className="font-mono text-xs text-text-muted">By {payload.overriddenBy ?? '—'}</p>
          {payload.overrideReason && <p className="text-xs text-text-muted">{payload.overrideReason}</p>}
        </div>
      )
    default:
      return null
  }
}

export function AuditTrailEntry({ event }) {
  return (
    <div className="border-l-2 border-border-strong py-2 pl-4">
      <div className="flex items-baseline justify-between gap-2">
        <p className="text-sm font-medium text-text">{EVENT_LABELS[event.eventType] ?? event.eventType}</p>
        <p className="font-mono text-[11px] text-text-faint">{formatTimestamp(event.recordedAt)}</p>
      </div>
      <div className="mt-1.5">
        <EventDetails eventType={event.eventType} payload={event.payload} />
      </div>
    </div>
  )
}
