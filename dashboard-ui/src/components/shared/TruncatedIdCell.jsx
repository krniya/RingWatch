// CSS-truncates a long ID (transaction/account) to fit its column instead of hard-slicing the
// string at a fixed character count - the full value is always available via the title tooltip.
export function TruncatedIdCell({ value, maxWidth = 140, tone = 'text-text-muted' }) {
  return (
    <td className={`truncate px-3 py-2 font-mono text-xs ${tone}`} style={{ maxWidth }} title={value}>
      {value}
    </td>
  )
}
