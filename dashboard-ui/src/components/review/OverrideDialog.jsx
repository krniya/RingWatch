import { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useOverrideDecision } from '@/hooks/useOverrideDecision'
import { shortenId } from '@/lib/formatters'

const OUTCOMES = ['APPROVE', 'FLAG', 'BLOCK']

export function OverrideDialog({ transaction, open, onOpenChange }) {
  const [outcome, setOutcome] = useState(transaction.outcome ?? 'APPROVE')
  const [reason, setReason] = useState('')
  const mutation = useOverrideDecision()

  function handleSubmit(event) {
    event.preventDefault()
    mutation.mutate(
      { transactionId: transaction.transactionId, outcome, reason },
      {
        onSuccess: () => {
          setReason('')
          onOpenChange(false)
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Override decision</DialogTitle>
            <DialogDescription className="font-mono">
              {shortenId(transaction.transactionId, 24)}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            <div>
              <label className="mb-1 block text-xs text-text-muted" htmlFor="override-outcome">
                New outcome
              </label>
              <select
                id="override-outcome"
                value={outcome}
                onChange={(event) => setOutcome(event.target.value)}
                className="w-full border border-border-strong bg-panel-raised px-3 py-2 font-mono text-sm text-text outline-none focus:border-accent"
              >
                {OUTCOMES.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="mb-1 block text-xs text-text-muted" htmlFor="override-reason">
                Reason
              </label>
              <textarea
                id="override-reason"
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                required
                rows={3}
                className="w-full resize-none border border-border-strong bg-panel-raised px-3 py-2 text-sm text-text outline-none focus:border-accent"
                placeholder="Why is this decision being overridden?"
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Submitting…' : 'Confirm override'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
