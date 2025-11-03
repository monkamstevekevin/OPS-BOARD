import { Component, ElementRef, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, AssetDetailDTO, MetricPointDTO, LiveStatus } from '../services/api.service';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-asset-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <a routerLink="/assets">â† retour</a>
    <div class="card" *ngIf="loading()">
      <div class="skeleton" style="padding:12px">
        <div class="sk-rect" style="width:30%;margin-bottom:12px"></div>
        <div class="sk-rect" style="width:60%;margin-bottom:12px"></div>
        <div class="sk-rect" style="width:50%;margin-bottom:12px"></div>
        <div class="sk-rect" style="width:80%;margin-bottom:12px"></div>
      </div>
    </div>
    <div class="card" *ngIf="error()">Erreur: {{error()}}</div>

    <div class="card" *ngIf="asset() as a">
      <h3>{{a.hostname}}</h3>
      <div class="row">
        <div class="col"><label>IP</label><div>{{a.ip}}</div></div>
        <div class="col"><label>OS</label><div>{{a.os}}</div></div>
        <div class="col"><label>Owner</label><div>{{a.owner}}</div></div>
        <div class="col"><label>Last Seen</label><div>{{a.lastSeen ? (a.lastSeen | date:'short') : ''}}</div></div>
      </div>
      <div class="row" *ngIf="live as lv">
        <div class="col"><label>VM <span class="help" data-tip="Status = power only (running=UP, stopped=DOWN)
GA = QEMU Guest Agent reachability.">i</span></label>
          <div>
            <span class="status" [class.up]="lv?.vmState==='running'" [class.down]="lv?.vmState!=='running'">{{ lv?.vmState || '-' }}</span>
            <span class="status" [class.up]="lv?.qgaUp" [class.down]="lv && !lv?.qgaUp">GA: {{ lv?.qgaUp ? 'UP' : 'DOWN' }}</span>
          </div>
        </div>
        <div class="col"><label>IP (Live)</label><div>{{ lv?.ipv4 || '' }}</div></div>
        <div class="col"><label>Node/VMID</label><div>{{ lv?.node || '-' }} / {{ lv?.vmid || '-' }}</div></div>
        <div class="col"><label>Refresh</label><div>{{ lv?.fetchedAt | date:'mediumTime' }}</div></div>
      </div>
      <div class="row" *ngIf="live && live.topPid">
        <div class="col"><label>Top Process</label><div>PID {{live.topPid}} · {{live.topName}} · CPU {{live.topCpu | number:'1.0-0'}}% <span *ngIf="live.topMem">· MEM {{live.topMem | number:'1.0-1'}}%</span></div></div>
      </div>
      <div class="row">
        <div class="col"><label>Tags</label><div>{{(a.tags||[]).join(', ')}}</div></div>
      </div>
      <hr>
      <h3>Ã‰diter</h3>
      <div class="row">
        <div class="col"><label>Nouveau Owner</label><input [(ngModel)]="editOwner" placeholder="owner"/></div>
        <div class="col"><label>Tags (sÃ©parÃ©s par des virgules)</label><input [(ngModel)]="editTags" placeholder="tag1, tag2"/></div>
      </div>
      <div class="actions" style="margin-top:8px">
        <button class="btn" (click)="saveMeta()">Enregistrer</button>
      </div>
      <hr>
      <h3>Admin · Node/VMID</h3>
      <div class="row">
        <div class="col"><label>Node</label><input [(ngModel)]="editNode" placeholder="pve01"/></div>
        <div class="col"><label>VMID</label><input type="number" [(ngModel)]="editVmid" placeholder="123"/></div>
      </div>
      <div class="actions" style="margin-top:8px">
        <button class="btn" (click)="saveMapping()">Save Mapping</button>
      </div>
    </div>

    <div class="card" *ngIf="metrics().length">
      <h3>Metrics (latest) <span class="help" data-tip="CPU = used % of total VM capacity (all vCPUs)
