import { Component, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, AssetListItemDTO, LiveStatus } from '../services/api.service';
import { ToastService } from '../services/toast.service';

@Component({
  selector: 'app-assets-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="card">
      <div class="toolbar">
        <div class="col">
          <label>Recherche</label>
          <div class="input-group">
            <span class="icon">🔎</span>
            <input [(ngModel)]="q" placeholder="hostname..." (input)="onSearchInput()" (keyup.enter)="search()" />
          </div>
        </div>
        <div class="col">
          <label>Status</label>
          <select [(ngModel)]="statusFilter" (change)="applyFilters()">
            <option value="ALL">Tous</option>
            <option value="UP">UP</option>
            <option value="STALE">STALE</option>
            <option value="DOWN">DOWN</option>
          </select>
        </div>
        <div class="col">
          <label>VM state</label>
          <select [(ngModel)]="vmFilter" (change)="applyFilters()">
            <option value="ALL">All</option>
            <option value="RUNNING">Running</option>
            <option value="MISSING">Missing</option>
            <option value="STOPPED">Stopped</option>
          </select>
        </div>
        <div class="col">
          <label>Tri</label>
          <select [(ngModel)]="sortBy" (change)="applyFilters()">
            <option value="hostname">Hostname</option>
            <option value="cpu">CPU</option>
            <option value="ram">RAM</option>
            <option value="disk">Disk</option>
            <option value="lastSeen">Last Seen</option>
          </select>
        </div>
        <div class="col" style="align-self:center">
          <button class="btn primary" (click)="search()">Rechercher</button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <div class="col">
          <label>Bulk action</label>
          <select [(ngModel)]="bulkAction">
            <option value="start">Start</option>
            <option value="shutdown">Shutdown</option>
            <option value="stop">Stop</option>
            <option value="reset">Reset</option>
          </select>
        </div>
        <div class="col" style="align-self:center">
          <button class="btn primary" [disabled]="selectedCount()===0" (click)="runBulkSelected()">Run on {{selectedCount()}} selected</button>
        </div>
      </div>
    </div>
    <div class="card" *ngIf="loading()">
      <div class="skeleton" style="padding:12px">
        <div class="sk-rect" style="width:40%;margin-bottom:10px"></div>
        <div *ngFor="let i of [1,2,3,4,5]" style="display:grid;grid-template-columns:repeat(6,1fr);gap:12px;margin:10px 0">
          <div class="sk-rect"></div>
          <div class="sk-rect"></div>
          <div class="sk-rect"></div>
          <div class="sk-rect"></div>
          <div class="sk-rect"></div>
          <div class="sk-rect"></div>
        </div>
      </div>
    </div>
    <div class="card" *ngIf="error()" class="muted">Erreur: {{error()}}</div>
    <div class="card" *ngIf="!loading() && !error()">
      <h3>Assets <span class="muted" *ngIf="refreshing()">· mise à jour…</span></h3>
      <table class="assets-table">
        <thead>
          <tr>
            <th><input type="checkbox" [checked]="allSelected()" (change)="toggleAll($event)" /></th>
            <th>Hostname</th>
            <th>IP</th>
            <th>OS</th>
            <th>Status <span class="help" data-tip="Power state only (running=UP, stopped=DOWN)">i</span></th>
            <th>VM <span class="help" data-tip="VM power + GA (QEMU Guest Agent)">i</span></th>
            <th>IP (Live)</th>
            <th>Last Seen</th>
            <th>CPU <span class="help" data-tip="Used % of total VM capacity">i</span></th>
            <th>RAM <span class="help" data-tip="Used %">i</span></th>
            <th>Disk <span class="help" data-tip="Used %">i</span></th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let it of filtered()">
            <td><input type="checkbox" [checked]="isSelected(it.hostname)" (change)="toggleOne(it.hostname, $event)" /></td>
            <td>
              <a [routerLink]="['/asset', it.hostname]">{{it.hostname}}</a>
              <span *ngIf="alertsMap[it.hostname]" class="badge">{{alertsMap[it.hostname]}}</span>
              <span *ngIf="liveMap[it.hostname]?.vmState==='missing'" class="badge">Missing</span>
            </td>
            <td>{{it.ip}}</td>
            <td>{{it.os}}</td>
            <td>
              <span class="status" [class.up]="it.status==='UP'" [class.stale]="it.status==='STALE'" [class.down]="it.status!=='UP' && it.status!=='STALE'">{{it.status || 'DOWN'}}</span>
            </td>
            <td>
              <span class="status" [class.up]="liveMap[it.hostname]?.vmState==='running'" [class.down]="liveMap[it.hostname]?.vmState!=='running'">{{ liveMap[it.hostname]?.vmState || '-' }}</span>
              <span class="status" [class.up]="liveMap[it.hostname]?.qgaUp" [class.down]="liveMap[it.hostname] && !liveMap[it.hostname]?.qgaUp">GA: {{ liveMap[it.hostname]?.qgaUp ? 'UP' : 'DOWN' }}</span>
              <div class="muted" *ngIf="liveMap[it.hostname]?.topPid">
                PID {{liveMap[it.hostname]?.topPid}} · {{liveMap[it.hostname]?.topName}} · CPU {{liveMap[it.hostname]?.topCpu | number:'1.0-0'}}%
              </div>
            </td>
            <td>{{ liveMap[it.hostname]?.ipv4 || '' }}</td>
            <td>{{it.lastSeen ? (it.lastSeen | date:'short') : ''}}</td>
            <td>{{it.cpu != null ? (it.cpu | number:'1.0-0') + '%' : ''}}</td>
            <td>{{it.ram != null ? (it.ram | number:'1.0-0') + '%' : ''}}</td>
            <td>{{it.disk != null ? (it.disk | number:'1.0-0') + '%' : ''}}</td>
            <td>
              <div class="actions">
                <button class="btn" (click)="confirm('start', it.hostname)">Start</button>
                <button class="btn" (click)="confirm('shutdown', it.hostname)">Shutdown</button>
                <button class="btn" (click)="confirm('stop', it.hostname)">Stop</button>
                <button class="btn" (click)="confirm('reset', it.hostname)">Reset</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Confirm modal -->
    <div class="modal" [class.hidden]="!modalOpen">
      <div class="modal-content">
        <h3>Confirm {{pendingAction?.action | uppercase}}</h3>
        <div class="muted" *ngIf="pendingAction">
          Host: {{pendingAction.hostname}} · Node: {{pendingAction.node}} · VMID: {{pendingAction.vmid}}
        </div>
        <div class="modal-actions">
          <button class="btn" (click)="modalOpen=false">Cancel</button>
          <button class="btn primary" (click)="runPending()">Run</button>
        </div>
      </div>
    </div>
  `
})
export class AssetsListComponent {
  q = '';
  items = signal<AssetListItemDTO[]>([]);
  filtered = signal<AssetListItemDTO[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  statusFilter: 'ALL'|'UP'|'STALE'|'DOWN' = 'ALL';
  vmFilter: 'ALL'|'RUNNING'|'MISSING'|'STOPPED' = 'ALL';
  sortBy: 'hostname'|'cpu'|'ram'|'disk'|'lastSeen' = 'hostname';
  liveMap: { [hostname: string]: LiveStatus } = {};
  liveTimer: any;
  itemsTimer: any;
  alertsMap: { [hostname: string]: number } = {};
  alertsTimer: any;
  refreshing = signal(false);
  searchTimer: any;
  modalOpen = false;
  pendingAction: { action: 'start'|'shutdown'|'stop'|'reset', hostname: string, node: string, vmid: number } | null = null;
  selection = new Set<string>();
  bulkAction: 'start'|'shutdown'|'stop'|'reset' = 'start';

  constructor(private api: ApiService, private toasts: ToastService) {
    this.fetch(false);
    this.pullLive();
    this.liveTimer = setInterval(()=> this.pullLive(), 30000);
    this.itemsTimer = setInterval(()=> this.fetch(true), 30000);
    this.pullAlerts();
    this.alertsTimer = setInterval(()=> this.pullAlerts(), 15000);
  }

  search(){ this.fetch(); }

  fetch(silent: boolean = false){
    if (silent) { this.refreshing.set(true); } else { this.loading.set(true); this.error.set(null); }
    this.api.listAssets(this.q, { includeRetired: false }).subscribe({
      next: (res) => {
        this.items.set(res); this.applyFilters();
        if (silent) { this.refreshing.set(false); } else { this.loading.set(false); }
      },
      error: (e) => {
        if (silent) { this.refreshing.set(false); /* keep old data, no flicker */ }
        else { this.error.set(e.message || 'error'); this.loading.set(false); }
      }
    });
  }

  applyFilters(){
    let arr = [...this.items()];
    if (this.statusFilter !== 'ALL') arr = arr.filter(a => (a.status||'DOWN') === this.statusFilter);
    if (this.vmFilter !== 'ALL') {
      arr = arr.filter(a => {
        const vm = this.liveMap[a.hostname];
        const st = vm?.vmState || '';
        if (this.vmFilter === 'RUNNING') return st === 'running';
        if (this.vmFilter === 'MISSING') return st === 'missing';
        if (this.vmFilter === 'STOPPED') return st !== '' && st !== 'running' && st !== 'missing';
        return true;
      });
    }
    const key = this.sortBy;
    arr.sort((a:any,b:any)=>{
      const av = key==='lastSeen' ? (a.lastSeen? new Date(a.lastSeen).getTime():0) : (a[key] ?? -Infinity);
      const bv = key==='lastSeen' ? (b.lastSeen? new Date(b.lastSeen).getTime():0) : (b[key] ?? -Infinity);
      if (key==='hostname') return String(a.hostname).localeCompare(String(b.hostname));
      return (bv - av);
    });
    this.filtered.set(arr);
  }

  onSearchInput(){
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(()=> this.fetch(true), 400);
  }

  confirm(action: 'start'|'shutdown'|'stop'|'reset', hostname: string){
    const live = this.liveMap[hostname];
    if (!live || !live.node || !live.vmid) { alert('Node/VMID not available yet. Open the asset page to set mapping, or wait for live status.'); return; }
    this.pendingAction = { action, hostname, node: live.node, vmid: Number(live.vmid) } as any;
    this.modalOpen = true;
  }
  runPending(){
    if (!this.pendingAction) return;
    const { action, node, vmid } = this.pendingAction;
    const call = action==='start'? this.api.adminStart(node, vmid)
      : action==='shutdown'? this.api.adminShutdown(node, vmid)
      : action==='stop'? this.api.adminStop(node, vmid)
      : this.api.adminReset(node, vmid);
    call.subscribe({ next: _ => { this.modalOpen=false; this.pullLive(); this.toasts.success(`${action} OK on ${this.pendingAction?.hostname}`); }, error: e => { this.toasts.error(e.message || 'error'); this.modalOpen=false; } });
  }

  // Selection helpers for bulk
  isSelected(h: string){ return this.selection.has(h); }
  selectedCount(){ return this.selection.size; }
  toggleOne(h: string, ev: any){ if (ev?.target?.checked) this.selection.add(h); else this.selection.delete(h); }
  allSelected(){ const arr = this.filtered(); return arr.length>0 && arr.every(it => this.selection.has(it.hostname)); }
  toggleAll(ev: any){ const checked = !!ev?.target?.checked; const arr = this.filtered(); if (checked){ arr.forEach(it => this.selection.add(it.hostname)); } else { arr.forEach(it => this.selection.delete(it.hostname)); } }
  runBulkSelected(){
    if (this.selection.size===0) return;
    const hostnames = Array.from(this.selection);
    this.api.adminBulk(this.bulkAction, { hostnames }).subscribe({
      next: (res:any[]) => {
        const ok = res.filter(r => r.outcome==='OK').length;
        const err = res.length - ok;
        this.toasts.success(`${this.bulkAction} OK on ${ok} host(s)`);
        if (err>0) this.toasts.error(`${err} failed`);
        this.pullLive();
      },
      error: e => this.toasts.error(e.message || 'bulk error')
    });
  }

  pullLive(){
    this.api.liveAll().subscribe({
      next: list => {
        const m: any = {};
        list.forEach(ls => m[ls.hostname] = ls);
        this.liveMap = m; this.applyFilters();
      },
      error: _ => {}
    });
  }

  pullAlerts(){
    this.api.listAlerts().subscribe({
      next: list => {
        const map: any = {};
        list.forEach((a:any)=>{ if (!a.acknowledged) { map[a.hostname] = (map[a.hostname]||0)+1; }});
        this.alertsMap = map;
      },
      error: _ => {}
    });
  }

  ngOnDestroy(){ if (this.liveTimer) clearInterval(this.liveTimer); if (this.itemsTimer) clearInterval(this.itemsTimer); if (this.alertsTimer) clearInterval(this.alertsTimer); }
}
