# Architecture (How It Works)

This project is a two‑tier app:
- Backend: Spring Boot (Java 17), exposes REST APIs under `/api` and `/api/admin`.
- Frontend: Angular (standalone components, Angular 17), served by `ng serve` during dev, or as static assets behind a reverse proxy in prod.

Core goals
- Monitor Proxmox VMs (power state, QGA, IPv4) and show host metrics (CPU/RAM/Disk).
- Provide admin actions (start/stop/reset, bulk, QGA exec).
- Alert when resource usage exceeds thresholds.
- Detect VMs removed from Proxmox and reconcile the inventory.

Key concepts
- Asset: an entry in the DB representing a host or VM. Some assets are “mapped” to Proxmox via `(node, vmid)`.
- Metrics: time series of CPU/RAM/Disk usage for each asset (ingested by an Agent/Collector).
- Live status cache: a lightweight, scheduled snapshot of VM state, QGA reachability, IP and top process.
- Discovery: compares current Proxmox inventory to assets to propose updates and detect missing VMs.

High‑level data flow
- Ingest: An external Agent posts metrics to the backend. The backend saves to DB.
- Query: UI calls `/api/assets` to list assets (fast DB read). It overlays `/api/status/live` to show VM state without slowing the list.
- Live refresh: A scheduler polls Proxmox and caches a `LiveStatus` per asset. UI refreshes every ~30s.
- Discovery: An admin action calls Proxmox inventory (cluster‑wide with fallback), compares with DB, and shows proposals + missing VMs.
- Admin actions: UI calls `/api/admin/...`; backend calls Proxmox (WebClient) and returns results.

Non‑goals
- Heavy synchronous calls in list views (no per‑row Proxmox calls during listing — it would be slow and brittle).

Why these choices
- Separation of concerns: DB list is fast and consistent; live data is cached by a scheduler and overlaid in the UI.
- Resilience: If Proxmox is down, the assets list still loads (using last metrics); live panel just shows stale/missing.
- UX: Periodic refresh without flicker; toast notifications; skeleton loaders.

