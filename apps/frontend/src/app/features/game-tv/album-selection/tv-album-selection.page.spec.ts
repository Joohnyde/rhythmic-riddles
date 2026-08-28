import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GameSession } from '../../../core/session/game-session.service';
import { AlbumCardVm } from '../../../domain/game/models/album.model';
import { AlbumSelectionStore } from '../../../domain/game/state/album-selection.store';
import { Stage1AlbumFocusComponent } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-focus.component';
import type { AlbumFocusLayout } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-focus.types';
import { Stage1AbortError } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-focus-async';
import { Stage1TvAlbumMarqueeComponent } from '../../../shared/ui/stage1-album-selection/tv-album-marquee/stage1-tv-album-marquee.component';
import { TvAlbumSelectionPage } from './tv-album-selection.page';

interface TvAlbumSelectionVm {
  readonly albums: readonly AlbumCardVm[];
  readonly pickedByTeam: {
    readonly id: string;
    readonly name: string;
    readonly image: string;
  } | null;
  readonly selectedAlbum: {
    readonly categoryId: string;
    readonly chosenCategoryPreview: { readonly title: string; readonly image: string };
    readonly pickedByTeam: {
      readonly id: string;
      readonly name: string;
      readonly image: string;
    } | null;
    readonly started: boolean;
    readonly ordinalNumber: number;
  } | null;
  readonly loaded: boolean;
  readonly inTransit: boolean;
}

const TEAM = { id: 'team-a', name: 'Tempo', image: '/team-icons/team-a.png' };
const FOCUS_LAYOUT: AlbumFocusLayout = {
  selected: { albumId: 'album-b', left: 120, top: 80, width: 96, height: 120 },
  cards: [
    { albumId: 'album-a', left: 10, top: 80, width: 96, height: 120 },
    { albumId: 'album-b', left: 120, top: 80, width: 96, height: 120 },
    { albumId: 'album-c', left: 230, top: 80, width: 96, height: 120 },
  ],
};

function album(id: string, name = id, picked = false): AlbumCardVm {
  return {
    id,
    name,
    image: id,
    pickedByTeam: picked ? TEAM.image : null,
    ordinalNumber: picked ? 1 : null,
  };
}

function selectedVm(albums: readonly AlbumCardVm[]): TvAlbumSelectionVm {
  return {
    albums,
    pickedByTeam: TEAM,
    selectedAlbum: {
      categoryId: 'album-b',
      chosenCategoryPreview: { title: 'Bravo', image: 'album-b' },
      pickedByTeam: TEAM,
      started: false,
      ordinalNumber: 1,
    },
    loaded: true,
    inTransit: false,
  };
}

class ResizeObserverMock {
  static instances: ResizeObserverMock[] = [];
  constructor(private readonly callback: ResizeObserverCallback) {
    ResizeObserverMock.instances.push(this);
  }
  observe = vi.fn();
  disconnect = vi.fn();
  emit(width: number, height: number): void {
    this.callback(
      [{ contentRect: { width, height } as DOMRectReadOnly } as ResizeObserverEntry],
      this as unknown as ResizeObserver,
    );
  }
}

