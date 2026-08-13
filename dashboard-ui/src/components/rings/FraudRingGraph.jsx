import { useEffect, useRef, useState } from 'react'
import ForceGraph2D from 'react-force-graph-2d'

// Mirrors index.css's tokens - canvas rendering can't consume CSS custom properties directly, so
// these are hardcoded to match --color-text-muted/--color-accent/--color-border-strong.
const NODE_COLOR = '#8b92a3'
const NODE_COLOR_SELECTED = '#22d3ee'
const LINK_COLOR = '#34384a'

export function FraudRingGraph({ graphData, selectedAccountId, onNodeClick }) {
  const containerRef = useRef(null)
  const fgRef = useRef(null)
  const [size, setSize] = useState({ width: 0, height: 0 })

  // The first canvas-based component in the app - ForceGraph2D needs explicit width/height
  // props, it won't auto-size to its parent like everything else here.
  useEffect(() => {
    const element = containerRef.current
    if (!element) return

    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect
      setSize({ width, height })
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  // A ring detection links every member pair, so a large ring is a near-complete graph - the
  // library's default charge/link-distance packs those far too tightly to tell nodes/edges apart.
  // Stronger mutual repulsion plus a longer rest length for links spreads clusters out; re-applied
  // whenever the node/link set changes since toGraphData feeds in new detections over time.
  // Also depends on size: ForceGraph2D (and fgRef) only mounts once the ResizeObserver reports a
  // nonzero size, which doesn't change graphData's reference - without this, the very first
  // layout is skipped and keeps the library's default (tightly-packed) forces.
  useEffect(() => {
    const fg = fgRef.current
    if (!fg) return
    fg.d3Force('charge')?.strength(-400).distanceMax(600)
    fg.d3Force('link')?.distance(100)
    fg.d3ReheatSimulation()
  }, [graphData, size.width, size.height])

  return (
    <div ref={containerRef} className="relative flex-1">
      {size.width > 0 && size.height > 0 && (
        <ForceGraph2D
          ref={fgRef}
          width={size.width}
          height={size.height}
          graphData={graphData}
          backgroundColor="transparent"
          nodeLabel={(node) => node.id}
          nodeRelSize={5}
          nodeColor={(node) => (node.id === selectedAccountId ? NODE_COLOR_SELECTED : NODE_COLOR)}
          linkColor={() => LINK_COLOR}
          linkWidth={1}
          onNodeClick={(node) => onNodeClick?.(node.id)}
        />
      )}
    </div>
  )
}
