import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AlbumCardVm } from '../../../../domain/game/models/album.model';
import { Stage1TvAlbumMarqueeComponent } from './stage1-tv-album-marquee.component';

function album(id: string, name = id): AlbumCardVm {
  return { id, name, image: `${id}.png`, pickedByTeam: null, ordinalNumber: null };
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

@Component({
  standalone: true,
  imports: [Stage1TvAlbumMarqueeComponent],
  template: `<rr-stage1-tv-album-marquee [albums]="albums()" [imageUrl]="imageUrl" />`,
})
class HostComponent {
  readonly albums = signal([
    album('album-f', 'Foxtrot'),
    album('album-a', 'Alpha'),
    album('album-c', 'Charlie'),
    album('album-b', 'Bravo'),
    album('album-e', 'Echo'),
    album('album-d', 'Delta'),
  ]);
  readonly imageUrl = (image: string): string => `/assets/${image}`;
}

interface QueuedFrame {
  readonly id: number;
  readonly callback: FrameRequestCallback;
}

describe('Stage1TvAlbumMarqueeComponent integration', () => {
  let fixture: ComponentFixture<HostComponent>;
  let component: Stage1TvAlbumMarqueeComponent;
  let queuedFrames: QueuedFrame[];
  let cancelledFrames: QueuedFrame[];
  let frameId: number;
  let now: number;
  let originalResizeObserver: typeof ResizeObserver | undefined;
  let originalDomMatrixReadOnly: typeof DOMMatrixReadOnly | undefined;

  beforeEach(async () => {
    queuedFrames = [];
    cancelledFrames = [];
    frameId = 0;
    now = 0;
    ResizeObserverMock.instances = [];
    originalResizeObserver = globalThis.ResizeObserver;
    originalDomMatrixReadOnly = globalThis.DOMMatrixReadOnly;
    globalThis.ResizeObserver = ResizeObserverMock as unknown as typeof ResizeObserver;
    globalThis.DOMMatrixReadOnly = class {
      readonly m41 = 0;
      constructor(_transform?: string) {}
    } as unknown as typeof DOMMatrixReadOnly;

    vi.spyOn(performance, 'now').mockImplementation(() => now);
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      const id = ++frameId;
      queuedFrames.push({ id, callback });
      return id;
    });
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation((id) => {
      const index = queuedFrames.findIndex((frame) => frame.id === id);
      if (index >= 0) {
        cancelledFrames.push(queuedFrames[index]);
        queuedFrames.splice(index, 1);
      }
    });
    setReducedMotion(false);

    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    component = fixture.debugElement.children[0].componentInstance as Stage1TvAlbumMarqueeComponent;
    applyViewportGeometry(fixture.nativeElement, 260);
    markImagesReady(fixture.nativeElement);
    ResizeObserverMock.instances[0]?.emit(260, 420);
    await flushAllFrames(4);
    fixture.detectChanges();
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

  it('renders the provided deterministic order and duplicates the same logical identities only in looping mode', () => {
    const groups = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(
        '.stage1-tv-album-group',
      ),
    );
    expect(component.shouldLoop()).toBe(true);
    expect(component.layoutRows()).toBe(3);
    expect(groups).toHaveLength(2);

    const firstIds = cardIds(groups[0]);
    const duplicateIds = cardIds(groups[1]);
    expect(firstIds).toEqual(['album-f', 'album-a', 'album-c', 'album-b', 'album-e', 'album-d']);
    expect(duplicateIds).toEqual(firstIds);
  });

  it('handles zero and one album without inventing a looping duplicate group', async () => {
    fixture.componentInstance.albums.set([]);
    fixture.detectChanges();
    await settleMicrotasks();
    ResizeObserverMock.instances[0]?.emit(260, 420);
    await flushAllFrames(4);
    fixture.detectChanges();
    expect(component.shouldLoop()).toBe(false);
    expect(cardIds(fixture.nativeElement)).toEqual([]);

    fixture.componentInstance.albums.set([album('album-one', 'Only')]);
    fixture.detectChanges();
    await settleMicrotasks();
    markImagesReady(fixture.nativeElement);
    ResizeObserverMock.instances[0]?.emit(260, 420);
    await flushAllFrames(4);
    fixture.detectChanges();

    expect(component.shouldLoop()).toBe(false);
    expect(cardIds(fixture.nativeElement)).toEqual(['album-one']);
    expect(fixture.nativeElement.querySelectorAll('.stage1-tv-album-group')).toHaveLength(1);
  });

  it('switches between looping and static responsive layouts without reordering albums', async () => {
    const expected = cardIds(fixture.nativeElement.querySelector('.stage1-tv-album-group'));
    expect(component.shouldLoop()).toBe(true);

    applyViewportGeometry(fixture.nativeElement, 1200);
    ResizeObserverMock.instances[0]?.emit(1200, 420);
    await flushAllFrames(4);
    fixture.detectChanges();

    expect(component.shouldLoop()).toBe(false);
    expect(fixture.nativeElement.querySelectorAll('.stage1-tv-album-group')).toHaveLength(1);
    expect(cardIds(fixture.nativeElement.querySelector('.stage1-tv-album-group'))).toEqual(
      expected,
    );
    expect(component.layoutRows()).toBe(1);
  });

  it('captures the selected origin and visible layout through the public focus preparation flow', async () => {
    setReducedMotion(true);
    applyViewportGeometry(fixture.nativeElement, 1200);
    ResizeObserverMock.instances[0]?.emit(1200, 420);
    await flushAllFrames(4);
    fixture.detectChanges();
    applyCardGeometry(fixture.nativeElement, 1200);

    let resolved:
      Awaited<ReturnType<Stage1TvAlbumMarqueeComponent['prepareFocusLayout']>> | undefined;
    const promise = component.prepareFocusLayout('album-b').then((layout) => {
      resolved = layout;
      return layout;
    });
    await driveAsyncFrames();
    await promise;

    expect(resolved).not.toBeNull();
    expect(resolved?.selected.albumId).toBe('album-b');
    expect(resolved?.selected.left).toBe(300);
    expect(resolved?.cards.map((card) => card.albumId)).toEqual([
      'album-f',
      'album-a',
      'album-c',
      'album-b',
      'album-e',
      'album-d',
    ]);
  });

  it('chooses the most visible duplicated selected copy when the looping track contains two candidates', async () => {
    setReducedMotion(true);
    applyViewportGeometry(fixture.nativeElement, 260);
    ResizeObserverMock.instances[0]?.emit(260, 420);
    await flushAllFrames(4);
    fixture.detectChanges();
    applyLoopCandidateGeometry(fixture.nativeElement, 'album-b');

    const promise = component.prepareFocusLayout('album-b');
    await driveAsyncFrames();
    const layout = await promise;

    expect(layout?.selected.albumId).toBe('album-b');
    expect(layout?.selected.left).toBe(90);
  });

  it('positions an off-screen selected album before returning the focus snapshot', async () => {
    setReducedMotion(true);
    applyViewportGeometry(fixture.nativeElement, 260);
    applyOffscreenSelectedGeometry(fixture.nativeElement, 'album-b');

    const pending = component.prepareFocusLayout('album-b');
    await driveAsyncFrames();
    const layout = await pending;

    expect(layout?.selected.albumId).toBe('album-b');
    expect(component.positioningForFocus()).toBe(true);
    expect(Math.abs(component.focusOffset())).toBeGreaterThan(0);
  });

  it('reveals real wrap-adjacent columns only in looping mode and never synthesizes a static wrap neighbor', async () => {
    setReducedMotion(true);
    applyViewportGeometry(fixture.nativeElement, 260);
    ResizeObserverMock.instances[0]?.emit(260, 420);
    await flushAllFrames(4);
    fixture.detectChanges();
    expect(component.shouldLoop()).toBe(true);

    applyBoundaryNeighborGeometry(fixture.nativeElement, component);
    const loopingPending = component.prepareFocusLayout('album-b');
    await driveAsyncFrames();
    const loopingLayout = await loopingPending;

    expect(component.focusOffset()).not.toBe(0);
    expect(loopingLayout?.cards.map((card) => card.albumId)).toContain('album-c');

    component.resetFocusPositioning();
    fixture.componentInstance.albums.set([
      album('album-a', 'Alpha'),
      album('album-b', 'Bravo'),
      album('album-c', 'Charlie'),
    ]);
    fixture.detectChanges();
    await settleMicrotasks();
    markImagesReady(fixture.nativeElement);
    ResizeObserverMock.instances[0]?.emit(260, 420);
    await flushAllFrames(4);
    fixture.detectChanges();
    expect(component.shouldLoop()).toBe(false);

    applyBoundaryNeighborGeometry(fixture.nativeElement, component);
    const staticPending = component.prepareFocusLayout('album-b');
    await driveAsyncFrames();
    const staticLayout = await staticPending;

    expect(component.focusOffset()).toBe(0);
    expect(staticLayout?.cards.map((card) => card.albumId)).not.toContain('album-c');
  });

  it('cancels public focus preparation with an external signal and restores normal marquee positioning', async () => {
    setReducedMotion(false);
    applyViewportGeometry(fixture.nativeElement, 260);
    applyOffscreenSelectedGeometry(fixture.nativeElement, 'album-b');
    const abort = new AbortController();

    const pending = component.prepareFocusLayout('album-b', abort.signal);
    await flushFrame();
    await flushFrame();
    await flushFrame();
    await settleMicrotasks();
    expect(component.positioningForFocus()).toBe(true);

    abort.abort();
    await expect(pending).rejects.toThrow('Stage 1 focus preparation was aborted.');
    expect(component.positioningForFocus()).toBe(false);
    expect(component.focusOffset()).toBe(0);
  });

  it('supersedes an older public focus preparation so only the newest album owns marquee positioning', async () => {
    setReducedMotion(false);
    applyViewportGeometry(fixture.nativeElement, 260);
    applyOffscreenSelectedGeometry(fixture.nativeElement, 'album-b');

    const first = component.prepareFocusLayout('album-b');
    await flushFrame();
    await flushFrame();
    await flushFrame();
    await settleMicrotasks();

    applyOffscreenSelectedGeometry(fixture.nativeElement, 'album-c');
    const second = component.prepareFocusLayout('album-c');
    await expect(first).rejects.toThrow('Stage 1 focus preparation was aborted.');
    await driveAsyncFrames();
    const layout = await second;

    expect(layout?.selected.albumId).toBe('album-c');
  });

  it('rejects a public focus preparation instead of suspending forever when destroyed mid focus-offset animation', async () => {
    setReducedMotion(false);
    applyViewportGeometry(fixture.nativeElement, 260);
    applyOffscreenSelectedGeometry(fixture.nativeElement, 'album-b');

    const pending = component.prepareFocusLayout('album-b');
    const rejection = expect(pending).rejects.toThrow('Stage 1 focus preparation was aborted.');
    await driveUntil(() => component.positioningForFocus() && queuedFrames.length > 0);

    fixture.destroy();

    await rejection;
    expect(cancelledFrames.length).toBeGreaterThan(0);
    expect(ResizeObserverMock.instances[0]?.disconnect).toHaveBeenCalled();
  });

  it('does not let an already queued album-change microtask schedule a new measurement RAF after destroy', async () => {
    await flushAllFrames(10);
    expect(queuedFrames).toHaveLength(0);

    fixture.componentInstance.albums.set([album('album-x', 'Xray'), album('album-y', 'Yankee')]);
    fixture.detectChanges(); // effect queues recalculate microtask, but not its measurement RAF yet
    fixture.destroy();
    await settleMicrotasks();

    // Destroy invalidates the queued generation before the microtask runs, so no component-owned
    // measurement RAF can survive or appear afterward.
    expect(queuedFrames).toHaveLength(0);
    expect(ResizeObserverMock.instances[0]?.disconnect).toHaveBeenCalled();
  });

  it('cancels pending measurement RAF work and disconnects the observer during destroy', () => {
    ResizeObserverMock.instances[0]?.emit(300, 420);
    expect(queuedFrames.length).toBeGreaterThan(0);

    fixture.destroy();

    expect(cancelledFrames.length).toBeGreaterThan(0);
    expect(ResizeObserverMock.instances[0]?.disconnect).toHaveBeenCalled();
  });

  async function flushFrame(advanceMs = 16): Promise<void> {
    const frame = queuedFrames.shift();
    if (!frame) {
      await settleMicrotasks();
      return;
    }
    now += advanceMs;
    frame.callback(now);
    await settleMicrotasks();
  }

  async function flushAllFrames(limit: number): Promise<void> {
    for (let index = 0; index < limit && queuedFrames.length > 0; index += 1) {
      await flushFrame(index > 2 ? 400 : 16);
    }
  }

  async function driveAsyncFrames(limit = 20): Promise<void> {
    for (let index = 0; index < limit; index += 1) {
      await settleMicrotasks();
      if (queuedFrames.length === 0) continue;
      await flushFrame(index > 4 ? 400 : 16);
    }
  }

  async function driveUntil(predicate: () => boolean, limit = 20): Promise<void> {
    for (let index = 0; index < limit && !predicate(); index += 1) {
      await settleMicrotasks();
      if (queuedFrames.length > 0) {
        await flushFrame(16);
      }
    }
    expect(predicate()).toBe(true);
  }
});