RAM/Disk = used %">i</span></h3>
      <div class="row">
        <div class="col"><label>PÃ©riode</label>
          <div class="row">
            <div class="col"><input type="datetime-local" [(ngModel)]="fromLocal"></div>
            <div class="col"><input type="datetime-local" [(ngModel)]="toLocal"></div>
            <div class="col"><button class="btn" (click)="loadRange()">Charger</button></div>
          </div>
          <div class="row" *ngIf="summary">
            <div class="col"><label>CPU</label><div>{{summary?.cpuAvg | number:'1.0-0'}}% avg / {{summary?.cpuMax | number:'1.0-0'}}% max</div></div>
            <div class="col"><label>RAM</label><div>{{summary?.ramAvg | number:'1.0-0'}}% avg / {{summary?.ramMax | number:'1.0-0'}}% max</div></div>
            <div class="col"><label>Disk</label><div>{{summary?.diskAvg | number:'1.0-0'}}% avg / {{summary?.diskMax | number:'1.0-0'}}% max</div></div>
          </div>
        </div>
      </div>
      <div class="row">
        <div class="col"><label>CPU %</label><canvas class="chart" #cpuCanvas></canvas></div>
        <div class="col"><label>RAM %</label><canvas class="chart" #ramCanvas></canvas></div>
        <div class="col"><label>Disk %</label><canvas class="chart" #dskCanvas></canvas></div>
      </div>
    </div>

    <div class="card" *ngIf="fsList.length">
      <h3>Disks</h3>
      <table class="assets-table">
        <thead><tr><th>Mount</th><th>Used %</th></tr></thead>
        <tbody>
          <tr *ngFor="let f of fsList">
            <td>{{f.mount}}</td>
            <td>{{f.usedPct==null? '-' : (f.usedPct | number:'1.0-1')+'%'}}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="card">
      <h3>Diagnostics</h3>
      <div class="actions" style="margin-bottom:8px">
        <button class="btn" (click)="diagnose()">Diagnose Now</button>
      </div>
      <div *ngIf="diagError" class="muted">{{diagError}}</div>
      <div *ngIf="diag?.notVm" class="muted">Not a Proxmox VM (no node/vmid mapping)</div>
      <div class="row" *ngIf="diag">
        <div class="col">
          <label>Top CPU</label>
          <table class="assets-table" *ngIf="diag.cpuTop?.length">
            <thead><tr><th>PID</th><th>Name</th><th>CPU %</th><th>MEM % / WS</th></tr></thead>
            <tbody>
              <tr *ngFor="let p of diag.cpuTop">
                <td>{{p.pid}}</td><td>{{p.name}}</td><td>{{p.cpu | number:'1.0-1'}}</td><td>{{p._memLabel}}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="col">
          <label>Top RAM</label>
          <table class="assets-table" *ngIf="diag.memTop?.length">
            <thead><tr><th>PID</th><th>Name</th><th>CPU %</th><th>MEM % / WS</th></tr></thead>
            <tbody>
              <tr *ngFor="let p of diag.memTop">
                <td>{{p.pid}}</td><td>{{p.name}}</td><td>{{p.cpu | number:'1.0-1'}}</td><td>{{p._memLabel}}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `
})
export class AssetDetailComponent {
  hostname = '';
  asset = signal<AssetDetailDTO | null>(null);
  metrics = signal<MetricPointDTO[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  live: LiveStatus | null = null;
  liveTimer: any;
  metricsTimer: any;
  editOwner = '';
  editTags = '';
  editNode = '';
  editVmid: any = '';
  fromLocal = '';
  toLocal = '';
  summary: any = null;
  @ViewChild('cpuCanvas') cpuRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('ramCanvas') ramRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('dskCanvas') dskRef?: ElementRef<HTMLCanvasElement>;
  alertsAll: any[] = [];
  alertsTimer: any;
  diag: any = null;
  diagError: string | null = null;
  fsList: { mount: string, usedPct: number|null }[] = [];

  constructor(private route: ActivatedRoute, private api: ApiService) {
    this.hostname = this.route.snapshot.paramMap.get('hostname') || '';
    this.fetch();
    this.pullLive();
    this.liveTimer = setInterval(()=> this.pullLive(), 30000);
    this.metricsTimer = setInterval(()=> this.refreshLatestMetrics(), 30000);
    this.pullAlerts();
    this.alertsTimer = setInterval(()=> this.pullAlerts(), 15000);
  }

  fetch(){
    this.loading.set(true);
    this.error.set(null);
    this.api.getAsset(this.hostname).subscribe({
      next: a => { this.asset.set(a); this.editOwner = a.owner || ''; this.editTags = (a.tags||[]).join(', '); },
      error: e => { this.error.set(e.message || 'error'); this.loading.set(false);} 
    });
    this.api.latestMetrics(this.hostname, 50).subscribe({
      next: m => { this.metrics.set(m); this.loading.set(false); setTimeout(()=>this.drawCharts(), 0); },
      error: e => { this.error.set(e.message || 'error'); this.loading.set(false);} 
    });
  }

  drawCharts(){
    const cpu = this.cpuRef?.nativeElement; const ram = this.ramRef?.nativeElement; const dsk = this.dskRef?.nativeElement;
    const points = this.metrics();
    if (!cpu || !ram || !dsk || !points.length) return;
    const draw = (canvas: HTMLCanvasElement, series: number[], color: string)=>{
      const ctx = canvas.getContext('2d')!;
      const w = canvas.width = canvas.clientWidth * devicePixelRatio;
      const h = canvas.height = canvas.clientHeight * devicePixelRatio;
      ctx.scale(devicePixelRatio, devicePixelRatio);
      const pad = 30; const innerW = canvas.clientWidth - pad*2; const innerH = canvas.clientHeight - pad*2;
      const minY = 0, maxY = 100; const spanY = 100; const xCount = series.length;
      const xTo = (i:number)=> pad + (i/(Math.max(1,xCount-1))) * innerW;
      const yTo = (v:number)=> pad + innerH - ((v-minY)/spanY)*innerH;
      ctx.fillStyle = '#0f172a'; ctx.fillRect(0,0,canvas.clientWidth,canvas.clientHeight);
      ctx.strokeStyle = '#223152'; ctx.beginPath(); ctx.moveTo(pad,pad); ctx.lineTo(pad,pad+innerH); ctx.lineTo(pad+innerW,pad+innerH); ctx.stroke();
      ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.beginPath();
      series.forEach((v,i)=>{ const x=xTo(i), y=yTo(v); if(i===0) ctx.moveTo(x,y); else ctx.lineTo(x,y); }); ctx.stroke();
      ctx.fillStyle = '#94a3b8'; ctx.font = '12px system-ui'; ctx.fillText('0',4,pad+innerH); ctx.fillText('100',4,pad+12);
    };
    draw(cpu, points.map(p=>p.cpu), '#f59e0b');
    draw(ram, points.map(p=>p.ram), '#22c55e');
    draw(dsk, points.map(p=>p.disk), '#0ea5e9');
  }

  private refreshLatestMetrics(){
    // If user selected a custom range, do not override
    if (this.fromLocal || this.toLocal) return;
    this.api.latestMetrics(this.hostname, 50).subscribe({
      next: m => { this.metrics.set(m); setTimeout(()=>this.drawCharts(), 0); },
      error: _ => {}
    });
    this.api.fsUsage(this.hostname).subscribe({ next: l => this.fsList = l as any, error: _ => {} });
  }

  saveMeta(){
    const tags = this.editTags.split(',').map(s=>s.trim()).filter(Boolean);
    this.api.patchAsset(this.hostname, { owner: this.editOwner || null, tags }).subscribe({
      next: ()=> this.fetch(),
      error: e => this.error.set(e.message || 'error')
    });
  }

  saveMapping(){
    if (!this.editNode || !this.editVmid) return;
    this.api.adminUpdateMapping(this.hostname, this.editNode, Number(this.editVmid)).subscribe({
      next: _ => this.pullLive(),
      error: e => this.error.set(e.message || 'error')
    });
  }

  loadRange(){
    if (!this.fromLocal || !this.toLocal) return;
    const fromIso = new Date(this.fromLocal).toISOString();
    const toIso = new Date(this.toLocal).toISOString();
    this.loading.set(true);
    this.api.rangeMetrics(this.hostname, fromIso, toIso).subscribe({
      next: pts => { this.metrics.set(pts); this.loading.set(false); setTimeout(()=>this.drawCharts(), 0); },
      error: e => { this.error.set(e.message || 'error'); this.loading.set(false); }
    });
    this.api.summary(this.hostname, fromIso, toIso).subscribe({
      next: s => this.summary = s,
      error: _ => {}
    });
  }

  // Alerts for this host
  hostAlerts(){ return this.alertsAll.filter(a => a.hostname === this.hostname && !a.acknowledged); }
  pullAlerts(){ this.api.listAlerts().subscribe({ next: l => this.alertsAll = l, error: _ => {} }); }
  ackAlert(id: string){ this.api.ackAlert(id).subscribe({ next: _ => this.pullAlerts(), error: _ => {} }); }
  diagnose(){
    this.diagError = null; this.diag = null;
    this.api.adminDiagnose(this.hostname, 3).subscribe({ next: r => {
      // Normalize fields to pid/name/cpu/mem and add human-readable mem label
      const norm = (arr:any[], isWin:boolean) => (arr||[]).map((p:any)=> {
        const pid = p.pid ?? p.PID ?? p.Id ?? p.IDProcess;
        const name = (p.name ?? p.Name ?? p.ProcessName ?? '').toString();
        const cpu = Number(p.cpu ?? p.CPU ?? p.PercentProcessorTime ?? 0);
        const rawMem = (p.mem ?? p.MEM ?? p.WorkingSet);
        let mem = Number(rawMem ?? 0);
        let memLabel = '';
        if (isWin) {
          // WorkingSet bytes → MB/GB
          const bytes = Number(mem);
          if (!isNaN(bytes) && bytes > 0) {
            const mb = bytes / (1024*1024);
            memLabel = (mb >= 1024 ? (mb/1024).toFixed(1)+' GB' : mb.toFixed(0)+' MB');
          } else { memLabel = '—'; }
        } else {
          // Linux: mem is % (0–100)
          memLabel = isNaN(mem) ? '—' : (mem.toFixed(1)+'%');
        }
        return { pid, name, cpu, mem, _memLabel: memLabel };
      });
      const isWin = !!(r && (r.os||'').toString().toUpperCase().includes('WIN'));
      r.cpuTop = norm(r.cpuTop, isWin);
      r.memTop = norm(r.memTop, isWin);
      this.diag = r; this.diagError = (r && r.error) ? r.error : null;
    }, error: e => { this.diagError = e.message || 'error'; } });
  }

  pullLive(){
    this.api.liveOne(this.hostname).subscribe({
      next: s => this.live = s,
      error: _ => {}
    });
  }

  ngOnDestroy(){ if (this.liveTimer) clearInterval(this.liveTimer); if (this.metricsTimer) clearInterval(this.metricsTimer); if (this.alertsTimer) clearInterval(this.alertsTimer); }
}

