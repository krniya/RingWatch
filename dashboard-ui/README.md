# RingWatch Dashboard

The analyst-facing React frontend for RingWatch (Phase 5). Not a Maven module — built and run
independently of the backend services, per the root [README](../README.md#repository-structure).

Vite + React 19, plain JavaScript (no TypeScript), Tailwind CSS v4, and shadcn/ui (Base UI
primitives) re-themed into a dark, zero-radius "Tactical Telemetry" look — see `src/index.css` for
the theme tokens.

## Running it

Requires `api-gateway`, `auth-service`, and `audit-service` running locally (see the root
[README](../README.md#running-it-locally)).

```bash
npm install
npm run dev   # http://localhost:5173
```

Log in with the seeded admin account. The API base URL defaults to `http://localhost:8080`
(the gateway); override with `VITE_API_BASE_URL` if needed.

## Built so far

- Login against Auth Service, JWT + role held in `sessionStorage`, attached to every API call
- Route guard (redirects to `/login` when unauthenticated) and logout
- App shell (`AppShell`/`Sidebar`) and a toast host (`AlertToastHost`, Framer Motion transitions) —
  not yet fed by anything, since no backend push source exists yet (see below)
- Live transaction feed (`FeedPage`): polls `GET /audit` every 5s (via the gateway's `/audit/**`
  route) on a rolling 6h window, folding the audit-event log into one row per transaction

## Explicit follow-ups (not built yet)

- **Review queue + override action** — `POST /transactions/{id}/override` doesn't exist on the
  backend yet either; this is a backend slice as much as a frontend one
- **Fraud ring graph** — planned with [`react-force-graph`](https://github.com/vasturiano/react-force-graph)
  (2D/canvas), already a dependency; no page built yet
- **Live push for the alert toasts** — there's no Dashboard Gateway Service or WebSocket/SSE
  endpoint anywhere in the backend yet, so `AlertToastHost` has no data source. The live feed
  polls instead (`useLiveTransactions`), deliberately written so a push-based source can replace
  the polling later without changing anything that consumes the hook
- Vitest + React Testing Library — no test framework added in this pass