function cardIds(root: ParentNode | null): string[] {
  if (!root) return [];
  return Array.from(root.querySelectorAll<HTMLElement>('.stage1-album-card[data-album-id]')).map(
    (card) => card.dataset['albumId'] ?? '',
  );
}

function setReducedMotion(matches: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockImplementation(() => ({
      matches,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
}

async function settleMicrotasks(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}

function applyViewportGeometry(root: HTMLElement, width: number): void {
  const viewport = root.querySelector<HTMLElement>('.stage1-tv-album-marquee');
  const group = root.querySelector<HTMLElement>('.stage1-tv-album-group');
  const track = root.querySelector<HTMLElement>('.stage1-tv-album-track');
  if (!viewport) return;
  Object.defineProperty(viewport, 'clientWidth', { configurable: true, value: width });
  mockRect(viewport, 0, 0, width, 420);
  if (group) mockRect(group, 0, 0, 900, 360);
  if (track) mockRect(track, 0, 0, 1800, 360);
}

function applyCardGeometry(root: HTMLElement, viewportWidth: number): void {
  const cards = Array.from(root.querySelectorAll<HTMLElement>('.stage1-album-card[data-album-id]'));
  cards.forEach((card, index) => {
    const logicalIndex = index % 6;
    mockRect(card, logicalIndex * 100, 20, 90, 110);
  });
  const viewport = root.querySelector<HTMLElement>('.stage1-tv-album-marquee');
  if (viewport) mockRect(viewport, 0, 0, viewportWidth, 420);
}

function applyLoopCandidateGeometry(root: HTMLElement, selectedId: string): void {
  const viewport = root.querySelector<HTMLElement>('.stage1-tv-album-marquee');
  if (viewport) mockRect(viewport, 0, 0, 260, 420);
  const cards = Array.from(root.querySelectorAll<HTMLElement>('.stage1-album-card[data-album-id]'));
  let selectedCopy = 0;
  cards.forEach((card, index) => {
    if (card.dataset['albumId'] === selectedId) {
      mockRect(card, selectedCopy++ === 0 ? -200 : 90, 20, 90, 110);
    } else {
      mockRect(card, 400 + index * 100, 20, 90, 110);
    }
  });
}

function applyOffscreenSelectedGeometry(root: HTMLElement, selectedId: string): void {
  const viewport = root.querySelector<HTMLElement>('.stage1-tv-album-marquee');
  if (viewport) mockRect(viewport, 0, 0, 260, 420);
  const cards = Array.from(root.querySelectorAll<HTMLElement>('.stage1-album-card[data-album-id]'));
  cards.forEach((card, index) => {
    mockRect(card, card.dataset['albumId'] === selectedId ? 700 : 20 + index * 100, 20, 90, 110);
  });
}

function applyBoundaryNeighborGeometry(
  root: HTMLElement,
  component: Stage1TvAlbumMarqueeComponent,
): void {
  const viewport = root.querySelector<HTMLElement>('.stage1-tv-album-marquee');
  if (viewport) mockRect(viewport, 0, 0, 260, 420);

  const seen = new Map<string, number>();
  for (const [index, card] of Array.from(
    root.querySelectorAll<HTMLElement>('.stage1-album-card[data-album-id]'),
  ).entries()) {
    const id = card.dataset['albumId'] ?? '';
    const copy = seen.get(id) ?? 0;
    seen.set(id, copy + 1);
    const baseLeft =
      copy > 0
        ? 900 + index * 100
        : id === 'album-a'
          ? 20
          : id === 'album-b'
            ? 110
            : id === 'album-c'
              ? 270
              : 500 + index * 100;
    mockDynamicRect(card, baseLeft, 20, 80, 110, () => component.focusOffset());
  }
}

function mockDynamicRect(
  element: HTMLElement,
  baseLeft: number,
  top: number,
  width: number,
  height: number,
  horizontalOffset: () => number,
): void {
  Object.defineProperty(element, 'getBoundingClientRect', {
    configurable: true,
    value: () => {
      const left = baseLeft + horizontalOffset();
      return {
        x: left,
        y: top,
        left,
        top,
        width,
        height,
        right: left + width,
        bottom: top + height,
        toJSON: () => ({}),
      } as DOMRect;
    },
  });
}

function markImagesReady(root: HTMLElement): void {
  for (const image of Array.from(root.querySelectorAll<HTMLImageElement>('img.stage1-album-art'))) {
    Object.defineProperty(image, 'complete', { configurable: true, value: true });
    Object.defineProperty(image, 'naturalWidth', { configurable: true, value: 120 });
    image.decode = vi.fn().mockResolvedValue(undefined);
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
