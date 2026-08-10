import { useMemo, useRef, useState } from 'react'
import { Network } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { EmptyState } from '@/components/shared/EmptyState'
import { Skeleton } from '@/components/ui/skeleton'
import { FraudRingGraph } from '@/components/rings/FraudRingGraph'
import { RingDetailDrawer } from '@/components/rings/RingDetailDrawer'
import { useFraudRings } from '@/hooks/useFraudRings'
import { toGraphData, detectionsForAccount } from '@/lib/fraudRingGraph'

export function FraudRingsPage() {
  const { detections, isLoading, isError } = useFraudRings()
  const [selectedAccountId, setSelectedAccountId] = useState(null)

  // Survives across polls (unlike graphData itself) so toGraphData can hand react-force-graph the
  // same node objects it was already simulating, instead of resetting the layout every 15s.
  const nodeCacheRef = useRef(new Map())
  const graphData = useMemo(() => toGraphData(detections, nodeCacheRef.current), [detections])
  const selectedDetections = useMemo(
    () => detectionsForAccount(detections, selectedAccountId),
    [detections, selectedAccountId],
  )

  return (
    <div className="flex h-full flex-col">
      <div className="flex flex-1 flex-col overflow-hidden">
        <PageHeader title="Fraud Rings" subtitle="Detected clusters of colluding accounts" />

        {isLoading && (
          <div className="space-y-3 p-6">
            <Skeleton className="h-64 w-full" />
          </div>
        )}

        {isError && <p className="p-6 font-mono text-sm text-block">Failed to load fraud ring detections.</p>}

        {!isLoading && !isError && detections.length === 0 && (
          <EmptyState
            icon={Network}
            heading="No fraud rings detected yet"
            description="Detected clusters of accounts sharing a device, IP, or forming a circular fund-transfer pattern will appear here as a graph, with edges linking accounts flagged in the same ring."
          />
        )}

        {!isLoading && !isError && detections.length > 0 && (
          <FraudRingGraph
            graphData={graphData}
            selectedAccountId={selectedAccountId}
            onNodeClick={setSelectedAccountId}
          />
        )}
      </div>

      <RingDetailDrawer
        accountId={selectedAccountId}
        detections={selectedDetections}
        open={Boolean(selectedAccountId)}
        onOpenChange={(open) => {
          if (!open) setSelectedAccountId(null)
        }}
      />
    </div>
  )
}
