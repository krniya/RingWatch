// FR23: the detection pipeline never records which two accounts share which specific device/IP
// (see fraud-ring-detection-service's FraudRingEvent - `sharedAttributes` is a prose summary, not
// structured per-pair data). So "edges = shared attributes" is represented here as co-membership
// in the same detected ring: every pair within a detection's memberAccountIds becomes an edge,
// labeled with that detection's own description. Edges from overlapping detections are merged so
// a pair that co-appears in multiple rings carries all of them, not just the most recent.
//
// `previousNodesById` (a Map the caller keeps across renders/polls, e.g. via useRef) lets already-
// seen accounts reuse the same node object react-force-graph was rendering before: the underlying
// d3-force simulation stores each node's x/y/vx/vy directly on that object, so handing it a fresh
// `{ id }` every 15s poll would look like a brand-new graph and restart the layout from scratch.
export function toGraphData(detections, previousNodesById = new Map()) {
  const nodesById = new Map()
  const linksByKey = new Map()

  for (const detection of detections) {
    const members = [...detection.memberAccountIds]
    for (const id of members) {
      if (!nodesById.has(id)) nodesById.set(id, previousNodesById.get(id) ?? { id })
    }
    for (let i = 0; i < members.length; i++) {
      for (let j = i + 1; j < members.length; j++) {
        const [source, target] = [members[i], members[j]].sort()
        const key = `${source}|${target}`
        const link = linksByKey.get(key)
        if (link) {
          link.detections.push(detection)
        } else {
          linksByKey.set(key, { source, target, detections: [detection] })
        }
      }
    }
  }

  // Feed this call's nodes back into the shared cache so the next poll can keep reusing them.
  nodesById.forEach((node, id) => previousNodesById.set(id, node))

  return {
    nodes: [...nodesById.values()],
    links: [...linksByKey.values()],
  }
}

export function detectionsForAccount(detections, accountId) {
  if (!accountId) return []
  return detections.filter((detection) => detection.memberAccountIds.includes(accountId))
}
