import { AnimatePresence, motion } from 'framer-motion'
import { dismissToast, useToasts } from '@/lib/toastStore'

const TONE_STYLES = {
  block: 'border-block bg-block-muted',
  flag: 'border-flag bg-flag-muted',
  approve: 'border-approve bg-approve-muted',
  info: 'border-border-strong bg-panel-raised',
}

export function AlertToastHost() {
  const toasts = useToasts()

  return (
    <div className="pointer-events-none fixed top-4 right-4 z-50 flex w-80 flex-col gap-2">
      <AnimatePresence initial={false}>
        {toasts.map((toast) => (
          <motion.button
            key={toast.id}
            type="button"
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.18 }}
            onClick={() => dismissToast(toast.id)}
            className={`pointer-events-auto border px-3 py-2 text-left font-mono text-xs text-text ${TONE_STYLES[toast.tone]}`}
          >
            {toast.message}
          </motion.button>
        ))}
      </AnimatePresence>
    </div>
  )
}
