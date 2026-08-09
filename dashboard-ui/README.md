# RingWatch Dashboard

The analyst-facing React frontend for RingWatch (Phase 5). Not a Maven module — built and run
independently of the backend services, per the root [README](../README.md#repository-structure).

## Planned scope

- Login against Auth Service (JWT stored client-side, attached to every API call)
- Live transaction feed
- Review queue for FLAG/BLOCK decisions, with an override action
  (`POST /transactions/{id}/override`)
- Fraud ring graph visualization (Cytoscape.js or D3)
- In-app alert toasts, fed by the Dashboard Gateway Service over WebSocket/SSE
  (consumes `notifications.alerts`)

Nothing is scaffolded yet beyond this folder — tooling/framework setup lands in a follow-up slice.
