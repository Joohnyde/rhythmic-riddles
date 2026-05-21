import { Routes } from '@angular/router';
export const GAME_TV_ROUTES: Routes = [
  {
    path: 'winner',
    loadComponent: () => import('./winner/tv-winner.page').then((m) => m.TvWinnerPage),
  },
  {
    path: 'songs',
    loadComponent: () => import('./song-round/tv-song-round.page').then((m) => m.TvSongRoundPage),
  },
  {
    path: 'albums',
    loadComponent: () =>
      import('./album-selection/tv-album-selection.page').then((m) => m.TvAlbumSelectionPage),
  },
  {
    path: 'lobby',
    loadComponent: () => import('./lobby/tv-lobby.page').then((m) => m.TvLobbyPage),
  },
  { path: '**', loadComponent: () => import('./home/tv-home.page').then((m) => m.TvHomePage) },
];
