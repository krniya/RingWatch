import { useSyncExternalStore } from 'react'

let toasts = []
const listeners = new Set()

function emit() {
  for (const listener of listeners) listener()
}

export function pushToast(toast) {
  const id = crypto.randomUUID()
  toasts = [...toasts, { id, tone: 'info', ...toast }]
  emit()
  return id
}

export function dismissToast(id) {
  toasts = toasts.filter((toast) => toast.id !== id)
  emit()
}

export function useToasts() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => toasts,
  )
}