describe('TvAlbumSelectionPage Stage 1 presentation', () => {
  let fixture: ComponentFixture<TvAlbumSelectionPage>;
  let queuedFrames: Array<{ id: number; callback: FrameRequestCallback }>;
  let frameId: number;
  let originalResizeObserver: typeof ResizeObserver | undefined;
  let originalDomMatrixReadOnly: typeof DOMMatrixReadOnly | undefined;
  const messages$ = new Subject<never>();
  const session: { code: string | null; messages$: Subject<never> | null } = {
    code: 'AKKU',
    messages$,
  };
  const connect = vi.fn();
  const disconnect = vi.fn();
  const navigate = vi.fn().mockResolvedValue(true);
  const vm = signal<TvAlbumSelectionVm>({
    albums: [album('album-a', 'Alpha'), album('album-b', 'Bravo'), album('album-c', 'Charlie')],
    pickedByTeam: TEAM,
    selectedAlbum: null,
    loaded: true,
    inTransit: false,
  });

  beforeEach(async () => {
    queuedFrames = [];
    frameId = 0;
    connect.mockClear();
    disconnect.mockClear();
    navigate.mockClear();
    session.code = 'AKKU';
    session.messages$ = messages$;
    vm.set({
      albums: [album('album-a', 'Alpha'), album('album-b', 'Bravo'), album('album-c', 'Charlie')],
      pickedByTeam: TEAM,
      selectedAlbum: null,
      loaded: true,
      inTransit: false,
    });

    ResizeObserverMock.instances = [];
    originalResizeObserver = globalThis.ResizeObserver;
    originalDomMatrixReadOnly = globalThis.DOMMatrixReadOnly;
    globalThis.ResizeObserver = ResizeObserverMock as unknown as typeof ResizeObserver;
    globalThis.DOMMatrixReadOnly = class {
      readonly a = 1;
      readonly b = 0;
      readonly m41 = 0;
      readonly m42 = 0;
      constructor(_transform?: string) {}
    } as unknown as typeof DOMMatrixReadOnly;
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      const id = ++frameId;
      queuedFrames.push({ id, callback });
      return id;
    });
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation((id) => {
      queuedFrames = queuedFrames.filter((frame) => frame.id !== id);
    });
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation(() => ({
        matches: true,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    });

    await TestBed.configureTestingModule({
      imports: [TvAlbumSelectionPage],
      providers: [
        { provide: GameSession, useValue: session },
        { provide: AlbumSelectionStore, useValue: { vm, connect, disconnect } },
        { provide: Router, useValue: { navigate } },
      ],
    }).compileComponents();

    createFixture();
  });

  afterEach(() => {
    fixture?.destroy();
    if (originalResizeObserver) globalThis.ResizeObserver = originalResizeObserver;
    else delete (globalThis as Partial<typeof globalThis>).ResizeObserver;
    if (originalDomMatrixReadOnly) globalThis.DOMMatrixReadOnly = originalDomMatrixReadOnly;
    else delete (globalThis as Partial<typeof globalThis>).DOMMatrixReadOnly;
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('connects exactly once for a valid TV session and disconnects exactly once on destroy', () => {
    expect(connect).toHaveBeenCalledOnce();
    expect(connect).toHaveBeenCalledWith(messages$, 'tv');

    fixture.destroy();

    expect(disconnect).toHaveBeenCalledOnce();
  });

  it('routes away without connecting when the TV session is missing', () => {
    fixture.destroy();
    connect.mockClear();
    disconnect.mockClear();
    session.code = null;
    session.messages$ = null;

    createFixture();

    expect(connect).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['tv']);
  });

  it('shows loading for a fresh connection instead of rendering stale Stage 1 content', () => {
    fixture.destroy();
    vm.set({
      albums: [],
      pickedByTeam: null,
      selectedAlbum: null,
      loaded: false,
      inTransit: false,
    });
    createFixture();

    expect(fixture.nativeElement.querySelector('[data-testid="tv-albums-loading"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('rr-stage1-tv-album-marquee')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('[data-testid="tv-album-focus"]')).toBeFalsy();
  });

  it('passes canonical VM order to the normal marquee and renders picker plus already-picked state', async () => {
    vm.set({
      albums: [
        album('album-a', 'Alpha'),
        album('album-b', 'Bravo', true),
        album('album-c', 'Charlie'),
      ],
      pickedByTeam: TEAM,
      selectedAlbum: null,
      loaded: true,
      inTransit: false,
    });
    fixture.detectChanges();
    await flushAllFrames(4);
    fixture.detectChanges();
    const firstGroup = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.stage1-tv-album-group',
    );
    const cards = Array.from(firstGroup?.querySelectorAll<HTMLElement>('[data-album-id]') ?? []);
    const ids = cards.map((card) => card.dataset['albumId']);
    const pickedCard = cards.find((card) => card.dataset['albumId'] === 'album-b');

    expect(ids).toEqual(['album-a', 'album-b', 'album-c']);
    expect(pickedCard?.classList.contains('stage1-album-card--disabled')).toBe(true);
    expect(pickedCard?.querySelector('.stage1-album-team-icon')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Tempo');
    expect(fixture.componentInstance.showNormalAlbums()).toBe(true);
  });

  it('drives a selected VM change through measuring, child focus readiness, and settled state without forcing phase signals', async () => {
    const marquee = findMarquee();
    vi.spyOn(marquee, 'prepareFocusLayout').mockResolvedValue(FOCUS_LAYOUT);
    vm.set(
      selectedVm([
        album('album-a', 'Alpha'),
        album('album-b', 'Bravo', true),
        album('album-c', 'Charlie'),
      ]),
    );
    fixture.detectChanges();

    expect(pagePhase()).toBe('measuring');
    expect(fixture.componentInstance.showNormalAlbums()).toBe(true);

    await driveUntil(() => pagePhase() === 'animating');

    expect(marquee.prepareFocusLayout).toHaveBeenCalledWith('album-b', expect.any(AbortSignal));
    expect(pagePhase()).toBe('animating');
    expect(fixture.componentInstance.showFocusScene()).toBe(true);
    const focus = findFocus();
    expect(focus.selectedId()).toBe('album-b');
    const selectedCard = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '[data-testid="tv-album-focus-card-album-b"]',
    );
    expect(selectedCard?.querySelector<HTMLImageElement>('.stage1-album-team-icon')?.src).toContain(
      'team-a.png',
    );

    focus.ready.emit();
    fixture.detectChanges();
    expect(fixture.componentInstance.focusSceneReady()).toBe(true);
    expect(fixture.componentInstance.showNormalAlbums()).toBe(false);

    focus.animationSettled.emit();
    fixture.detectChanges();
    expect(pagePhase()).toBe('settled');
  });

  it('replays a recovered selected VM that already exists when the TV page is created', async () => {
    fixture.destroy();
    vm.set(
      selectedVm([
        album('album-a', 'Alpha'),
        album('album-b', 'Bravo', true),
        album('album-c', 'Charlie'),
      ]),
    );
    createFixture();

    const marquee = findMarquee();
    vi.spyOn(marquee, 'prepareFocusLayout').mockResolvedValue(FOCUS_LAYOUT);
    expect(pagePhase()).toBe('measuring');

    await driveUntil(() => pagePhase() === 'animating');

    expect(marquee.prepareFocusLayout).toHaveBeenCalledWith('album-b', expect.any(AbortSignal));
    const focus = findFocus();
    expect(focus.selectedId()).toBe('album-b');
    focus.ready.emit();
    fixture.detectChanges();
    focus.animationSettled.emit();
    fixture.detectChanges();

    expect(pagePhase()).toBe('settled');
  });

  it('falls back to the normal marquee when the current child focus scene reports failure', async () => {
    const marquee = findMarquee();
    vi.spyOn(marquee, 'prepareFocusLayout').mockResolvedValue(FOCUS_LAYOUT);
    vm.set(
      selectedVm([
        album('album-a', 'Alpha'),
        album('album-b', 'Bravo', true),
        album('album-c', 'Charlie'),
      ]),
    );
    fixture.detectChanges();
    await driveUntil(() => pagePhase() === 'animating');

    expect(pagePhase()).toBe('animating');
    // Reproduce the real failure window: marquee focus preparation has already moved/paused the
    // track, but the focus child fails before emitting `ready`, so the same marquee stays mounted.
    marquee.positioningForFocus.set(true);
    marquee.focusOffset.set(84);
    findFocus().failed.emit();
    fixture.detectChanges();

    expect(pagePhase()).toBe('idle');
    expect(marquee.positioningForFocus()).toBe(false);
    expect(marquee.focusOffset()).toBe(0);
    expect(fixture.componentInstance.showNormalAlbums()).toBe(true);
    expect(fixture.componentInstance.showFocusScene()).toBe(false);
  });

  it('aborts pending focus preparation and resets presentation state on page teardown', async () => {
    const marquee = findMarquee();
    let signal: AbortSignal | undefined;
    vi.spyOn(marquee, 'prepareFocusLayout').mockImplementation((_albumId, requestSignal) => {
      signal = requestSignal;
      return new Promise((_resolve, reject) => {
        requestSignal?.addEventListener('abort', () => reject(new Stage1AbortError()), {
          once: true,
        });
      });
    });

    vm.set(
      selectedVm([
        album('album-a', 'Alpha'),
        album('album-b', 'Bravo', true),
        album('album-c', 'Charlie'),
      ]),
    );
    fixture.detectChanges();
    await driveUntil(() => Boolean(signal));
    expect(signal).toBeTruthy();

    fixture.destroy();

    expect(signal?.aborted).toBe(true);
    expect(fixture.componentInstance.focus.phase()).toBe('idle');
    expect(fixture.componentInstance.focus.requestedAlbumId).toBeNull();
  });

  it('supersedes focus preparation when the selected album changes before the first request completes', async () => {
    const marquee = findMarquee();
    let firstSignal: AbortSignal | undefined;
    let resolveFirst: ((layout: AlbumFocusLayout | null) => void) | undefined;
    vi.spyOn(marquee, 'prepareFocusLayout').mockImplementation((albumId, signal) => {
      if (albumId === 'album-b') {
        firstSignal = signal;
        return new Promise((resolve) => {
          resolveFirst = resolve;
        });
      }
      return Promise.resolve({
        ...FOCUS_LAYOUT,
        selected: { albumId: 'album-c', left: 230, top: 80, width: 96, height: 120 },
      });
    });

    vm.set(
      selectedVm([
        album('album-a', 'Alpha'),
        album('album-b', 'Bravo', true),
        album('album-c', 'Charlie'),
      ]),
    );
    fixture.detectChanges();
    await driveUntil(() => Boolean(firstSignal));
    expect(firstSignal).toBeTruthy();

    vm.set({
      ...selectedVm([
        album('album-a', 'Alpha'),
        album('album-b', 'Bravo'),
        album('album-c', 'Charlie', true),
      ]),
      selectedAlbum: {
        categoryId: 'album-c',
        chosenCategoryPreview: { title: 'Charlie', image: 'album-c' },
        pickedByTeam: TEAM,
        started: false,
        ordinalNumber: 2,
      },
    });
    fixture.detectChanges();
    expect(firstSignal?.aborted).toBe(true);
    resolveFirst?.(FOCUS_LAYOUT);
    await driveUntil(
      () =>
        fixture.componentInstance.focus.requestedAlbumId === 'album-c' &&
        pagePhase() === 'animating',
    );

    expect(fixture.componentInstance.focus.requestedAlbumId).toBe('album-c');
    expect(pagePhase()).toBe('animating');
  });

  it('switches the page marquee between narrow looping and wide static modes without changing logical order', async () => {
    const albums = [
      album('album-a', 'Alpha'),
      album('album-b', 'Bravo'),
      album('album-c', 'Charlie'),
      album('album-d', 'Delta'),
      album('album-e', 'Echo'),
      album('album-f', 'Foxtrot'),
    ];
    vm.set({ albums, pickedByTeam: TEAM, selectedAlbum: null, loaded: true, inTransit: false });
    fixture.detectChanges();
    markImagesReady(fixture.nativeElement);

    const marquee = findMarquee();
    const viewport = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.stage1-tv-album-marquee',
    );
    if (!viewport) throw new Error('Expected TV album marquee viewport');

    Object.defineProperty(viewport, 'clientWidth', { configurable: true, value: 260 });
    ResizeObserverMock.instances[0]?.emit(260, 420);
    await flushAllFrames(4);
    fixture.detectChanges();
    const narrowOrder = Array.from(
      (fixture.nativeElement as HTMLElement)
        .querySelector('.stage1-tv-album-group')
        ?.querySelectorAll<HTMLElement>('[data-album-id]') ?? [],
    ).map((card) => card.dataset['albumId']);
    expect(marquee.shouldLoop()).toBe(true);
    expect(marquee.layoutRows()).toBe(3);

    Object.defineProperty(viewport, 'clientWidth', { configurable: true, value: 1200 });
    ResizeObserverMock.instances[0]?.emit(1200, 420);
    await flushAllFrames(4);
    fixture.detectChanges();
    const wideOrder = Array.from(
      (fixture.nativeElement as HTMLElement)
        .querySelector('.stage1-tv-album-group')
        ?.querySelectorAll<HTMLElement>('[data-album-id]') ?? [],
    ).map((card) => card.dataset['albumId']);

    expect(marquee.shouldLoop()).toBe(false);
    expect(marquee.layoutRows()).toBe(1);
    expect(narrowOrder).toEqual(albums.map((value) => value.id));
    expect(wideOrder).toEqual(narrowOrder);
    expect(vm().albums.map((value) => value.id)).toEqual(narrowOrder);
  });

  it('recomputes and commits the selected focus origin after switching between looping and static TV modes', async () => {
    const albums = [
      album('album-a', 'Alpha'),
      album('album-b', 'Bravo'),
      album('album-c', 'Charlie'),
      album('album-d', 'Delta'),
      album('album-e', 'Echo'),
      album('album-f', 'Foxtrot'),
    ];
    vm.set({ albums, pickedByTeam: TEAM, selectedAlbum: null, loaded: true, inTransit: false });
    fixture.detectChanges();
    markImagesReady(fixture.nativeElement);

    const marquee = findMarquee();
    const viewport = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.stage1-tv-album-marquee',
    );
    if (!viewport) throw new Error('Expected TV album marquee viewport');

    Object.defineProperty(viewport, 'clientWidth', { configurable: true, value: 260 });
    ResizeObserverMock.instances[0]?.emit(260, 420);
    await flushAllFrames(8);
    fixture.detectChanges();
    expect(marquee.shouldLoop()).toBe(true);
    markImagesReady(fixture.nativeElement);
    applyResponsiveMarqueeFocusGeometry(fixture.nativeElement, 260, 'album-b', 90);

    // Use the real marquee preparation path. The visible looping copy of album-b is at x=90; the
    // duplicate copy is deliberately off-screen so the captured origin proves candidate selection,
    // clipping, and page-level commit all use current rendered geometry.
    vm.set(selectedVm(albums));
    fixture.detectChanges();
    await driveUntil(() => pagePhase() === 'animating');
    const loopingLayout = fixture.componentInstance.focusLayout();
    expect(loopingLayout?.selected.albumId).toBe('album-b');
    expect(loopingLayout?.selected.left).toBe(90);

    findFocus().failed.emit();
    vm.set({ albums, pickedByTeam: TEAM, selectedAlbum: null, loaded: true, inTransit: false });
    fixture.detectChanges();

    Object.defineProperty(viewport, 'clientWidth', { configurable: true, value: 1200 });
    ResizeObserverMock.instances[0]?.emit(1200, 420);
    await flushAllFrames(8);
    fixture.detectChanges();
    expect(marquee.shouldLoop()).toBe(false);
    markImagesReady(fixture.nativeElement);
    applyResponsiveMarqueeFocusGeometry(fixture.nativeElement, 1200, 'album-b', 420);

    // After responsive relayout there is only one static copy. A second real preparation must
    // capture x=420 for the same logical album; retaining x=90 would expose stale looping origin.
    vm.set(selectedVm(albums));
    fixture.detectChanges();
    await driveUntil(() => pagePhase() === 'animating');
    const staticLayout = fixture.componentInstance.focusLayout();
    expect(staticLayout?.selected.albumId).toBe('album-b');
    expect(staticLayout?.selected.left).toBe(420);
    expect(staticLayout?.selected.left).not.toBe(loopingLayout?.selected.left);
  });

  function createFixture(): void {
    queuedFrames = [];
    fixture = TestBed.createComponent(TvAlbumSelectionPage);
    fixture.detectChanges();
    markImagesReady(fixture.nativeElement);
  }

  function findMarquee(): Stage1TvAlbumMarqueeComponent {
    return fixture.debugElement.query(
      (debug) => debug.componentInstance instanceof Stage1TvAlbumMarqueeComponent,
    ).componentInstance as Stage1TvAlbumMarqueeComponent;
  }

  function findFocus(): Stage1AlbumFocusComponent {
    return fixture.debugElement.query(
      (debug) => debug.componentInstance instanceof Stage1AlbumFocusComponent,
    ).componentInstance as Stage1AlbumFocusComponent;
  }

  function pagePhase(): string | null {
    return (
      fixture.nativeElement
        .querySelector('[data-testid="tv-albums-page"]')
        ?.getAttribute('data-focus-phase') ?? null
    );
  }

  async function flushFrame(): Promise<void> {
    const frame = queuedFrames.shift();
    if (!frame) {
      await settleMicrotasks();
      return;
    }
    frame.callback(performance.now());
    await settleMicrotasks();
  }

  async function flushAllFrames(limit: number): Promise<void> {
    for (let index = 0; index < limit && queuedFrames.length > 0; index += 1) {
      await flushFrame();
    }
  }

  async function driveUntil(predicate: () => boolean, limit = 30): Promise<void> {
    for (let index = 0; index < limit && !predicate(); index += 1) {
      fixture.detectChanges();
      await settleMicrotasks();
      if (queuedFrames.length > 0) {
        await flushFrame();
      }
    }
    fixture.detectChanges();
    expect(predicate()).toBe(true);
  }
});

