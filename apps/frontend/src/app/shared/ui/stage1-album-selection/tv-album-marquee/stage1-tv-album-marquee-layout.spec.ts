import { describe, expect, it } from 'vitest';
import { planStage1TvAlbumMarqueeLayout } from './stage1-tv-album-marquee-layout';

describe('Stage 1 TV album marquee layout', () => {
  it('starts with a single non-looping slot when there are no albums yet', () => {
    expect(
      planStage1TvAlbumMarqueeLayout({
        albumCount: 0,
        availableWidth: 360,
        albumSize: 120,
        gap: 12,
      }),
    ).toEqual({
      visibleColumns: 2,
      layoutColumns: 1,
      layoutRows: 1,
      shouldLoop: false,
    });
  });

  it('shows about three albums when six albums fit into one visible carousel column', () => {
    expect(
      planStage1TvAlbumMarqueeLayout({
        albumCount: 6,
        availableWidth: 126,
        albumSize: 120,
        gap: 12,
      }),
    ).toEqual({
      visibleColumns: 1,
      layoutColumns: 2,
      layoutRows: 3,
      shouldLoop: true,
    });
  });

  it('keeps a six-album carousel static when two full columns are visible', () => {
    expect(
      planStage1TvAlbumMarqueeLayout({
        albumCount: 6,
        availableWidth: 252,
        albumSize: 120,
        gap: 12,
      }),
    ).toEqual({
      visibleColumns: 2,
      layoutColumns: 2,
      layoutRows: 3,
      shouldLoop: false,
    });
  });

  it('loops when the final carousel page would not fully fill the visible window', () => {
    expect(
      planStage1TvAlbumMarqueeLayout({
        albumCount: 7,
        availableWidth: 252,
        albumSize: 120,
        gap: 12,
      }),
    ).toEqual({
      visibleColumns: 2,
      layoutColumns: 3,
      layoutRows: 3,
      shouldLoop: true,
    });
  });
});
