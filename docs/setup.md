# Setup, Run, Troubleshoot

Prereqs
- Java 17, Maven, Node.js 18+, Docker Desktop (for local Postgres)

Environment
- Copy `.env.example` to `.env` and fill values (do not commit `.env`).
- Export env vars in your shell or set them in your service manager.

Start DB
- `docker compose -f docker/docker-compose.yml up -d`
- Verify: `Test-NetConnection localhost -Port 5433` (Windows) or `nc -zv localhost 5433` (macOS/Linux)

Run backend
- `mvn spring-boot:run`
- Backend: http://localhost:8060

Run frontend (dev)
- `cd frontend-angular && npm install && npm start`
- UI: http://localhost:4200 (proxy sends `/api` to `http://localhost:8060`)

Settings in UI
- Base URL (optional with proxy)
- X-API-KEY and X-ADMIN-API-KEY — must match backend env

Troubleshooting
- DB connection refused on 5433 ? Start Docker or point `DB_URL` to your own DB.
- 0 Unknown Error in UI ? Restart `npm start`, ensure proxyConfig is set, or set Base URL in Settings.
- 401 on admin actions ? Set Admin key in Settings.
- Proxmox listing issues ? Ensure token can call `/cluster/resources?type=vm` or per-node listing and `status/current`.
- CORS ? `CorsConfig` enables localhost:4200 by default; adjust in `config` if needed.