async function settleMicrotasks(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}

function applyResponsiveMarqueeFocusGeometry(
  root: HTMLElement,
  viewportWidth: number,
  selectedId: string,
  selectedLeft: number,
): void {
  const viewport = root.querySelector<HTMLElement>('.stage1-tv-album-marquee');
  const firstGroup = root.querySelector<HTMLElement>('.stage1-tv-album-group');
  const track = root.querySelector<HTMLElement>('.stage1-tv-album-track');
  if (!viewport) throw new Error('Expected TV album marquee viewport');

  Object.defineProperty(viewport, 'clientWidth', { configurable: true, value: viewportWidth });
  mockRect(viewport, 0, 0, viewportWidth, 420);
  if (firstGroup) mockRect(firstGroup, 0, 0, Math.max(900, viewportWidth), 360);
  if (track) mockRect(track, 0, 0, Math.max(1800, viewportWidth), 360);

  const cards = Array.from(root.querySelectorAll<HTMLElement>('.stage1-album-card[data-album-id]'));
  const seen = new Map<string, number>();
  for (const [index, card] of cards.entries()) {
    const albumId = card.dataset['albumId'] ?? '';
    const copyIndex = seen.get(albumId) ?? 0;
    seen.set(albumId, copyIndex + 1);

    if (albumId === selectedId) {
      mockRect(card, copyIndex === 0 ? selectedLeft : viewportWidth + 700, 80, 80, 110);
      continue;
    }

    if (copyIndex > 0) {
      mockRect(card, viewportWidth + 700 + index * 90, 80, 80, 110);
      continue;
    }

    if (viewportWidth <= 300) {
      const narrowLeftByAlbum: Record<string, number> = {
        'album-a': 5,
        'album-c': 175,
        'album-d': 5,
        'album-e': selectedLeft,
        'album-f': 175,
      };
      mockRect(card, narrowLeftByAlbum[albumId] ?? 500 + index * 90, 80, 80, 110);
      continue;
    }

    const logicalIndex = Math.max(
      0,
      ['album-a', 'album-b', 'album-c', 'album-d', 'album-e', 'album-f'].indexOf(albumId),
    );
    mockRect(card, 160 + logicalIndex * 180, 80, 80, 110);
  }
}

function mockRect(
  element: HTMLElement,
  left: number,
  top: number,
  width: number,
  height: number,
): void {
  Object.defineProperty(element, 'getBoundingClientRect', {
    configurable: true,
    value: () =>
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
      }) as DOMRect,
  });
}

function markImagesReady(root: HTMLElement): void {
  for (const image of Array.from(root.querySelectorAll<HTMLImageElement>('img.stage1-album-art'))) {
    Object.defineProperty(image, 'complete', { configurable: true, value: true });
    Object.defineProperty(image, 'naturalWidth', { configurable: true, value: 120 });
    image.decode = vi.fn().mockResolvedValue(undefined);
  }
}
