import { Routes } from '@angular/router';
import { AssetsListComponent } from './components/assets-list.component';
import { AssetDetailComponent } from './components/asset-detail.component';
import { AdminComponent } from './components/admin.component';
import { AlertsComponent } from './components/alerts.component';
import { AdminDiscoveryComponent } from './components/admin-discovery.component';

export const routes: Routes = [
  { path: '', redirectTo: 'assets', pathMatch: 'full' },
  { path: 'assets', component: AssetsListComponent },
  { path: 'asset/:hostname', component: AssetDetailComponent },
  { path: 'admin', component: AdminComponent },
  { path: 'admin/discovery', component: AdminDiscoveryComponent },
  { path: 'alerts', component: AlertsComponent },
  { path: '**', redirectTo: 'assets' }
];
