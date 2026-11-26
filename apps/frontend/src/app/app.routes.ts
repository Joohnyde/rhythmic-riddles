import { Routes } from '@angular/router';
export const routes: Routes = [
  {
    path: 'startup-splash',
    loadComponent: () =>
      import('./features/startup-splash/startup-splash.page').then((m) => m.StartupSplashPage),
  },
  {
    path: 'admin',
    loadChildren: () =>
      import('./features/game-admin/game-admin.routes').then((m) => m.GAME_ADMIN_ROUTES),
  },
  {
    path: '',
    loadChildren: () => import('./features/game-tv/game-tv.routes').then((m) => m.GAME_TV_ROUTES),
  },
];
