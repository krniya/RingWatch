import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { AlertToastHost } from './AlertToastHost'
import { useAlertSocket } from '@/hooks/useAlertSocket'

export function AppShell() {
  useAlertSocket()

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
