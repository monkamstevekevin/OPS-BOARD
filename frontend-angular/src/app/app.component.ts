import { Component, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ToastService } from './services/toast.service';
import { ApiService } from './services/api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, FormsModule, CommonModule],
  template: `
    <header class="topbar">
      <div class="brand">CAUREQ Ops Board</div>
      <nav class="nav">
        <a routerLink="/assets" routerLinkActive="active">Assets</a>
        <a routerLink="/alerts" routerLinkActive="active">Alerts</a>
        <a routerLink="/admin" routerLinkActive="active">Admin</a>
        <a routerLink="/admin/discovery" routerLinkActive="active">Discovery <span *ngIf="missingCount()>0" class="badge">{{missingCount()}}</span></a>
      </nav>
      <button class="btn" (click)="openSettings()">Settings</button>
    </header>
    <div class="container">
      <router-outlet />
    </div>

    <div class="drawer" [class.hidden]="!settingsOpen()">
      <div class="drawer-content">
        <h2>Paramètres</h2>
        <label>API Base URL
          <input type="text" [(ngModel)]="baseUrl" placeholder="http://localhost:8060" />
        </label>
        <label>X-API-KEY
          <input type="password" [(ngModel)]="apiKey" placeholder="optional" />
        </label>
        <label>X-ADMIN-API-KEY
          <input type="password" [(ngModel)]="adminKey" placeholder="optional" />
        </label>
        <div class="drawer-actions">
          <button class="btn primary" (click)="save()">Save</button>
          <button class="btn" (click)="closeSettings()">Close</button>
        </div>
      </div>
    </div>

    <div class="toasts">
      <div class="toast" *ngFor="let t of toasts()" [class.ok]="t.type==='success'" [class.err]="t.type==='error'">
        {{t.text}}
      </div>
    </div>
  `,
})
export class AppComponent {
  settingsOpen = signal(false);

  baseUrl = localStorage.getItem('ops.baseUrl') || 'http://localhost:8060';
  apiKey = localStorage.getItem('ops.apiKey') || '';
  adminKey = localStorage.getItem('ops.adminKey') || '';

  constructor(private toastsSvc: ToastService, private api: ApiService, private router: Router) { this.pollMissing(); }
  toasts = this.toastsSvc.toasts;
  missingCount = signal(0);
  private missingTimer: any;

  openSettings(){ this.settingsOpen.set(true); }
  closeSettings(){ this.settingsOpen.set(false); }
  save(){
    localStorage.setItem('ops.baseUrl', this.baseUrl.trim());
    localStorage.setItem('ops.apiKey', this.apiKey.trim());
    localStorage.setItem('ops.adminKey', this.adminKey.trim());
    this.closeSettings();
  }

  private pollMissing(){
    const run = ()=> {
      this.api.liveAll().subscribe({
        next: list => {
          const count = (list||[]).filter((x:any)=> x && x.vmState === 'missing').length;
          const prev = this.missingCount();
          this.missingCount.set(count);
          if (prev === 0 && count > 0) {
            this.toastsSvc.info(`Missing VMs detected (${count}). Open Discovery to reconcile.`);
            try { if (!this.router.url.includes('/admin/discovery')) this.router.navigate(['/admin/discovery']); } catch {}
          }
        },
        error: _ => {}
      });
    };
    run();
    this.missingTimer = setInterval(run, 30000);
  }
  ngOnDestroy(){ if (this.missingTimer) clearInterval(this.missingTimer); }
}
