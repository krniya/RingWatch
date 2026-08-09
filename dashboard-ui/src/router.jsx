import { Navigate, Outlet, createBrowserRouter, useLocation } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { AppShell } from '@/components/layout/AppShell'
import { LoginPage } from '@/pages/LoginPage'
import { FeedPage } from '@/pages/FeedPage'

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
          { path: '/', element: <Navigate to="/feed" replace /> },
          { path: '/feed', element: <FeedPage /> },
          { path: '*', element: <Navigate to="/feed" replace /> },
        ],
      },
    ],
  },
])
