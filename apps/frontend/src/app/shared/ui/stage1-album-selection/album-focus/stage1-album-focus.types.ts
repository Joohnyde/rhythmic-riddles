export type AlbumFocusPhase = 'idle' | 'measuring' | 'animating' | 'settled';

export type Stage1NeighborDirection =
  'top-left' | 'top' | 'top-right' | 'left' | 'right' | 'bottom-left' | 'bottom' | 'bottom-right';

export interface AlbumFocusOrigin {
  readonly left: number;
  readonly top: number;
  readonly width: number;
  readonly height: number;
}

export interface AlbumFocusCardOrigin extends AlbumFocusOrigin {
  readonly albumId: string;
}

export interface AlbumFocusLayout {
  readonly selected: AlbumFocusCardOrigin;
  readonly cards: readonly AlbumFocusCardOrigin[];
}
