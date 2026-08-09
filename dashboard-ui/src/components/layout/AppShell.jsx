import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { AlertToastHost } from './AlertToastHost'

export function AppShell() {
  return (
    <div className="flex h-full bg-bg text-text">
      <Sidebar />
      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
      <AlertToastHost />
    </div>
  )
}
