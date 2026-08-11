# RingWatch Dashboard

The analyst-facing React frontend for RingWatch. Not a Maven module — built and run independently
of the backend services, per the root [README](../README.md#repository-structure).

Vite + React 19, plain JavaScript (no TypeScript), Tailwind CSS v4, and shadcn/ui (Base UI
primitives) re-themed into a dark, zero-radius "Tactical Telemetry" look — see `src/index.css` for
the theme tokens.

## Running it

Requires `api-gateway`, `auth-service`, `audit-service`, `decision-engine`,
`fraud-ring-detection-service`, and `dashboard-gateway-service` running locally (see the root
[README](../README.md#running-it-locally)).

```bash
npm install
npm run dev   # http://localhost:5173
```

Log in with the seeded admin account. The API base URL defaults to `http://localhost:8080`
(the gateway); override with `VITE_API_BASE_URL` if needed. The alert WebSocket base URL defaults
to `ws://localhost:8080` (the gateway too); override with `VITE_WS_BASE_URL` if needed.

## What's here

- Login against Auth Service, JWT + role held in `sessionStorage`, attached to every API call
- Route guard (redirects to `/login` when unauthenticated) and logout
- App shell (`AppShell`/`Sidebar`) and a toast host (`AlertToastHost`, Framer Motion transitions),
  fired both by analyst actions (e.g. a successful override) and by live push: `useAlertSocket`
  holds a WebSocket to dashboard-gateway-service's `/ws/alerts`, which broadcasts every
  `notifications.alerts` Kafka event (new FLAG/BLOCK decisions, newly detected fraud rings) as an
  in-app toast in real time
- Live transaction feed (`FeedPage`): polls `GET /audit` every 5s (via the gateway's `/audit/**`
  route) on a rolling 6h window, folding the audit-event log into one row per transaction
- Review queue (`ReviewQueuePage`): polls the audit log for flagged/blocked transactions over a
  rolling 24h window, with an **Override** action (`POST /transactions/{id}/override`) that lets
  an analyst set a new outcome with a reason; the override shows up in the transaction's audit
  trail as its own event, distinct from the original decision
- Audit trail drawer (`AuditTrailDrawer`): the full CREATED → SCORED → DECIDED → (OVERRIDDEN /
  RECONCILED) history for a single transaction, opened from either the feed or the review queue
- Fraud rings page (`FraudRingsPage`): a force-directed graph
  ([`react-force-graph-2d`](https://github.com/vasturiano/react-force-graph)) of detected fraud
  rings — nodes are accounts, edges connect accounts that co-appeared in the same detected ring —
  with a detail drawer on node click showing which ring(s) that account belongs to and the AI's
  explanation of each

## Architecture notes

- **Alerts are push; the feed/review queue/rings are still polling.** `useAlertSocket` is the
  first genuinely push-driven data source in this app — it delivers in-app toasts live over
  WebSocket. The live feed, review queue, and fraud-rings graph still poll their REST endpoints on
  an interval; they show different data (full transaction/ring state, not point-in-time alerts)
  and were a separate scope decision, not something this feature touched. Each polling hook
  (`useLiveTransactions`, `useReviewQueue`, `useFraudRings`) is still written so a push-based data
  source could replace its interval later without changing anything that consumes it.
- **No missed-alert catch-up.** `useAlertSocket` reconnects on drop (fixed 5s delay) but doesn't
  request a backlog — an alert published while disconnected is simply not delivered, consistent
  with FR32's existing best-effort framing for this same alerts pipeline (email delivery already
  accepts this).
- **Graph node identity across polls.** `FraudRingGraph` reuses the same node objects across
  successive polls (via a cache keyed by account ID) so the force-directed layout's physics state
  persists instead of resetting every refresh — see `src/lib/fraudRingGraph.js`.
