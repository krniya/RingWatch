import { History } from 'lucide-react'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { EmptyState } from '@/components/shared/EmptyState'
import { AuditTrailEntry } from './AuditTrailEntry'
import { useAuditTrail } from '@/hooks/useAuditTrail'
import { shortenId } from '@/lib/formatters'

export function AuditTrailDrawer({ transactionId, open, onOpenChange }) {
  const { events, isLoading, isError } = useAuditTrail(transactionId)

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full rounded-l-[var(--radius-panel)] shadow-elevation-4 sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Audit trail</SheetTitle>
          <SheetDescription className="font-mono text-xs">
            {transactionId ? shortenId(transactionId, 24) : ''}
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto px-4 pb-4">
          {isLoading && (
            <div className="space-y-3">
              {[...Array(3)].map((_, index) => (
                <Skeleton key={index} className="h-16 w-full" />
              ))}
            </div>
          )}
          {isError && <p className="font-mono text-sm text-block">Failed to load the audit trail.</p>}
          {!isLoading && !isError && events.length === 0 && (
            <EmptyState
              icon={History}
              heading="No history yet"
              description="This transaction has no recorded events."
            />
          )}
          {!isLoading && !isError && events.length > 0 && (
            <div className="space-y-3">
              {events.map((event) => (
                <AuditTrailEntry key={event.eventId} event={event} />
              ))}
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}
