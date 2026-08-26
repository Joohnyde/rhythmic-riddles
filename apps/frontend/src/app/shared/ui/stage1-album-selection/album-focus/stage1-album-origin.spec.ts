import { afterEach, describe, expect, it } from 'vitest';
import { captureStage1AlbumLayout } from './stage1-album-origin';

function mockRect(element: HTMLElement, left: number, top = 0, width = 80, height = 80): void {
  element.getBoundingClientRect = () =>
    ({
      x: left,
      y: top,
      left,
      top,
      width,
      height,
      right: left + width,
      bottom: top + height,
      toJSON: () => ({}),
    }) as DOMRect;
}

function createGrid(albumIds: readonly string[], columns: number): HTMLElement {
  const viewport = document.createElement('section');
  viewport.className = 'stage1-tv-album-marquee';
  mockRect(viewport, 0, 0, columns * 100, Math.ceil(albumIds.length / columns) * 100);

  albumIds.forEach((albumId, index) => {
    const card = document.createElement('article');
    card.className = 'stage1-album-card';
    card.dataset['albumId'] = albumId;
    const column = index % columns;
    const row = Math.floor(index / columns);
    mockRect(card, column * 100, row * 100);
    viewport.append(card);
  });

  document.body.append(viewport);
  return viewport;
}

describe('captureStage1AlbumLayout', () => {
  afterEach(() => {
    document.body.replaceChildren();
  });

  it('preserves a static 3x2 grid when the bottom-right album is selected', () => {
    const albumIds = ['album-a', 'album-b', 'album-c', 'album-d', 'album-e', 'album-f'];
    const viewport = createGrid(albumIds, 3);

    const layout = captureStage1AlbumLayout(viewport, 'album-f');

    expect(layout).not.toBeNull();
    expect(layout?.cards).toHaveLength(6);
    expect(layout?.cards.find((card) => card.albumId === 'album-a')).toMatchObject({
      left: 0,
      top: 0,
    });
    expect(layout?.cards.find((card) => card.albumId === 'album-f')).toMatchObject({
      left: 200,
      top: 100,
    });
  });

  it('captures only cards that are actually visible in the viewport', () => {
    const viewport = createGrid(['album-a', 'album-b', 'album-c', 'album-d'], 4);
    mockRect(viewport, 0, 0, 250, 100);

    const layout = captureStage1AlbumLayout(viewport, 'album-b');

    expect(layout?.cards.map((card) => card.albumId)).toEqual(['album-a', 'album-b', 'album-c']);
    expect(layout?.cards.map((card) => card.albumId)).not.toContain('album-d');
  });

  it('uses the most visible rendered copy of a looping carousel album', () => {
    const viewport = createGrid(['album-a', 'album-b'], 2);
    mockRect(viewport, 0, 0, 160, 100);

    const hiddenLoopGroup = document.createElement('div');
    hiddenLoopGroup.setAttribute('aria-hidden', 'true');
    const duplicate = document.createElement('article');
    duplicate.className = 'stage1-album-card';
    duplicate.dataset['albumId'] = 'album-b';
    mockRect(duplicate, 20, 0, 80, 80);
    hiddenLoopGroup.append(duplicate);
    viewport.append(hiddenLoopGroup);

    const layout = captureStage1AlbumLayout(viewport, 'album-b');

    expect(layout?.selected).toMatchObject({ left: 20, top: 0 });
    expect(layout?.cards.find((card) => card.albumId === 'album-b')).toMatchObject({
      left: 20,
      top: 0,
    });
  });

  it('keeps the selected album in the captured layout even when it is just outside the clip', () => {
    const viewport = createGrid(['album-a', 'album-b', 'album-c'], 3);
    mockRect(viewport, 0, 0, 180, 100);

    const layout = captureStage1AlbumLayout(viewport, 'album-c');

    expect(layout?.selected).toMatchObject({ left: 200, top: 0 });
    expect(layout?.cards.map((card) => card.albumId)).toContain('album-c');
  });
});
