# REST API Reference (Overview)

Note: This is a quick map. Inspect controllers under `src/main/java/.../api/` for exact shapes.

Public APIs (`/api`)
- GET `/api/assets?q&limit&offset&includeRetired=false` ? AssetListItemDTO[]
- GET `/api/assets/{hostname}` ? AssetDetailDTO
- GET `/api/assets/{hostname}/metrics?limit` ? MetricPointDTO[]
- GET `/api/assets/{hostname}/metrics?from&to` ? MetricPointDTO[]
- GET `/api/assets/{hostname}/metrics/summary?from&to` ? MetricSummaryDTO
- PATCH `/api/assets/{hostname}` ? Update owner/tags
- GET `/api/status/live` ? LiveStatus[]
- GET `/api/status/live/{hostname}` ? LiveStatus
- Alerts
  - GET `/api/alerts?host&ack&limit&offset`
  - POST `/api/alerts/{id}/ack`

Admin APIs (`/api/admin`) — require `X-ADMIN-API-KEY`
- VM actions
  - POST `/api/admin/vm/{node}/{vmid}/start|shutdown|stop|reset`
  - POST `/api/admin/vm/bulk/{action}` ? array of outcomes
- Diagnostics
  - GET `/api/admin/diag/host/{hostname}?top=3` ? top CPU/RAM processes via QGA or local diag
- Discovery
  - GET `/api/admin/discovery/preview` ? { toUpdate, unknownVms, missingAssets }
  - POST `/api/admin/discovery/apply` ({ proposals })
  - POST `/api/admin/discovery/clear-missing` ({ hostnames })
  - POST `/api/admin/discovery/archive-missing` ({ hostnames })
- Alerts config
  - GET/PUT `/api/admin/alerts/config` ? thresholds

Headers
- User APIs: `X-API-KEY` (if enabled)
- Admin APIs: `X-ADMIN-API-KEY`
