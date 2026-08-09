const STATUS_STYLES = {
  APPROVE: 'border-approve text-approve',
  FLAG: 'border-flag text-flag',
  BLOCK: 'border-block text-block',
  PENDING: 'border-border-strong text-text-muted',
}

const STATUS_LABELS = {
  APPROVE: 'Approved',
  FLAG: 'Flagged',
  BLOCK: 'Blocked',
  PENDING: 'Pending',
}

export function StatusBadge({ outcome }) {
  const status = outcome ?? 'PENDING'
  return (
    <span
      className={`inline-block border px-2 py-0.5 font-mono text-[11px] tracking-wide uppercase ${STATUS_STYLES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}
