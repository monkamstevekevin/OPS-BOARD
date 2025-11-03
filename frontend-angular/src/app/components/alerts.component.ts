/**
 * AlertsComponent — display and manage alerts.
 * - Shows editable thresholds (CPU/RAM/Disk) and persists via admin API.
 * - Lists alerts with filters (host, ack) and allows acknowledgement.
 */
import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../services/api.service';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-alerts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="card"><h3>Alerts</h3>
      <div *ngIf="error()" class="muted">Erreur: {{error()}}</div>
      <div class="toolbar" style="margin-bottom:8px">
        <div class="col">
          <label>CPU high %</label>
          <input type="number" [(ngModel)]="cpuTh" (change)="saveCfg()" />
        </div>
        <div class="col">
          <label>RAM high %</label>
          <input type="number" [(ngModel)]="ramTh" (change)="saveCfg()" />
        </div>
        <div class="col">
          <label>Disk high %</label>
          <input type="number" [(ngModel)]="dskTh" (change)="saveCfg()" />
        </div>
      </div>
      <div class="toolbar" style="margin-bottom:8px">
        <div class="col">
          <label>Filter host</label>
          <div class="input-group">
            <span class="icon">🔎</span>
            <input [(ngModel)]="filterHost" placeholder="hostname..." (input)="refreshListDebounced()" />
          </div>
        </div>
        <div class="col">
          <label>Status</label>
          <select [(ngModel)]="filterAck" (change)="load()">
            <option value="ALL">All</option>
            <option value="UNACK">Unacknowledged</option>
            <option value="ACK">Acknowledged</option>
          </select>
        </div>
      </div>
      <table class="assets-table" *ngIf="alerts().length">
        <thead>
          <tr><th>Time</th><th>Host</th><th>Type</th><th>Message</th><th>Ack</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let a of alerts()">
            <td>{{a.ts | date:'short'}}</td>
            <td>{{a.hostname}}</td>
            <td>{{a.type}}</td>
            <td>{{a.message}}</td>
            <td>
              <button class="btn" [disabled]="a.acknowledged" (click)="ack(a.id)">{{ a.acknowledged ? 'Acked' : 'Ack' }}</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div *ngIf="!alerts().length && !error()" class="muted">No alerts</div>
    </div>
  `
})
export class AlertsComponent {
  alerts = signal<any[]>([]);
  error = signal<string | null>(null);
  timer: any;
  cpuTh: number = 90; ramTh: number = 90; dskTh: number = 95;
  filterHost = '';
  filterAck: 'ALL'|'UNACK'|'ACK' = 'ALL';
  searchTimer: any;
  constructor(private api: ApiService){ this.load(); this.timer = setInterval(()=> this.load(), 15000); this.loadCfg(); }
  load(){
    const params: any = {};
    if (this.filterHost.trim()) params.host = this.filterHost.trim();
    if (this.filterAck === 'ACK') params.ack = true;
    if (this.filterAck === 'UNACK') params.ack = false;
    this.api.listAlerts(params).subscribe({ next: l => { this.alerts.set(l); this.error.set(null); }, error: e => this.error.set(e.message || 'error') });
  }
  ack(id: string){ this.api.ackAlert(id).subscribe({ next: _ => this.load(), error: e => this.error.set(e.message || 'error') }); }
  loadCfg(){ this.api.getAlertsConfig().subscribe({ next: c => { this.cpuTh = c.cpuHighPct; this.ramTh = c.ramHighPct; this.dskTh = c.diskHighPct; }, error: _ => {} }); }
  saveCfg(){ this.api.updateAlertsConfig({ cpuHighPct: this.cpuTh, ramHighPct: this.ramTh, diskHighPct: this.dskTh }).subscribe({ next: _ => {}, error: e => this.error.set(e.message || 'error') }); }
  ngOnDestroy(){ if (this.timer) clearInterval(this.timer); }
  refreshListDebounced(){ if (this.searchTimer) clearTimeout(this.searchTimer); this.searchTimer = setTimeout(()=> this.load(), 400); }
}
