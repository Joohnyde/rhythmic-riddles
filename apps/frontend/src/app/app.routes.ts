import { Routes } from '@angular/router';
import { admin_routes } from './pages/admin/admin.routes';
import { tv_routes } from './pages/tvapp/tv.routes';

export const routes: Routes = [
    {path:'admin', children: admin_routes},
    {path:'',children: tv_routes}
];
