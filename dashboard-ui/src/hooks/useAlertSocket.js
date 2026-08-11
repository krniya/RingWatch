import { useEffect } from 'react'
import { useAuth } from '@/context/AuthContext'
import { pushToast } from '@/lib/toastStore'

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL ?? 'ws://localhost:8080'
const RECONNECT_DELAY_MS = 5000

const ALERT_TYPE_TOAST_TONE = {
  TRANSACTION_BLOCKED: 'block',
  TRANSACTION_FLAGGED: 'flag',
  RING_DETECTED: 'flag',
}

// FR31: pushes toasts from notifications.alerts, via dashboard-gateway-service's WebSocket, into
// the same toastStore useOverrideDecision already writes to - no new toast plumbing needed.
// Connection failures fail silently and retry: toasts are supplementary, not load-bearing UI, so
// the feed/review queue's own polling keeps working regardless of this socket's state.
export function useAlertSocket() {
  const { session } = useAuth()
  const token = session?.token

  useEffect(() => {
    if (!token) return undefined

    let socket
    let reconnectTimer
    let stopped = false

    function connect() {
      socket = new WebSocket(`${WS_BASE_URL}/ws/alerts?token=${encodeURIComponent(token)}`)

      socket.onmessage = (event) => {
        try {
          const alert = JSON.parse(event.data)
          pushToast({
            tone: ALERT_TYPE_TOAST_TONE[alert.alertType] ?? 'info',
            message: alert.message,
          })
        } catch {
          // Malformed payload - drop it, the next alert will still come through.
        }
      }

      socket.onclose = () => {
        if (!stopped) reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS)
      }

      socket.onerror = () => socket.close()
    }

    connect()

    return () => {
      stopped = true
      clearTimeout(reconnectTimer)
      socket?.close()
    }
  }, [token])
}
