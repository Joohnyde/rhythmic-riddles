import { Routes } from '@angular/router';
import { TVHome } from './tvhome/tvhome';
import { TVStage0 } from './tvstage0/tvstage0';
import { TVStage1 } from './tvstage1/tvstage1';
import { TVStage2 } from './tvstage2/tvstage2';
import { Tvstage3 } from './tvstage3/tvstage3';

export const tv_routes: Routes = [
    {path:'winner', component: Tvstage3},
    {path:'songs', component: TVStage2},
    {path:'albums', component: TVStage1},
    {path:'lobby', component: TVStage0},
    {path:'**', component: TVHome}
];
