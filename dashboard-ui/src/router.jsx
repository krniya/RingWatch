import { Navigate, Outlet, createBrowserRouter, useLocation } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { AppShell } from '@/components/layout/AppShell'
import { LoginPage } from '@/pages/LoginPage'
import { OverviewPage } from '@/pages/OverviewPage'
import { FeedPage } from '@/pages/FeedPage'
import { ReviewQueuePage } from '@/pages/ReviewQueuePage'
import { FraudRingsPage } from '@/pages/FraudRingsPage'

function RequireAuth() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { path: '/', element: <Navigate to="/overview" replace /> },
          { path: '/overview', element: <OverviewPage /> },
          { path: '/feed', element: <FeedPage /> },
          { path: '/review', element: <ReviewQueuePage /> },
          { path: '/rings', element: <FraudRingsPage /> },
          { path: '*', element: <Navigate to="/overview" replace /> },
        ],
      },
    ],
  },
])
