import { useQuery } from '@tanstack/react-query'
import { fetchThrottledAccounts } from '@/api/throttledAccountsApi'
import { useAuth } from '@/context/AuthContext'

const POLL_INTERVAL_MS = 15000

export function useThrottledAccounts() {
  const { session } = useAuth()

  const query = useQuery({
    queryKey: ['throttled-accounts'],
    queryFn: () => fetchThrottledAccounts(session?.token),
    enabled: Boolean(session?.token),
    refetchInterval: POLL_INTERVAL_MS,
  })

  return {
    accounts: query.data ?? [],
    isLoading: query.isLoading,
    isError: query.isError && !query.data,
    error: query.error,
  }
}
