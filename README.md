# CAUREQ Ops Board

Operations dashboard for Proxmox VMs and hosts: inventory, live status, metrics, alerts, and admin actions. Backend is Spring Boot; frontend is Angular.

## Quick Start

- Start Postgres (Docker):
  - `docker compose -f docker/docker-compose.yml up -d`
- Configure environment
  - Copy `.env.example` to `.env` and fill values (tokens/keys). Spring reads env vars directly; your shell can export them or you can use your service manager to set them.
- Run backend (port 8060)
  - `mvn spring-boot:run`
- Run frontend (Angular dev server on 4200)
  - In `frontend-angular`: `npm install` then `npm start`
  - The dev proxy forwards `/api` to `http://localhost:8060`
- Open UI
  - http://localhost:4200
  - Click Settings (top right) and set Base URL (optional if proxy), X-API-KEY, and X-ADMIN-API-KEY

## Environment Variables

- Database
  - `DB_URL` (default `jdbc:postgresql://localhost:5433/opsdb`)
  - `DB_USER`, `DB_PASS`
- API keys
  - `INGEST_API_KEY` (for non-admin /api)
  - `ADMIN_API_KEY` (for /api/admin)
- Proxmox API
  - `PROXMOX_BASE_URL` (e.g., `https://pve:8006/api2/json`)
  - `PVE_TOKEN_ID`, `PVE_TOKEN_SECRET`
- App defaults
  - `APP_DEFAULT_NODE` (e.g., `pve01`)
  - Optional status capture: `APP_STATUS_TOUCH_LAST_SEEN`, `APP_STATUS_CAPTURE_TOP`, `APP_STATUS_CAPTURE_INTERVAL_MS`
- Admin network restriction
  - `ADMIN_ALLOW_IPS` (comma-separated list of IPs/CIDRs)

All of the above map to Spring Boot properties via relaxed binding.

## Features Overview

- Assets list (fast): DB-backed list with latest metrics; live VM status overlaid from cache (refresh ~30s)
- Live status: VM power, GA reachability, IPv4, and (optional) top process
- Alerts: runtime thresholds, list + ack
- Admin: start/stop/reset, exec via QGA, bulk actions
- Discovery: Proxmox inventory → proposals, Unknown VMs, Missing VMs
  - Clear mappings for missing
  - Archive missing (adds `retired` tag and clears mapping)
- UI polish: modern theme, skeletons, toasts, filters, badges

## Missing VM Detection

- The scheduler flags `vmState = "missing"` when Proxmox returns 404 for a mapped VM.
- Discovery uses cluster-wide listing (qemu+lxc) with per-node fallback, and confirms via `status/current` 404 to handle RBAC.
- In the UI:
  - Top bar shows a badge on Discovery when missing VMs are detected and auto-navigates to the page.
  - Assets toolbar has a "VM state" filter (Running/Missing/Stopped).
  - Use Discovery → Preview to Clear or Archive missing.

## Security and Secrets

- No secrets are stored in the repo; use env vars (`.env` for local only; do not commit it).
- Rotate any previously committed keys/tokens.
- Enable GitHub Secret Scanning + Push Protection.
- (Optional) Purge old secrets from history using BFG or `git filter-repo`.

## Troubleshooting

- Backend fails to start: `Connection to localhost:5433 refused`
  - Start Docker Postgres: `docker compose -f docker/docker-compose.yml up -d`
  - Or point `DB_URL` to your own Postgres
- Frontend 0 Unknown Error on /api
  - Use `npm start` (proxy), or set Base URL in Settings
  - Set API keys in Settings
- Admin 401
  - Set X-ADMIN-API-KEY in Settings
- Proxmox errors
  - Token must allow cluster resources or per-node listing and `status/current`

## Scripts

- Postgres (Docker): `docker/docker-compose.yml`
- Agent/Collector (Windows PowerShell): see `Agent/` directory

