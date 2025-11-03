import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../services/api.service';

@Component({
  selector: 'app-admin-discovery',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="card">
      <h3>Proxmox Discovery</h3>
      <div class="actions" style="margin-bottom:8px">
        <button class="btn" (click)="preview()">Preview</button>
        <button class="btn primary" [disabled]="!proposals().length" (click)="apply()">Apply ({{proposals().length}})</button>
        <button class="btn danger" [disabled]="!missing().length" (click)="clearMissing()">Clear mappings for missing ({{missing().length}})</button>
        <button class="btn danger" [disabled]="!missing().length" (click)="archiveMissing()">Archive missing (hide from list)</button>
      </div>
      <div *ngIf="error()" class="muted">Erreur: {{error()}}</div>
      <div class="row">
        <div class="col">
          <h4>Proposals</h4>
          <table class="assets-table" *ngIf="proposals().length">
            <thead><tr><th>Hostname</th><th>Node</th><th>VMID</th><th>Reason</th></tr></thead>
            <tbody>
              <tr *ngFor="let p of proposals()">
                <td>{{p.hostname}}</td><td>{{p.node}}</td><td>{{p.vmid}}</td><td>{{p.reason}}</td>
              </tr>
            </tbody>
          </table>
          <div *ngIf="!proposals().length" class="muted">No proposals</div>
        </div>
        <div class="col">
          <h4>Unknown VMs</h4>
          <table class="assets-table" *ngIf="unknown().length">
            <thead><tr><th>Node</th><th>VMID</th><th>Name</th></tr></thead>
            <tbody>
              <tr *ngFor="let u of unknown()">
                <td>{{u.node}}</td><td>{{u.vmid}}</td><td>{{u.name}}</td>
              </tr>
            </tbody>
          </table>
          <div *ngIf="!unknown().length" class="muted">None</div>
        </div>
        <div class="col">
          <h4>Missing in Proxmox</h4>
          <table class="assets-table" *ngIf="missing().length">
            <thead><tr><th>Hostname</th><th>Node</th><th>VMID</th></tr></thead>
            <tbody>
              <tr *ngFor="let m of missing()">
                <td>{{m.hostname}}</td><td>{{m.node}}</td><td>{{m.vmid}}</td>
              </tr>
            </tbody>
          </table>
          <div *ngIf="!missing().length" class="muted">None</div>
        </div>
      </div>
    </div>
  `
})
export class AdminDiscoveryComponent {
  proposals = signal<any[]>([]);
  unknown = signal<any[]>([]);
  missing = signal<any[]>([]);
  error = signal<string|null>(null);
  constructor(private api: ApiService){}
  preview(){
    this.api.discoveryPreview().subscribe({
      next: r => { this.proposals.set(r.toUpdate||[]); this.unknown.set(r.unknownVms||[]); this.missing.set(r.missingAssets||[]); this.error.set(null); },
      error: e => this.error.set(e.message || 'error')
    });
  }
  apply(){
    this.api.discoveryApply(this.proposals()).subscribe({
      next: _ => this.preview(),
      error: e => this.error.set(e.message || 'error')
    });
  }
  clearMissing(){
    const names = this.missing().map(m => m.hostname).filter(Boolean);
    if (!names.length) return;
    this.api.discoveryClearMissing(names).subscribe({ next: _ => this.preview(), error: e => this.error.set(e.message || 'error') });
  }
  archiveMissing(){
    const names = this.missing().map(m => m.hostname).filter(Boolean);
    if (!names.length) return;
    this.api.discoveryArchiveMissing(names).subscribe({ next: _ => this.preview(), error: e => this.error.set(e.message || 'error') });
  }
}
