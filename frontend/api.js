const cfg = {
  get baseUrl() { return localStorage.getItem('ops.baseUrl') || 'http://localhost:8060'; },
  set baseUrl(v) { localStorage.setItem('ops.baseUrl', v); },
  get apiKey() { return localStorage.getItem('ops.apiKey') || ''; },
  set apiKey(v) { localStorage.setItem('ops.apiKey', v); },
  get adminKey() { return localStorage.getItem('ops.adminKey') || ''; },
  set adminKey(v) { localStorage.setItem('ops.adminKey', v); },
};

function headers(isAdmin = false) {
  const h = { 'Content-Type': 'application/json' };
  if (!isAdmin && cfg.apiKey) h['X-API-KEY'] = cfg.apiKey;
  if (isAdmin && cfg.adminKey) h['X-ADMIN-API-KEY'] = cfg.adminKey;
  return h;
}

async function jsonFetch(path, opts = {}) {
  const isAdmin = path.startsWith('/api/admin/');
  const res = await fetch(cfg.baseUrl + path, {
    ...opts,
    headers: { ...(opts.headers || {}), ...headers(isAdmin) },
  });
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`;
    try { const err = await res.json(); msg = err.message || msg; } catch {}
    throw new Error(msg);
  }
  const ct = res.headers.get('content-type') || '';
  return ct.includes('application/json') ? res.json() : res.text();
}

export const api = {
  cfg,
  listAssets: (q = '') => jsonFetch(`/api/assets${q ? `?q=${encodeURIComponent(q)}` : ''}`),
  getAsset: (hostname) => jsonFetch(`/api/assets/${encodeURIComponent(hostname)}`),
  latestMetrics: (hostname, limit = 50) => jsonFetch(`/api/assets/${encodeURIComponent(hostname)}/metrics?limit=${limit}`),
  rangeMetrics: (hostname, fromIso, toIso) => jsonFetch(`/api/assets/${encodeURIComponent(hostname)}/metrics?from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`),
  summary: (hostname, fromIso, toIso) => jsonFetch(`/api/assets/${encodeURIComponent(hostname)}/metrics/summary?from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`),
  patchAsset: (hostname, body) => jsonFetch(`/api/assets/${encodeURIComponent(hostname)}`, { method: 'PATCH', body: JSON.stringify(body) }),

  // Admin routes
  adminStart: (node, vmid) => jsonFetch(`/api/admin/vm/${encodeURIComponent(node)}/${vmid}/start`, { method: 'POST' }),
  adminShutdown: (node, vmid) => jsonFetch(`/api/admin/vm/${encodeURIComponent(node)}/${vmid}/shutdown`, { method: 'POST' }),
  adminStop: (node, vmid) => jsonFetch(`/api/admin/vm/${encodeURIComponent(node)}/${vmid}/stop`, { method: 'POST' }),
  adminReset: (node, vmid) => jsonFetch(`/api/admin/vm/${encodeURIComponent(node)}/${vmid}/reset`, { method: 'POST' }),
  adminExec: (node, vmid, commandArr, input, timeoutSec = 10) => jsonFetch(`/api/admin/exec/${encodeURIComponent(node)}/${vmid}`, {
    method: 'POST',
    body: JSON.stringify({ command: commandArr, input, timeoutSec })
  }),
};

