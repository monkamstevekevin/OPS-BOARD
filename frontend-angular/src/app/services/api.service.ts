import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface AssetListItemDTO {
  hostname: string; ip: string; os: string;
  status: 'UP'|'DOWN'|'STALE'|string; lastSeen: string | null;
  cpu?: number; ram?: number; disk?: number;
}
export interface AssetDetailDTO {
  hostname: string; ip: string; os: string;
  owner: string | null; tags: string[]; lastSeen: string | null;
}
export interface MetricPointDTO { ts: string; cpu: number; ram: number; disk: number; }
export interface MetricSummaryDTO {
  from: string; to: string;
  cpuAvg?: number; cpuMax?: number;
  ramAvg?: number; ramMax?: number;
  diskAvg?: number; diskMax?: number;
  points: number;
}

export interface LiveStatus {
  hostname: string;
  node: string;
  vmid: number;
  vmState: string;
  qgaUp: boolean;
  ipv4?: string | null;
  topPid?: number | null;
  topName?: string | null;
  topCpu?: number | null;
  topMem?: number | null;
  fetchedAt: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  private base() {
    const cfg = (localStorage.getItem('ops.baseUrl') || '').trim();
    if (cfg) return cfg; // user-configured absolute base URL
    // Dev: if running via Angular dev server (port 4200), use proxy with relative /api
    if (location.port === '4200') return '';
    // Fallback default
    return 'http://localhost:8060';
  }

  listAssets(q: string = '', opts?: { limit?: number; offset?: number; includeRetired?: boolean }) {
    const qs: string[] = [];
    if (q) qs.push(`q=${encodeURIComponent(q)}`);
    if (opts?.limit) qs.push(`limit=${opts.limit}`);
    if (opts?.offset) qs.push(`offset=${opts.offset}`);
    if (typeof opts?.includeRetired === 'boolean') qs.push(`includeRetired=${opts.includeRetired}`);
    const url = `${this.base()}/api/assets${qs.length?`?${qs.join('&')}`:''}`;
    return this.http.get<AssetListItemDTO[]>(url);
  }
  getAsset(hostname: string){ return this.http.get<AssetDetailDTO>(`${this.base()}/api/assets/${encodeURIComponent(hostname)}`); }
  latestMetrics(hostname: string, limit: number){
    return this.http.get<MetricPointDTO[]>(`${this.base()}/api/assets/${encodeURIComponent(hostname)}/metrics?limit=${limit}`);
  }
  rangeMetrics(hostname: string, fromIso: string, toIso: string){
    return this.http.get<MetricPointDTO[]>(`${this.base()}/api/assets/${encodeURIComponent(hostname)}/metrics?from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`);
  }
  summary(hostname: string, fromIso: string, toIso: string){
    return this.http.get<MetricSummaryDTO>(`${this.base()}/api/assets/${encodeURIComponent(hostname)}/metrics/summary?from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`);
  }
  patchAsset(hostname: string, body: { owner?: string|null; tags?: string[]|null; }){
    return this.http.patch<void>(`${this.base()}/api/assets/${encodeURIComponent(hostname)}`, body);
  }
  fsUsage(hostname: string){ return this.http.get<{mount:string, usedPct:number|null}[]>(`${this.base()}/api/assets/${encodeURIComponent(hostname)}/metrics/fs`); }
  // Live status
  liveAll(){ return this.http.get<LiveStatus[]>(`${this.base()}/api/status/live`); }
  liveOne(hostname: string){ return this.http.get<LiveStatus>(`${this.base()}/api/status/live/${encodeURIComponent(hostname)}`); }

  // Alerts
  listAlerts(params?: { host?: string; ack?: boolean; limit?: number; offset?: number }){
    const qs: string[] = [];
    if (params) {
      if (params.host) qs.push(`host=${encodeURIComponent(params.host)}`);
      if (typeof params.ack === 'boolean') qs.push(`ack=${params.ack}`);
      if (params.limit) qs.push(`limit=${params.limit}`);
      if (params.offset) qs.push(`offset=${params.offset}`);
    }
    const q = qs.length ? `?${qs.join('&')}` : '';
    return this.http.get<any[]>(`${this.base()}/api/alerts${q}`);
  }
  ackAlert(id: string){ return this.http.post(`${this.base()}/api/alerts/${encodeURIComponent(id)}/ack`, {}); }

  // Admin discovery
  discoveryPreview(){ return this.http.get<any>(`${this.base()}/api/admin/discovery/preview`); }
  discoveryApply(proposals: any[]){ return this.http.post<any>(`${this.base()}/api/admin/discovery/apply`, { proposals }); }
  discoveryClearMissing(hostnames: string[]){ return this.http.post<any>(`${this.base()}/api/admin/discovery/clear-missing`, { hostnames }); }
  discoveryArchiveMissing(hostnames: string[]){ return this.http.post<any>(`${this.base()}/api/admin/discovery/archive-missing`, { hostnames }); }

  // Admin alerts config
  getAlertsConfig(){ return this.http.get<any>(`${this.base()}/api/admin/alerts/config`); }
  updateAlertsConfig(cfg: { cpuHighPct?: number; ramHighPct?: number; diskHighPct?: number; }){
    return this.http.put(`${this.base()}/api/admin/alerts/config`, cfg);
  }

  // Admin asset mapping
  adminUpdateMapping(hostname: string, node: string, vmid: number){
    return this.http.patch(`${this.base()}/api/admin/assets/${encodeURIComponent(hostname)}/mapping`, { node, vmid });
  }

  // Admin diagnostics
  adminDiagnose(hostname: string, top: number = 3){
    return this.http.get<any>(`${this.base()}/api/admin/diag/host/${encodeURIComponent(hostname)}?top=${top}`);
  }

  // Admin endpoints
  adminStart(node: string, vmid: number){ return this.http.post<any>(`${this.base()}/api/admin/vm/${encodeURIComponent(node)}/${vmid}/start`, {}); }
  adminShutdown(node: string, vmid: number){ return this.http.post<any>(`${this.base()}/api/admin/vm/${encodeURIComponent(node)}/${vmid}/shutdown`, {}); }
  adminStop(node: string, vmid: number){ return this.http.post<any>(`${this.base()}/api/admin/vm/${encodeURIComponent(node)}/${vmid}/stop`, {}); }
  adminReset(node: string, vmid: number){ return this.http.post<any>(`${this.base()}/api/admin/vm/${encodeURIComponent(node)}/${vmid}/reset`, {}); }
  adminExec(node: string, vmid: number, command: string[], input: string, timeoutSec: number){
    return this.http.post<any>(`${this.base()}/api/admin/exec/${encodeURIComponent(node)}/${vmid}`, { command, input, timeoutSec });
  }

  // Bulk actions
  adminBulk(action: 'start'|'shutdown'|'stop'|'reset', payload: { hostnames?: string[]; tag?: string; }){
    return this.http.post<any[]>(`${this.base()}/api/admin/vm/bulk/${action}`, payload);
  }
}
