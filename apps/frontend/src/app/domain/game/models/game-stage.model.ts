export type GameStage = 'lobby' | 'albums' | 'songs' | 'winner';

export function routeForStage(surface: 'admin' | 'tv', stage: GameStage): unknown[] {
  return surface === 'admin' ? ['admin', stage] : [stage];
}
