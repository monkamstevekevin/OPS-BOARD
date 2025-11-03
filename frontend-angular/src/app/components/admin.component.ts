import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, AssetListItemDTO, LiveStatus } from '../services/api.service';
import { ToastService } from '../services/toast.service';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="card">
      <h3>Actions Admin</h3>
      <div class="toolbar">
        <div class="col"><label>Node</label><input [(ngModel)]="node" placeholder="ex: Caureqlab"/></div>
        <div class="col"><label>VM</label>
          <select [(ngModel)]="selectedVmHost" (change)="onSelectVm()">
            <option [ngValue]="''">-- select VM --</option>
            <option *ngFor="let ls of liveList" [ngValue]="ls.hostname">{{ls.hostname}} ({{ls.node}}/{{ls.vmid}})</option>
          </select>
        </div>
        <div class="col"><label>VMID</label><input [(ngModel)]="vmid" type="number" placeholder="ex: 101"/></div>
        <div class="col" style="align-self:center">
          <div class="actions">
            <button class="btn primary" (click)="start()">Start</button>
            <button class="btn" (click)="shutdown()">Shutdown</button>
            <button class="btn" (click)="stop()">Stop</button>
            <button class="btn" (click)="reset()">Reset</button>
          </div>
        </div>
      </div>
      <div class="actions" style="margin-top:8px">
        <button class="btn primary" (click)="start()">Start</button>
        <button class="btn" (click)="shutdown()">Shutdown</button>
        <button class="btn" (click)="stop()">Stop</button>
        <button class="btn" (click)="reset()">Reset</button>
      </div>
      <hr>
      <h3>Exec via QGA</h3>
      <div class="toolbar">
        <div class="col"><label>Program</label><input [(ngModel)]="prog" placeholder="ex: cmd.exe ou bash"/></div>
        <div class="col"><label>Args</label><input [(ngModel)]="args" placeholder="ex: /c whoami ou -lc whoami"/></div>
        <div class="col" style="align-self:center"><button class="btn" (click)="exec()">Exec</button></div>
      </div>
      <label>STDIN (optional)</label>
      <textarea [(ngModel)]="stdin" rows="3"></textarea>
      <div class="row"><div class="col"><label>Timeout (sec)</label><input [(ngModel)]="timeout" type="number" placeholder="10"/></div></div>
    </div>

    <div class="card">
      <h3>Bulk VM Actions</h3>
      <div class="toolbar">
        <div class="col"><label>Action</label>
          <select [(ngModel)]="bulkAction">
            <option value="start">Start</option>
            <option value="shutdown">Shutdown</option>
            <option value="stop">Stop</option>
            <option value="reset">Reset</option>
          </select>
        </div>
        <div class="col"><label>Tag (optional)</label><input [(ngModel)]="bulkTag" placeholder="tag name" /></div>
        <div class="col" style="align-self:center"><button class="btn primary" (click)="bulk()">Run Bulk</button></div>
      </div>
      <label>Hostnames (one per line, optional)</label>
      <textarea [(ngModel)]="bulkHosts" rows="4" placeholder="vm-101-web
