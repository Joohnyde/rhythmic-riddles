import { Routes } from '@angular/router';
import { GAME_CONNECTION_SURFACE_ROUTE_DATA } from '../../core/realtime/game-connection-route.data';

export const GAME_TV_ROUTES: Routes = [
  {
    path: 'winner',
    data: { [GAME_CONNECTION_SURFACE_ROUTE_DATA]: 'tv' },
    loadComponent: () => import('./winner/tv-winner.page').then((m) => m.TvWinnerPage),
  },
  {
    path: 'songs',
    data: { [GAME_CONNECTION_SURFACE_ROUTE_DATA]: 'tv' },
    loadComponent: () => import('./song-round/tv-song-round.page').then((m) => m.TvSongRoundPage),
  },
  {
    path: 'albums',
    data: { [GAME_CONNECTION_SURFACE_ROUTE_DATA]: 'tv' },
    loadComponent: () =>
      import('./album-selection/tv-album-selection.page').then((m) => m.TvAlbumSelectionPage),
  },
  {
    path: 'lobby',
    data: { [GAME_CONNECTION_SURFACE_ROUTE_DATA]: 'tv' },
    loadComponent: () => import('./lobby/tv-lobby.page').then((m) => m.TvLobbyPage),
  },
  { path: '**', loadComponent: () => import('./home/tv-home.page').then((m) => m.TvHomePage) },
];
