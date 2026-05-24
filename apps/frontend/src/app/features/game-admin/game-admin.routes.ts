import { Routes } from '@angular/router';
export const GAME_ADMIN_ROUTES: Routes = [
  {
    path: 'winner',
    loadComponent: () => import('./winner/admin-winner.page').then((m) => m.AdminWinnerPage),
  },
  {
    path: 'songs',
    loadComponent: () =>
      import('./song-round/admin-song-round.page').then((m) => m.AdminSongRoundPage),
  },
  {
    path: 'albums',
    loadComponent: () =>
      import('./album-selection/admin-album-selection.page').then((m) => m.AdminAlbumSelectionPage),
  },
  {
    path: 'lobby',
    loadComponent: () => import('./lobby/admin-lobby.page').then((m) => m.AdminLobbyPage),
  },
  {
    path: '**',
    loadComponent: () => import('./home/admin-home.page').then((m) => m.AdminHomePage),
  },
];
