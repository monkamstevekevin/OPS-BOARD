import { api } from './api.js';
import { lineChart } from './charts.js';

const appEl = document.getElementById('app');

function $(sel, root = document) { return root.querySelector(sel); }
function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') el.className = v; else if (k.startsWith('on')) el.addEventListener(k.slice(2), v); else el.setAttribute(k, v);
  }
  for (const c of children) {
    el.append(c?.nodeType ? c : document.createTextNode(c ?? ''));
  }
  return el;
}

function statusBadge(status) {
  const cls = status === 'UP' ? 'up' : status === 'STALE' ? 'stale' : 'down';
  return h('span', { class: `status ${cls}` }, status || 'DOWN');
}

async function viewAssets() {
  const q = new URL(location.href).searchParams.get('q') || '';
  const wrap = h('div');
  const search = h('div', { class: 'card' },
    h('label', {}, 'Recherche'),
    h('input', { id: 'search', type: 'text', value: q, placeholder: 'hostname...' }),
    h('div', { style: 'margin-top:8px' },
      h('button', { class: 'btn primary', onClick: () => {
        const v = $('#search', search).value.trim();
        location.hash = `#/assets${v ? `?q=${encodeURIComponent(v)}` : ''}`;
      } }, 'Rechercher')
    )
  );
  wrap.append(search);

  const listCard = h('div', { class: 'card' }, h('div', { class: 'muted' }, 'Chargement...'));
  wrap.append(listCard);
  appEl.replaceChildren(wrap);

  try {
    const items = await api.listAssets(q);
    const table = h('table');
    table.append(
      h('thead', {}, h('tr', {},
        h('th', {}, 'Hostname'), h('th', {}, 'IP'), h('th', {}, 'OS'), h('th', {}, 'Status'), h('th', {}, 'Last Seen'), h('th', {}, 'CPU'), h('th', {}, 'RAM'), h('th', {}, 'Disk')
      )),
      h('tbody', {}, ...items.map(it => h('tr', { class: 'row-item' },
        h('td', {}, h('a', { href: `#/asset/${encodeURIComponent(it.hostname)}` }, it.hostname)),
        h('td', {}, it.ip || ''),
        h('td', {}, it.os || ''),
        h('td', {}, statusBadge(it.status)),
        h('td', {}, it.lastSeen ? new Date(it.lastSeen).toLocaleString() : ''),
        h('td', {}, it.cpu != null ? `${it.cpu.toFixed(0)}%` : ''),
        h('td', {}, it.ram != null ? `${it.ram.toFixed(0)}%` : ''),
        h('td', {}, it.disk != null ? `${it.disk.toFixed(0)}%` : '')
      )))
    );
    listCard.replaceChildren(h('h3', {}, 'Assets'), table);
  } catch (e) {
    listCard.replaceChildren(h('div', { class: 'muted' }, `Erreur: ${e.message}`));
  }
}

async function viewAssetDetail(hostname) {
  const wrap = h('div');
  wrap.append(h('div', {}, h('a', { href: '#/assets' }, '← retour')));

  const cardInfo = h('div', { class: 'card' }, h('div', { class: 'muted' }, 'Chargement...'));
  const cardCharts = h('div', { class: 'card' }, h('div', { class: 'muted' }, 'Chargement...'));
  wrap.append(cardInfo, cardCharts);
  appEl.replaceChildren(wrap);

  try {
    const info = await api.getAsset(hostname);
    const latest = await api.latestMetrics(hostname, 50);

    cardInfo.replaceChildren(
      h('h3', {}, info.hostname),
      h('div', { class: 'row' },
        h('div', { class: 'col' }, h('label', {}, 'IP'), h('div', {}, info.ip || '')),
        h('div', { class: 'col' }, h('label', {}, 'OS'), h('div', {}, info.os || '')),
        h('div', { class: 'col' }, h('label', {}, 'Owner'), h('div', {}, info.owner || '')),
        h('div', { class: 'col' }, h('label', {}, 'Last Seen'), h('div', {}, info.lastSeen ? new Date(info.lastSeen).toLocaleString() : '')),
      ),
      h('div', { class: 'row' },
        h('div', { class: 'col' }, h('label', {}, 'Tags'), h('div', {}, (info.tags || []).join(', ')))
      )
    );

    const toSeries = (points, key) => points.map(p => ({ ts: p.ts, v: p[key] }));
    const cpu = toSeries(latest, 'cpu');
    const ram = toSeries(latest, 'ram');
    const dsk = toSeries(latest, 'disk');

    const cpuCanvas = h('canvas', { class: 'chart' });
    const ramCanvas = h('canvas', { class: 'chart' });
    const dskCanvas = h('canvas', { class: 'chart' });

    cardCharts.replaceChildren(
      h('h3', {}, 'Metrics (latest)'),
      h('div', { class: 'row' },
        h('div', { class: 'col' }, h('label', {}, 'CPU %'), cpuCanvas),
        h('div', { class: 'col' }, h('label', {}, 'RAM %'), ramCanvas),
        h('div', { class: 'col' }, h('label', {}, 'Disk %'), dskCanvas),
      )
    );

    requestAnimationFrame(() => {
      lineChart(cpuCanvas, cpu, { yMin: 0, yMax: 100, color: '#f59e0b' });
      lineChart(ramCanvas, ram, { yMin: 0, yMax: 100, color: '#22c55e' });
      lineChart(dskCanvas, dsk, { yMin: 0, yMax: 100, color: '#0ea5e9' });
    });
  } catch (e) {
    cardInfo.replaceChildren(h('div', { class: 'muted' }, `Erreur: ${e.message}`));
    cardCharts.replaceChildren();
  }
}

