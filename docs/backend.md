# Backend (Spring Boot)

This section explains the backend structure, the main classes, and why they exist.

- Entry point: `src/main/java/org/caureq/caureqopsboard/CaureqOpsBoardApplication.java`
- Configuration: `config/` (CORS, properties)
- APIs: `api/` (controllers)
- Services: `service/` (business logic and external integrations)
- Repositories: `repo/` (Spring Data JPA)
- Domain: `domain/` (JPA entities)

## Controllers

- `api/AssetController.java`
  - GET `/api/assets`: fast list of assets (from DB) with optional `q`, pagination `limit/offset`, and `includeRetired=false` by default.
  - GET `/api/assets/{hostname}`: asset detail (DB fields). Live status is requested separately from the UI for speed.
  - Why: avoid blocking on Proxmox during listing.

- `api/MetricController.java`
  - GET `/api/assets/{hostname}/metrics` latest or range, and summary.

- `api/AlertsController.java`, `api/AdminAlertsController.java`
  - User endpoints to list/ack alerts; admin endpoints to get/set thresholds.

- `api/AdminVmController.java`, `api/AdminDiagController.java`, `api/AdminAssetController.java`
  - Admin endpoints: VM actions, diagnostics via QGA, update mapping `(node, vmid)`.

- `api/AdminDiscoveryController.java`
  - GET `/api/admin/discovery/preview`: proposals (update mappings), unknown VMs, missing assets.
  - POST `/api/admin/discovery/apply`: apply proposals.
  - POST `/api/admin/discovery/clear-missing`: clear `(node, vmid)` for listed hostnames.
  - POST `/api/admin/discovery/archive-missing`: also add `retired` tag.

- `api/StatusController.java`
  - GET `/api/status/live` and `/api/status/live/{hostname}`: returns the in-memory live cache.

## Services

- `service/AssetQueryService.java`
  - Lists assets (pageable) from DB; does NOT call Proxmox per row. Computes status from `lastSeen` windows (UP/STALE/DOWN). Loads the latest metric per asset via one batch query.
  - Why: scales to many assets and remains fast even if Proxmox is slow.

- `service/MetricQueryService.java`
  - Fetches metrics/time ranges and simple summaries for charts.

- `service/LiveStatusService.java`
  - Scheduled job that builds an in-memory `LiveStatus` map: VM power state, QGA reachability, IPv4 and optional top process. Marks `vmState="missing"` if Proxmox returns 404.
  - Why: decouple live polling from requests; reduce load on Proxmox.

- `service/InventoryDiscoveryService.java`
  - Compares Proxmox inventory to DB:
    - First try cluster-wide `GET /cluster/resources?type=vm` (QEMU+LXC)
    - Fallback: per-node `/nodes/{node}/qemu` and `/lxc`
    - Track presence by `vmid` and `node#vmid`
    - Confirm missing via `status/current` to tolerate RBAC filters
  - Produces: proposals (update mapping), unknown VMs, missing assets.
  - Provides: `clearMappings()` and `archiveMissing()` helpers.

- `service/ProxmoxClient.java`
  - Wraps Proxmox HTTP calls (actions, QGA, status/current) using `WebClient`. Centralized error handling that throws `ProxmoxApiException` with HTTP status.

- `service/AlertService.java` and `service/alerts/*`
  - Keeps configurable thresholds, persists alerts, and exposes query/update methods.

- `service/IngestService.java`
  - Receives metrics from Agents and stores them (wired in `api/IngestController`).

- `service/ActionService.java`
  - Provides QGA exec and helpers used by diagnostics and live top capture.

## Repositories & Entities

- `domain/Asset`, `domain/Metric`, `domain/AlertRecord` (JPA entities)
- `repo/AssetRepo`, `repo/MetricRepo`, `repo/AlertRepo`
  - Notable: `MetricRepo.findLatestForAssets(Collection<Asset>)` to batch the “latest metric per asset” query.

## Security & Config

- `security/ApiKeyFilter` (user key), `security/ApiKeyAdminFilter` (admin key)
- `config/AppProps` and `application.yml` map env vars:
  - `ADMIN_API_KEY`, `INGEST_API_KEY`, `PVE_TOKEN_ID`, `PVE_TOKEN_SECRET`, DB envs, etc.

## Scheduling & Performance

- Live status refresh default: `app.status.refresh-ms: 30000` (every 30s).
- Optional capture of top process is throttled per host (`capture-interval-ms`).
- No per-row Proxmox calls in list or details endpoint; UI overlays live info by calling `/api/status/live`.
