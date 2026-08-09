export function PageHeader({ title, subtitle, children }) {
  return (
    <div className="glass-surface sticky top-0 z-10 flex items-center justify-between px-6 py-4">
      <div>
        <h1 className="text-lg font-medium">{title}</h1>
        {subtitle && <div className="font-mono text-xs text-text-muted">{subtitle}</div>}
      </div>
      {children}
    </div>
  )
}
