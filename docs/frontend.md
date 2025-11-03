# Frontend (Angular)

The UI is an Angular 17 app using standalone components. It talks to the backend via `ApiService` and displays data with modern, readable styles.

Project layout (frontend-angular/src/app)
- app.component.ts — Shell: top bar, routes outlet, Settings drawer, toast container
- app.routes.ts — Routes: `/assets`, `/asset/:hostname`, `/alerts`, `/admin`, `/admin/discovery`
- components/
  - assets-list.component.ts — List with filters, selection, per-row actions, badges
  - asset-detail.component.ts — Detail view, metrics charts, diagnostics, mapping save
  - alerts.component.ts — Alerts list with thresholds and filters
  - admin.component.ts — VM admin actions, bulk execution, QGA exec
  - admin-discovery.component.ts — Discovery preview: proposals, unknown VMs, missing assets
- services/
  - api.service.ts — HTTP client for all endpoints
  - api-key.interceptor.ts — Injects X-API-KEY / X-ADMIN-API-KEY from Settings
  - toast.service.ts — Simple toasts (success/error/info)

Key UX patterns
- Non-blocking refresh: lists show data immediately from DB; live info refreshes every ~30s and overlays.
- Skeletons: show loading states without page flicker.
- Toasts: show success/failure feedback for actions and refreshes.
- Modern controls: inputs with icons, clear status chips, consistent typography/colors.

How to configure
- Click Settings (top right) and set:
  - Base URL (optional if using dev proxy)
  - X-API-KEY — for non-admin APIs
  - X-ADMIN-API-KEY — for admin actions and discovery

Why the UI calls two endpoints for Assets
- `/api/assets` returns the DB snapshot quickly.
- `/api/status/live` returns cached VM state (power/GA/IP) separately to avoid slow per-row Proxmox calls.

Missing VM visibility
- Top bar shows a badge on Discovery when missing VMs are detected; the app auto-navigates once to help you react.
- Assets list has a “VM state” filter (All/Running/Missing/Stopped).
- Discovery page can Clear or Archive missing.
