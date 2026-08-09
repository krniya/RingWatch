// Shared row-interaction wiring for clickable <tr>s (TransactionRow/ReviewQueueRow) - a native
// <tr> isn't focusable or keyboard-activatable on its own, so this fills that in consistently
// rather than each row re-implementing the same Enter/Space handling.
export function useClickableRow(transaction, onClick) {
  function handleKeyDown(event) {
    if (!onClick) return
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onClick(transaction)
    }
  }

  return {
    tabIndex: onClick ? 0 : undefined,
    onClick: onClick ? () => onClick(transaction) : undefined,
    onKeyDown: handleKeyDown,
  }
}
