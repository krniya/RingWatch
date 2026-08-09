import { Search } from 'lucide-react'

export function SearchInput({ value, onChange, placeholder = 'Search…' }) {
  return (
    <div className="relative w-64">
      <Search className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-text-faint" />
      <input
        type="text"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="w-full rounded border border-border-strong bg-panel-raised py-1.5 pr-3 pl-8 font-mono text-xs text-text outline-none transition-colors focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-accent/30"
      />
    </div>
  )
}
