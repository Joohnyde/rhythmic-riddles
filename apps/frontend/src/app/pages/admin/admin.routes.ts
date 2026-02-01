import { Routes } from '@angular/router';
import { AdminHome } from './admin-home/admin-home';
import { AdminStage0 } from './admin-stage0/admin-stage0';
import { AdminStage1 } from './admin-stage1/admin-stage1';
import { AdminStage2 } from './admin-stage2/admin-stage2';
import { AdminStage3 } from './admin-stage3/admin-stage3';

export const admin_routes: Routes = [
    {path:'winner', component: AdminStage3},
    {path:'songs', component: AdminStage2},
    {path:'albums', component: AdminStage1},
    {path:'lobby', component: AdminStage0},
    {path:'**', component: AdminHome}
];
