Frontend Angular for CAUREQ Ops Board

Prerequisites
- Node.js 18+ and npm installed
- Optional: Angular CLI (`npm i -g @angular/cli`), but not strictly required (use `npm start`).

Setup
1) Open a terminal in `frontend-angular/`
2) Run: `npm install`

Run (dev)
- Backend at `http://localhost:8060` (default).
- Start dev server with API proxy to avoid CORS:
  - `npm start` (alias of `ng serve --proxy-config proxy.conf.json`)
  - Open http://localhost:4200

Configure API keys
- Click the gear (⚙) in the top bar to set:
  - Admin Key (X-ADMIN-API-KEY) for `/api/admin/*`
  - API Key (X-API-KEY) for ingest/asset PATCH
  - Base URL is used only for absolute calls; proxy covers `/api/*` in dev.

Build
- `npm run build` → outputs to `dist/`

Notes
- The proxy routes `/api/*` to `http://localhost:8060` in dev.
- For production behind the same domain, configure your web server to reverse-proxy `/api/` to the backend or enable CORS on the backend.

