import { Routes } from '@angular/router';
import { GAME_CONNECTION_SURFACE_ROUTE_DATA } from '../../core/realtime/game-connection-route.data';

export const GAME_ADMIN_ROUTES: Routes = [
  {
    path: 'winner',
    data: { [GAME_CONNECTION_SURFACE_ROUTE_DATA]: 'admin' },
    loadComponent: () => import('./winner/admin-winner.page').then((m) => m.AdminWinnerPage),
  },
  {
    path: 'songs',
    data: { [GAME_CONNECTION_SURFACE_ROUTE_DATA]: 'admin' },
    loadComponent: () =>
      import('./song-round/admin-song-round.page').then((m) => m.AdminSongRoundPage),
  },
  {
    path: 'albums',
    data: { [GAME_CONNECTION_SURFACE_ROUTE_DATA]: 'admin' },
    loadComponent: () =>
      import('./album-selection/admin-album-selection.page').then((m) => m.AdminAlbumSelectionPage),
  },
  {
    path: 'lobby',
    data: { [GAME_CONNECTION_SURFACE_ROUTE_DATA]: 'admin' },
    loadComponent: () => import('./lobby/admin-lobby.page').then((m) => m.AdminLobbyPage),
  },
  {
    path: '**',
    loadComponent: () => import('./home/admin-home.page').then((m) => m.AdminHomePage),
  },
];
