import { NavLink } from 'react-router-dom'
import { Activity, LogOut, Network, ShieldAlert } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { Button } from '@/components/ui/button'

const NAV_ITEMS = [
  { to: '/feed', label: 'Live Feed', icon: Activity },
  { to: '/review', label: 'Review Queue', icon: ShieldAlert },
  { to: '/rings', label: 'Fraud Rings', icon: Network },
]

export function Sidebar() {
  const { session, logout } = useAuth()

  return (
    <aside className="flex w-56 shrink-0 flex-col border-r border-border bg-panel">
      <div className="border-b border-border px-4 py-3">
        <p className="font-mono text-xs tracking-wider text-text-faint uppercase">RingWatch</p>
        <p className="text-sm font-medium">Analyst Console</p>
      </div>

      <nav className="flex-1 space-y-1 p-2">
        {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-2 px-3 py-2 text-sm transition-colors ${
                isActive
                  ? 'bg-panel-raised text-text'
                  : 'text-text-muted hover:bg-panel-raised hover:text-text'
              }`
            }
          >
            <Icon className="size-4" />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-border p-3">
        <p className="truncate font-mono text-xs text-text-muted">{session?.username}</p>
        <p className="mb-2 font-mono text-[11px] text-text-faint uppercase">{session?.role}</p>
        <Button variant="outline" size="sm" className="w-full justify-center" onClick={logout}>
          <LogOut className="size-3.5" />
          Log out
        </Button>
      </div>
    </aside>
  )
}
