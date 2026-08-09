import { Network } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { EmptyState } from '@/components/shared/EmptyState'

export function FraudRingsPage() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex flex-1 flex-col overflow-y-auto">
        <PageHeader title="Fraud Rings" subtitle="Detected clusters of colluding accounts" />

        <EmptyState
          icon={Network}
          heading="Ring detection isn't queryable yet"
          description="Fraud-ring-detection-service already detects rings from shared account attributes, but only publishes them to Kafka - nothing persists that history anywhere queryable yet. This page will visualize rings as a graph (nodes = accounts, edges = shared attributes) once that backend work lands."
        />
      </div>
    </div>
  )
}
