import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error' | 'info';
export interface Toast { id: number; type: ToastType; text: string; ttl: number; }

@Injectable({ providedIn: 'root' })
export class ToastService {
  toasts = signal<Toast[]>([]);
  private nextId = 1;

  show(text: string, type: ToastType = 'info', ttl = 3500) {
    const id = this.nextId++;
    const t: Toast = { id, type, text, ttl };
    this.toasts.update(arr => [...arr, t]);
    setTimeout(() => this.dismiss(id), ttl);
  }

  success(text: string, ttl = 3000){ this.show(text, 'success', ttl); }
  error(text: string, ttl = 5000){ this.show(text, 'error', ttl); }
  info(text: string, ttl = 3500){ this.show(text, 'info', ttl); }

  dismiss(id: number){ this.toasts.update(arr => arr.filter(t => t.id !== id)); }
}

