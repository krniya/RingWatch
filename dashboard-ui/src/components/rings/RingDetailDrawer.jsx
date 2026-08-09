import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import { formatTimestamp, shortenId } from '@/lib/formatters'

export function RingDetailDrawer({ accountId, detections, open, onOpenChange }) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full rounded-l-[var(--radius-panel)] shadow-elevation-4 sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Ring membership</SheetTitle>
          <SheetDescription className="font-mono text-xs">
            {accountId ? shortenId(accountId, 24) : ''}
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-3 overflow-y-auto px-4 pb-4">
          {detections.map((detection, index) => (
            <div
              key={`${detection.ringId}-${index}`}
              className="rounded-[var(--radius-panel)] border border-border bg-panel-raised p-3"
            >
              <p className="font-mono text-xs text-text-muted">{formatTimestamp(detection.detectedAt)}</p>
              <p className="mt-1 text-sm text-text">{detection.sharedAttributes}</p>
              {detection.aiExplanation && (
                <p className="mt-2 text-xs text-text-muted">{detection.aiExplanation}</p>
              )}
              <p className="mt-2 font-mono text-xs text-text-muted">
                Members: {detection.memberAccountIds.map((id) => shortenId(id)).join(', ')}
              </p>
            </div>
          ))}
        </div>
      </SheetContent>
    </Sheet>
  )
}
