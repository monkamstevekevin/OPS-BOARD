# Contributing Guide

Style & principles
- Keep changes focused and small; explain the “why” in commit messages.
- Prefer fast endpoints and cache live data on the backend when possible.
- Favor clarity over cleverness; write small, testable methods.

Commits
- Conventional messages help: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`
- One logical change per commit; describe what and why.

Backend
- Add APIs in `api/`, logic in `service/`, data in `repo/`.
- Handle Proxmox errors with `ProxmoxApiException` and return useful messages.
- Avoid per-row remote calls in list endpoints; if needed, add a scheduler/cache.

Frontend
- Keep templates simple; avoid complex expressions in HTML.
- Show non-blocking loaders and toasts for long actions.
- Use `ApiService` and the interceptor for keys.

Docs
- Update `docs/` and `README.md` when adding notable features.
- Include rationale for design choices in PRs to help new contributors learn.
