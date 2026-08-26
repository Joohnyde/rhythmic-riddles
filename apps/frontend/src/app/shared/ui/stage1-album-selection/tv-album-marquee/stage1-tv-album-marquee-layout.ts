export interface Stage1TvAlbumMarqueeLayoutInput {
  readonly albumCount: number;
  readonly availableWidth: number;
  readonly albumSize: number;
  readonly gap: number;
}

export interface Stage1TvAlbumMarqueeLayout {
  readonly visibleColumns: number;
  readonly layoutColumns: number;
  readonly layoutRows: number;
  readonly shouldLoop: boolean;
}

const CAROUSEL_ROWS = 3;

/**
 * Computes the TV album strip without touching DOM. Keeping this pure lets unit tests cover the
 * narrow "three visible albums out of six" carousel edge case without a browser layout dependency.
 */
export function planStage1TvAlbumMarqueeLayout({
  albumCount,
  availableWidth,
  albumSize,
  gap,
}: Stage1TvAlbumMarqueeLayoutInput): Stage1TvAlbumMarqueeLayout {
  const visibleColumns = Math.max(
    1,
    Math.floor((Math.max(availableWidth, 1) + gap) / (albumSize + gap)),
  );
  const shouldLoop = albumCount > visibleColumns * CAROUSEL_ROWS;
  const layoutColumns = shouldLoop
    ? Math.max(1, Math.ceil(albumCount / CAROUSEL_ROWS))
    : Math.max(1, Math.min(visibleColumns, albumCount));
  const layoutRows = shouldLoop
    ? CAROUSEL_ROWS
    : Math.max(1, Math.ceil(albumCount / layoutColumns));

  return {
    visibleColumns,
    layoutColumns,
    layoutRows,
    shouldLoop,
  };
}
