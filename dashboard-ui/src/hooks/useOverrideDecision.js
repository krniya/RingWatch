import { useMutation, useQueryClient } from '@tanstack/react-query'
import { overrideDecision } from '@/api/decisionApi'
import { useAuth } from '@/context/AuthContext'
import { pushToast } from '@/lib/toastStore'

const OUTCOME_TOAST_TONE = {
  APPROVE: 'approve',
  FLAG: 'flag',
  BLOCK: 'block',
}

// First useMutation in the app (everything else so far is polling useQuery) - an override is a
// one-shot analyst action, not something to poll for.
export function useOverrideDecision() {
  const { session } = useAuth()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ transactionId, outcome, reason }) =>
      overrideDecision(transactionId, { outcome, reason }, session?.token),
    onSuccess: (response, { transactionId }) => {
      // Non-exact match (the default) also covers useReviewQueue's ['audit-log', 'review'] key.
      queryClient.invalidateQueries({ queryKey: ['audit-log'] })
      queryClient.invalidateQueries({ queryKey: ['audit-trail', transactionId] })
      pushToast({
        tone: OUTCOME_TOAST_TONE[response.outcome] ?? 'info',
        message: `Transaction ${transactionId} overridden to ${response.outcome}.`,
      })
    },
    onError: (error) => {
      pushToast({
        tone: 'block',
        message: error.body?.message ?? 'Override failed. Please try again.',
      })
    },
  })
}