function adminView() {
  const wrap = h('div');
  const card = h('div', { class: 'card' });
  card.append(h('h3', {}, 'Actions Admin (node/vmid)'));

  const node = h('input', { placeholder: 'node (ex: Caureqlab)' });
  const vmid = h('input', { placeholder: 'vmid (ex: 101)', type: 'number' });
  const out = h('pre', { class: 'muted' });
  const execProg = h('input', { placeholder: 'program (ex: cmd.exe or bash)' });
  const execArgs = h('input', { placeholder: 'args (ex: /c whoami or -lc whoami)' });
  const execIn = h('textarea', { placeholder: 'stdin (optional)', rows: 3 });
  const timeout = h('input', { placeholder: 'timeout sec (default 10)', type: 'number' });

  const doCall = async (fn, label) => {
    try { out.textContent = label + ': ' + JSON.stringify(await fn(), null, 2); }
    catch (e) { out.textContent = 'Erreur: ' + e.message; }
  };

  card.append(
    h('div', { class: 'row' }, h('div', { class: 'col' }, h('label', {}, 'Node'), node), h('div', { class: 'col' }, h('label', {}, 'VMID'), vmid)),
    h('div', { class: 'actions' },
      h('button', { class: 'btn primary', onClick: () => doCall(() => api.adminStart(node.value, vmid.value), 'start') }, 'Start'),
      h('button', { class: 'btn', onClick: () => doCall(() => api.adminShutdown(node.value, vmid.value), 'shutdown') }, 'Shutdown'),
      h('button', { class: 'btn', onClick: () => doCall(() => api.adminStop(node.value, vmid.value), 'stop') }, 'Stop'),
      h('button', { class: 'btn', onClick: () => doCall(() => api.adminReset(node.value, vmid.value), 'reset') }, 'Reset'),
    ),
    h('hr'),
    h('h3', {}, 'Exec via QGA'),
    h('div', { class: 'row' },
      h('div', { class: 'col' }, h('label', {}, 'Program'), execProg),
      h('div', { class: 'col' }, h('label', {}, 'Args'), execArgs),
    ),
    h('label', {}, 'STDIN (optional)'), execIn,
    h('div', { class: 'row' }, h('div', { class: 'col' }, h('label', {}, 'Timeout (sec)'), timeout)),
    h('div', { class: 'actions' },
      h('button', { class: 'btn', onClick: () => doCall(() => {
        const cmd = [execProg.value, ...execArgs.value.split(' ').filter(Boolean)];
        const t = parseInt(timeout.value || '10', 10);
        return api.adminExec(node.value, vmid.value, cmd, execIn.value, t);
      }, 'exec') }, 'Exec'),
    ),
    h('div', {}, out)
  );

  wrap.append(card);
  appEl.replaceChildren(wrap);
}

function route() {
  const hash = location.hash || '#/assets';
  const [_, r, ...rest] = hash.slice(1).split('/');
  if (r === 'assets' && rest.length === 0) return viewAssets();
  if (r === 'assets' && rest.length >= 1 && rest[0].startsWith('?')) return viewAssets();
  if (r === 'asset' && rest.length >= 1) return viewAssetDetail(decodeURIComponent(rest[0]));
  if (r === 'admin') return adminView();
  return viewAssets();
}

// Settings drawer
const btnSettings = document.getElementById('btnSettings');
const drawer = document.getElementById('settings');
const btnSave = document.getElementById('btnSaveCfg');
const btnClose = document.getElementById('btnCloseCfg');
const cfgBaseUrl = document.getElementById('cfgBaseUrl');
const cfgApiKey = document.getElementById('cfgApiKey');
const cfgAdminKey = document.getElementById('cfgAdminKey');

function openSettings() {
  cfgBaseUrl.value = api.cfg.baseUrl;
  cfgApiKey.value = api.cfg.apiKey;
  cfgAdminKey.value = api.cfg.adminKey;
  drawer.classList.remove('hidden');
}
function closeSettings() { drawer.classList.add('hidden'); }
btnSettings.addEventListener('click', openSettings);
btnClose.addEventListener('click', closeSettings);
btnSave.addEventListener('click', () => {
  api.cfg.baseUrl = cfgBaseUrl.value.trim();
  api.cfg.apiKey = cfgApiKey.value.trim();
  api.cfg.adminKey = cfgAdminKey.value.trim();
  closeSettings();
  route();
});

window.addEventListener('hashchange', route);
route();

