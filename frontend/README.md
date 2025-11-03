Frontend for CAUREQ Ops Board

Overview
- Static SPA (no build) using vanilla HTML/JS/CSS.
- Talks to the backend at a configurable base URL (defaults to http://localhost:8060).
- Features:
  - List assets with search
  - Asset detail with latest metrics and simple charts
  - Admin actions (start/shutdown/stop/reset, exec) when admin key + IP allowlist permit

Run (local dev)
1) Start the backend on http://localhost:8060
2) Serve this folder with any static server, e.g.:
   - Python: `python -m http.server 8061` (then open http://localhost:8061)
   - PowerShell (simple dev server with .NET): `dotnet tool install --global dotnet-serve` then `dotnet-serve -d . -p 8061`
     or any other static server you prefer

CORS
- Because this frontend is served from a different origin than the backend, CORS must be allowed on the backend.
- If you see CORS errors in the browser console, enable CORS in the Spring app (global CorsConfiguration or @CrossOrigin on controllers). I can add a small CORS config bean if you want.

Quick Test
- Open http://localhost:8061
- Click ⚙ and set Base URL (e.g., http://localhost:8060). Set API/Admin keys if needed.
- Navigate to “Assets” to list hosts; click one to view metrics.
- Use “Admin” to call VM actions or exec (requires admin key + allowlisted IP).

Config
- Use the top-right settings drawer in the UI to set:
  - API Base URL (e.g., http://localhost:8060)
  - X-API-KEY (for ingest/asset PATCH)
  - X-ADMIN-API-KEY (for /api/admin/* routes)
- Values are persisted in localStorage.

Notes
- Charts are minimal Canvas-based; no external libraries.
- Admin actions require backend IP allowlist to include your client IP.
