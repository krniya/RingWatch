import { useQuery } from '@tanstack/react-query'
import { fetchFraudRings } from '@/api/fraudRingsApi'
import { useAuth } from '@/context/AuthContext'

const POLL_INTERVAL_MS = 15000

export function useFraudRings() {
  const { session } = useAuth()

  const query = useQuery({
    queryKey: ['fraud-rings'],
    queryFn: () => fetchFraudRings(session?.token),
    enabled: Boolean(session?.token),
    refetchInterval: POLL_INTERVAL_MS,
  })

  return {
    detections: query.data ?? [],
    isLoading: query.isLoading,
    isError: query.isError && !query.data,
    error: query.error,
  }
}