vm-102-db"></textarea>
      <pre class="muted">{{out()}}</pre>
    </div>

    <div class="card">
      <h3>Bulk from selection</h3>
      <div class="toolbar">
        <div class="col"><label>Filter</label>
          <div class="input-group">
            <span class="icon">🔎</span>
            <input [(ngModel)]="filter" placeholder="hostname..." (input)="debouncedLoad()" />
          </div>
        </div>
        <div class="col"><label>Action</label>
          <select [(ngModel)]="bulkAction">
            <option value="start">Start</option>
            <option value="shutdown">Shutdown</option>
            <option value="stop">Stop</option>
            <option value="reset">Reset</option>
          </select>
        </div>
        <div class="col" style="align-self:center">
          <button class="btn primary" [disabled]="selectedCount()===0" (click)="runBulk()">Run on {{selectedCount()}} selected</button>
        </div>
      </div>

      <div class="skeleton" *ngIf="loading" style="padding:12px">
        <div class="sk-rect" style="width:40%;margin-bottom:10px"></div>
        <div *ngFor="let i of [1,2,3,4]" class="sk-rect" style="margin:8px 0"></div>
      </div>

      <table class="assets-table" *ngIf="!loading">
        <thead>
          <tr>
            <th><input type="checkbox" [checked]="allSelected()" (change)="toggleAll($event)" /></th>
            <th>Hostname</th>
            <th>Status</th>
            <th>CPU</th>
            <th>RAM</th>
            <th>Disk</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let a of filteredAssets()">
            <td><input type="checkbox" [checked]="isSelected(a.hostname)" (change)="toggleOne(a.hostname, $event)" /></td>
            <td>{{a.hostname}}</td>
            <td><span class="status" [class.up]="a.status==='UP'" [class.stale]="a.status==='STALE'" [class.down]="a.status!=='UP' && a.status!=='STALE'">{{a.status}}</span></td>
            <td>{{a.cpu!=null? (a.cpu|number:'1.0-0')+'%':''}}</td>
            <td>{{a.ram!=null? (a.ram|number:'1.0-0')+'%':''}}</td>
            <td>{{a.disk!=null? (a.disk|number:'1.0-0')+'%':''}}</td>
          </tr>
        </tbody>
      </table>
    </div>
  `
})
export class AdminComponent {
  node = (localStorage.getItem('ops.defaultNode') || '');
  vmid: number | string = '';
  prog = '';
  args = '';
  stdin = '';
  timeout: number | string = '10';
  out = signal('');
  // Bulk
  bulkTag = '';
  bulkHosts = '';
  bulkAction: 'start'|'shutdown'|'stop'|'reset' = 'start';
  // Selection list
  assets: AssetListItemDTO[] = [];
  loading = false;
  filter = '';
  selection = new Set<string>();
  searchTimer: any;
  // Live list for VM dropdown
  liveList: LiveStatus[] = [];
  liveTimer: any;
  selectedVmHost: string = '';

  constructor(public api: ApiService, private toasts: ToastService) { this.loadAssets(); this.loadLive(); this.liveTimer = setInterval(()=> this.loadLive(), 30000); }

  private request(obs: any, label: string){
    this.out.set('Loading...');
    obs.subscribe({
      next: (r:any) => this.out.set(`${label}: ` + JSON.stringify(r, null, 2)),
      error: (e:any) => this.out.set('Erreur: ' + (e.message || e))
    });
  }

  private saveNode(){ if (this.node && this.node.trim()) localStorage.setItem('ops.defaultNode', this.node.trim()); }
  start(){ this.saveNode(); this.request(this.api.adminStart(this.node, +this.vmid), 'start'); }
  shutdown(){ this.saveNode(); this.request(this.api.adminShutdown(this.node, +this.vmid), 'shutdown'); }
  stop(){ this.saveNode(); this.request(this.api.adminStop(this.node, +this.vmid), 'stop'); }
  reset(){ this.saveNode(); this.request(this.api.adminReset(this.node, +this.vmid), 'reset'); }

  exec(){
    this.saveNode();
    const cmd = [this.prog, ...this.args.split(' ').filter(Boolean)];
    const t = parseInt(String(this.timeout||'10'), 10);
    this.request(this.api.adminExec(this.node, +this.vmid, cmd, this.stdin, t), 'exec');
  }

  bulk(){
    const hostnames = this.bulkHosts.split(/\r?\n/).map(s=>s.trim()).filter(Boolean);
    const tag = this.bulkTag.trim() || undefined;
    this.out.set('Loading...');
    this.api.adminBulk(this.bulkAction, { hostnames: hostnames.length? hostnames: undefined, tag }).subscribe({
      next: (r:any)=> this.out.set(JSON.stringify(r, null, 2)),
      error: (e:any)=> this.out.set('Erreur: ' + (e.message || e))
    });
  }

  // Load assets for selection
  loadAssets(){
    this.loading = true;
    this.api.listAssets(this.filter).subscribe({
      next: a => { this.assets = a; this.loading = false; },
      error: _ => { this.loading = false; }
    });
  }
  debouncedLoad(){ if (this.searchTimer) clearTimeout(this.searchTimer); this.searchTimer = setTimeout(()=> this.loadAssets(), 400); }
  filteredAssets(){ return this.assets; }
  isSelected(h: string){ return this.selection.has(h); }
  selectedCount(){ return this.selection.size; }
  toggleOne(h: string, ev: any){ if (ev?.target?.checked) this.selection.add(h); else this.selection.delete(h); }
  allSelected(){ return this.assets.length>0 && this.assets.every(a => this.selection.has(a.hostname)); }
  toggleAll(ev: any){ const checked = !!ev?.target?.checked; if (checked){ this.assets.forEach(a => this.selection.add(a.hostname)); } else { this.assets.forEach(a => this.selection.delete(a.hostname)); } }
  runBulk(){
    if (this.selection.size===0) return;
    const hostnames = Array.from(this.selection);
    this.out.set('Loading...');
    this.api.adminBulk(this.bulkAction, { hostnames }).subscribe({
      next: (res:any[]) => {
        const ok = res.filter(r => r.outcome==='OK').length;
        const err = res.length - ok;
        this.toasts.success(`${this.bulkAction} OK on ${ok} host(s)`);
        if (err>0) this.toasts.error(`${err} failed`);
        this.out.set(JSON.stringify(res, null, 2));
      },
      error: e => { this.toasts.error(e.message || 'bulk error'); this.out.set('Erreur: ' + (e.message||e)); }
    });
  }

  // Live VM dropdown helpers
  loadLive(){ this.api.liveAll().subscribe({ next: l => this.liveList = l, error: _ => {} }); }
  onSelectVm(){
    const vm = this.liveList.find(x => x.hostname === this.selectedVmHost);
    if (vm) { if (vm.node) this.node = vm.node; if (vm.vmid) this.vmid = vm.vmid; }
  }
}
