export function EmptyState({ icon: Icon, heading, description }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-16 text-center">
      {Icon && (
        <div className="flex size-12 items-center justify-center rounded-panel border border-border bg-panel-raised">
          <Icon className="size-5 text-text-faint" />
        </div>
      )}
      <div className="space-y-1">
        <p className="text-sm font-medium text-text">{heading}</p>
        {description && <p className="max-w-sm font-mono text-xs text-text-muted">{description}</p>}
      </div>
    </div>
  )
}
