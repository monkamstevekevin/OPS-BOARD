import { HttpInterceptorFn } from '@angular/common/http';

export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const adminKey = localStorage.getItem('ops.adminKey') || '';
  const apiKey = localStorage.getItem('ops.apiKey') || '';
  let headers = req.headers;
  if (req.url.includes('/api/admin/')) {
    if (adminKey) headers = headers.set('X-ADMIN-API-KEY', adminKey);
  } else if (req.url.includes('/api/')) {
    if (apiKey) headers = headers.set('X-API-KEY', apiKey);
  }
  return next(req.clone({ headers }));
};

