import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AlbumCardVm } from '../../../../domain/game/models/album.model';
import type { AlbumFocusLayout } from './stage1-album-focus.types';
import { Stage1AlbumFocusComponent } from './stage1-album-focus.component';

const BASE_LAYOUT: AlbumFocusLayout = {
  selected: { albumId: 'album-b', left: 140, top: 120, width: 96, height: 120 },
  cards: [
    { albumId: 'album-a', left: 32, top: 32, width: 96, height: 120 },
    { albumId: 'album-b', left: 140, top: 120, width: 96, height: 120 },
    { albumId: 'album-c', left: 248, top: 32, width: 96, height: 120 },
  ],
};

function album(id: string, name = id, overrides: Partial<AlbumCardVm> = {}): AlbumCardVm {
  return { id, name, image: `${id}.png`, pickedByTeam: null, ordinalNumber: null, ...overrides };
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

interface QueuedFrame {
  readonly id: number;
  readonly callback: FrameRequestCallback;
}

describe('Stage1AlbumFocusComponent observable lifecycle', () => {
  let fixture: ComponentFixture<Stage1AlbumFocusComponent>;
  let component: Stage1AlbumFocusComponent;
  let queuedFrames: QueuedFrame[];
  let cancelledFrames: QueuedFrame[];
  let frameId: number;
  let now: number;
  let originalResizeObserver: typeof ResizeObserver | undefined;
  let originalDomMatrixReadOnly: typeof DOMMatrixReadOnly | undefined;
  let removeResizeListener: ReturnType<typeof vi.spyOn>;

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
      readonly a = 1;
      readonly b = 0;
      readonly m41 = 0;
      readonly m42 = 0;
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
    removeResizeListener = vi.spyOn(window, 'removeEventListener');
    setReducedMotion(false);

    await TestBed.configureTestingModule({
      imports: [Stage1AlbumFocusComponent],
    }).compileComponents();
    createFixture({ animate: true });
  });

  afterEach(() => {
    vi.useRealTimers();
    fixture?.destroy();
    if (originalResizeObserver) globalThis.ResizeObserver = originalResizeObserver;
    else delete (globalThis as Partial<typeof globalThis>).ResizeObserver;
    if (originalDomMatrixReadOnly) globalThis.DOMMatrixReadOnly = originalDomMatrixReadOnly;
    else delete (globalThis as Partial<typeof globalThis>).DOMMatrixReadOnly;
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('renders the selected card, applies the measured origin layout, and completes a real RAF animation once', async () => {
    const ready = vi.fn();
    const settled = vi.fn();
    component.ready.subscribe(ready);
    component.animationSettled.subscribe(settled);

    await advanceToReady();

    const selected = selectedCard('album-b');
    expect(selected).toBeTruthy();
    expect(selected.style.left).toBe(`${BASE_LAYOUT.cards[1].left}px`);
    expect(component.absoluteLayout()).toBe(true);
    expect(component.focusReady()).toBe(true);
    expect(component.focused()).toBe(false);
    expect(ready).toHaveBeenCalledOnce();
    expect(focusState()).toBe('ready');

    await flushFrame(16); // visible start frame -> active loop
    expect(component.focused()).toBe(true);
    expect(focusState()).toBe('animating');

    const scene = fixture.nativeElement.querySelector('.stage1-focus-scene') as HTMLElement;
    const initialTransform = scene.style.transform;
    await flushFrame(900);
    expect(scene.style.transform).not.toBe(initialTransform);
    expect(component.settled()).toBe(false);

    await flushFrame(1600);
    fixture.detectChanges();
    expect(component.settled()).toBe(true);
    expect(component.glowActive()).toBe(true);
    expect(focusState()).toBe('settled');
    expect(settled).toHaveBeenCalledOnce();
    expect(queuedFrames.length).toBe(0);
  });

  it('replays the visible focus transition for a recovered selected state instead of merely showing the card', async () => {
    const settled = vi.fn();
    component.animationSettled.subscribe(settled);

    await advanceToReady();
    expect(focusState()).toBe('ready');

    await flushFrame(16);
    expect(focusState()).toBe('animating');
    expect(component.settled()).toBe(false);

    await flushFrame(2400);
    fixture.detectChanges();
    expect(focusState()).toBe('settled');
    expect(settled).toHaveBeenCalledOnce();
  });

  it('uses reduced motion to reach the same final state without leaving an animation loop pending', async () => {
    fixture.destroy();
    setReducedMotion(true);
    createFixture({ animate: true });
    const events: string[] = [];
    const ready = vi.fn(() => events.push('ready'));
    const settled = vi.fn(() => events.push('settled'));
    component.ready.subscribe(ready);
    component.animationSettled.subscribe(settled);

    await flushFrame(); // scheduleFocus -> image preparation
    await settleMicrotasks();
    await flushFrame(); // image-readiness paint frame -> settle synchronously
    await settleMicrotasks();
    fixture.detectChanges();

    expect(component.settled()).toBe(true);
    expect(component.focused()).toBe(true);
    expect(component.focusReady()).toBe(true);
    expect(focusState()).toBe('settled');
    expect(ready).toHaveBeenCalledOnce();
    expect(settled).toHaveBeenCalledOnce();
    expect(events).toEqual(['ready', 'settled']);
    expect(queuedFrames.length).toBe(0);
  });

  it('invalidates an active selection immediately when a newer selected id supersedes it', async () => {
    const ready = vi.fn();
    const settled = vi.fn();
    component.ready.subscribe(ready);
    component.animationSettled.subscribe(settled);

    await advanceToReady();
    await flushFrame(16); // start A loop
    await flushFrame(400); // A progressed and queued its next loop frame
    expect(component.settled()).toBe(false);

    fixture.componentRef.setInput('selectedId', 'album-c');
    fixture.detectChanges();
    await settleMicrotasks();

    const staleA = cancelledFrames.at(-1);
    expect(staleA).toBeTruthy();
    expect(component.focusReady()).toBe(false);
    expect(component.focused()).toBe(false);

    // Simulate a browser/test queue delivering the already-cancelled callback anyway. It must be a no-op.
    now += 3000;
    staleA?.callback(now);
    await settleMicrotasks();
    expect(settled).not.toHaveBeenCalled();
    expect(component.settled()).toBe(false);

    await advanceToReady();
    expect(selectedCard('album-c').getAttribute('data-selected')).toBe('true');
    await flushFrame(16);
    await flushFrame(2400);
    fixture.detectChanges();

    expect(settled).toHaveBeenCalledOnce();
    expect(focusState()).toBe('settled');
    expect(selectedCard('album-c').getAttribute('data-selected')).toBe('true');
    expect(ready).toHaveBeenCalledTimes(2);
  });

  it('preserves picked-team icons only for left, top, and top-left neighbor directions', () => {
    const shouldPreserve = (
      component as unknown as {
        shouldPreservePickedIcon: (direction: string | null) => boolean;
      }
    ).shouldPreservePickedIcon.bind(component);

    expect(['left', 'top', 'top-left'].filter((direction) => shouldPreserve(direction))).toEqual([
      'left',
      'top',
      'top-left',
    ]);
    expect(
      ['right', 'top-right', 'bottom', 'bottom-left', 'bottom-right'].some((direction) =>
        shouldPreserve(direction),
      ),
    ).toBe(false);
    expect(shouldPreserve(null)).toBe(false);
  });

  it('keeps picked icons fully visible only on left/top/top-left remnants and masks a picked right neighbor with the album field', async () => {
    fixture.destroy();
    const rowLayout: AlbumFocusLayout = {
      selected: { albumId: 'album-b', left: 140, top: 120, width: 96, height: 120 },
      cards: [
        { albumId: 'album-a', left: 32, top: 120, width: 96, height: 120 },
        { albumId: 'album-b', left: 140, top: 120, width: 96, height: 120 },
        { albumId: 'album-c', left: 248, top: 120, width: 96, height: 120 },
      ],
    };
    createFixture({
      animate: true,
      layout: rowLayout,
      albums: [
        album('album-a', 'Alpha', { pickedByTeam: '/team-icons/left.png', ordinalNumber: 1 }),
        album('album-b', 'Bravo'),
        album('album-c', 'Charlie', { pickedByTeam: '/team-icons/right.png', ordinalNumber: 2 }),
      ],
    });

    for (const icon of Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(
        '.stage1-album-team-icon',
      ),
    )) {
      icon.style.width = '20px';
      icon.style.height = '20px';
      icon.style.right = '-5px';
      icon.style.bottom = '-5px';
    }

    await advanceToReady();
    await flushFrame(16);
    await flushFrame(2400);
    fixture.detectChanges();

    const left = selectedCard('album-a');
    const right = selectedCard('album-c');
    expect(component.neighborDirection('album-a')).toBe('left');
    expect(component.neighborDirection('album-c')).toBe('right');
    expect(left.style.getPropertyValue('--stage1-focus-icon-mask')).toBe('none');
    expect(left.style.getPropertyValue('--stage1-focus-icon-opacity')).toBe('1');

    expect(right.style.getPropertyValue('--stage1-focus-icon-mask')).toBe('');
    expect(right.style.getPropertyValue('--stage1-focus-icon-opacity')).toBe('');
    expect(right.style.getPropertyValue('--stage1-focus-content-mask')).toContain(
      'linear-gradient',
    );
    expect(right.style.getPropertyValue('--stage1-focus-icon-mask-position')).toMatch(
      /^-\d+(?:\.\d+)?px -\d+(?:\.\d+)?px$/,
    );
  });

  it('accepts a real resize before focus preparation without restarting or losing the selection', async () => {
    const settled = vi.fn();
    component.animationSettled.subscribe(settled);

    ResizeObserverMock.instances[0]?.emit(500, 460);
    await flushFrame(); // resize work before focus preparation
    await advanceToReady();
    await flushFrame(16);
    await flushFrame(2400);
    fixture.detectChanges();

    expect(selectedCard('album-b').getAttribute('data-selected')).toBe('true');
    expect(settled).toHaveBeenCalledOnce();
    expect(focusState()).toBe('settled');
  });

  it('defers a resize received during the active loop and reapplies final geometry after settlement without restarting', async () => {
    const settled = vi.fn();
    component.animationSettled.subscribe(settled);
    await advanceToReady();
    await flushFrame(16);
    await flushFrame(300);

    const viewport = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.stage1-focus-viewport',
    );
    if (!viewport) throw new Error('Expected Stage 1 focus viewport');

    // The resize is observed while the animation is active, but the new viewport geometry is only
    // allowed to be committed after settlement. Making the target center materially different from
    // the initial 420x420 viewport proves the deferred pass is not merely scheduled: it must
    // recalculate and reapply the final camera/card geometry for 640x360.
    Object.defineProperty(viewport, 'clientWidth', { configurable: true, value: 640 });
    Object.defineProperty(viewport, 'clientHeight', { configurable: true, value: 360 });
    Object.defineProperty(viewport, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ left: 0, top: 0, right: 640, bottom: 360, width: 640, height: 360 }),
    });
    ResizeObserverMock.instances[0]?.emit(640, 360);

    // Browser RAF callbacks queued for the same paint tick run with the same timestamp. Execute the
    // active animation callback and resize callback as one batch so resize observes the next active
    // focus-loop frame and defers its geometry work instead of racing the test harness.
    await flushFrameBatch(16);
    expect(component.settled()).toBe(false);

    // Complete the next animation tick. Settlement must schedule exactly one deferred resize pass.
    await flushFrameBatch(2400);
    fixture.detectChanges();
    expect(settled).toHaveBeenCalledOnce();
    expect(component.settled()).toBe(true);

    const selected = selectedCard('album-b');
    const settledLeftBeforeResize = Number.parseFloat(selected.style.left);
    const settledTopBeforeResize = Number.parseFloat(selected.style.top);
    const settledWidthBeforeResize = Number.parseFloat(
      selected.style.getPropertyValue('--album-size'),
    );
    const settledHeightBeforeResize =
      settledWidthBeforeResize * (BASE_LAYOUT.selected.height / BASE_LAYOUT.selected.width);
    const scene = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.stage1-focus-scene',
    );
    if (!scene) throw new Error('Expected Stage 1 focus scene');
    expect(scene.style.transform).toBe('none');
    expect(queuedFrames.length).toBeGreaterThanOrEqual(1);

    // In a real browser the committed absolute card styles become the next layout offsets. jsdom
    // does not recalculate offsetLeft/offsetWidth from CSS, so mirror that browser behavior before
    // the deferred resize callback reads the current grid geometry.
    syncOffsetsToCommittedLayout(fixture.nativeElement as HTMLElement);

    await flushAllFrames();
    fixture.detectChanges();

    const resizedTransform = parseSceneTransform(scene.style.transform);
    expect(scene.style.transform).not.toBe('none');

    // A wide resized viewport can legitimately need translation only: getTargetTransform() clamps
    // camera scale to at least 1. Prove the deferred pass recomputed the camera from the new
    // 640x360 viewport by checking the selected card's resulting visual center, without coupling
    // this lifecycle test to the product's current focus-coverage constant.
    const selectedCenterX = settledLeftBeforeResize + settledWidthBeforeResize / 2;
    const selectedCenterY = settledTopBeforeResize + settledHeightBeforeResize / 2;
    expect(resizedTransform.scale).toBeGreaterThanOrEqual(1);
    expect(selectedCenterX * resizedTransform.scale + resizedTransform.x).toBeCloseTo(320, 4);
    expect(selectedCenterY * resizedTransform.scale + resizedTransform.y).toBeCloseTo(180, 4);
    expect(settled).toHaveBeenCalledOnce();
    expect(component.settled()).toBe(true);
    expect(focusState()).toBe('settled');
  });

  it('fails safely when the selected id is absent without emitting ready or settlement', async () => {
    fixture.destroy();
    createFixture({ selectedId: 'album-missing', animate: true });
    const ready = vi.fn();
    const settled = vi.fn();
    const failed = vi.fn();
    component.ready.subscribe(ready);
    component.animationSettled.subscribe(settled);
    component.failed.subscribe(failed);

    await flushAllFrames();
    await settleMicrotasks();

    expect(ready).not.toHaveBeenCalled();
    expect(settled).not.toHaveBeenCalled();
    expect(failed).toHaveBeenCalledOnce();
    expect(component.settled()).toBe(false);
  });

  it('continues the public focus lifecycle after rendered album artwork reports load errors', async () => {
    fixture.destroy();
    createFixture({ animate: true, imagesReady: false });
    const ready = vi.fn();
    component.ready.subscribe(ready);

    await flushFrame(); // enter image readiness
    await settleMicrotasks();
    for (const image of Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLImageElement>(
        'img.stage1-album-art',
      ),
    )) {
      image.dispatchEvent(new Event('error'));
    }
    await settleMicrotasks();
    await flushFrame(); // post-image measurement frame
    await settleMicrotasks();
    fixture.detectChanges();

    expect(ready).toHaveBeenCalledOnce();
    expect(component.focusReady()).toBe(true);
  });

  it('uses the bounded image timeout to continue focus preparation without leaving a timer behind', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    fixture.destroy();
    createFixture({ animate: true, imagesReady: false });
    const ready = vi.fn();
    component.ready.subscribe(ready);

    await flushFrame(); // enter image readiness
    await settleMicrotasks();
    await vi.advanceTimersByTimeAsync(2500);
    await settleMicrotasks();
    await flushFrame(); // post-timeout measurement frame
    await settleMicrotasks();
    fixture.detectChanges();

    expect(ready).toHaveBeenCalledOnce();
    expect(component.focusReady()).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('aborts image preparation and emits nothing when destroyed before pending artwork settles', async () => {
    fixture.destroy();
    createFixture({ animate: true, imagesReady: false });
    const ready = vi.fn();
    const settled = vi.fn();
    component.ready.subscribe(ready);
    component.animationSettled.subscribe(settled);

    await flushFrame(); // enter pending image wait
    await settleMicrotasks();
    const pendingImages = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLImageElement>(
        'img.stage1-album-art',
      ),
    );
    fixture.destroy();

    for (const image of pendingImages) image.dispatchEvent(new Event('load'));
    for (const stale of [...cancelledFrames]) stale.callback(now + 1000);
    await settleMicrotasks();

    expect(ready).not.toHaveBeenCalled();
    expect(settled).not.toHaveBeenCalled();
    expect(ResizeObserverMock.instances.at(-1)?.disconnect).toHaveBeenCalled();
    expect(removeResizeListener).toHaveBeenCalledWith('resize', expect.any(Function));
  });

  it('cancels the active RAF loop on destroy and ignores a stale callback delivered afterward', async () => {
    const settled = vi.fn();
    component.animationSettled.subscribe(settled);
    await advanceToReady();
    await flushFrame(16);
    await flushFrame(300);

    fixture.destroy();
    const staleLoop = cancelledFrames.at(-1);
    expect(staleLoop).toBeTruthy();
    now += 3000;
    staleLoop?.callback(now);
    await settleMicrotasks();

    expect(settled).not.toHaveBeenCalled();
    expect(ResizeObserverMock.instances[0]?.disconnect).toHaveBeenCalled();
    expect(removeResizeListener).toHaveBeenCalledWith('resize', expect.any(Function));
  });

  function createFixture(
    options: {
      readonly selectedId?: string;
      readonly animate?: boolean;
      readonly imagesReady?: boolean;
      readonly albums?: readonly AlbumCardVm[];
      readonly layout?: AlbumFocusLayout;
    } = {},
  ): void {
    queuedFrames = [];
    cancelledFrames = [];
    fixture = TestBed.createComponent(Stage1AlbumFocusComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput(
      'albums',
      options.albums ?? [
        album('album-a', 'Alpha'),
        album('album-b', 'Bravo'),
        album('album-c', 'Charlie'),
      ],
    );
    fixture.componentRef.setInput('selectedId', options.selectedId ?? 'album-b');
    fixture.componentRef.setInput('imageUrl', (image: string) => `/assets/${image}`);
    const layout = options.layout ?? BASE_LAYOUT;
    fixture.componentRef.setInput('originRect', layout.selected);
    fixture.componentRef.setInput('originLayout', layout);
    fixture.componentRef.setInput('animateInitialFocus', options.animate ?? true);
    fixture.componentRef.setInput('testId', 'focus-under-test');
    fixture.detectChanges();
    applyFocusMeasurements(fixture.nativeElement, layout, options.imagesReady ?? true);
  }

  function selectedCard(albumId: string): HTMLElement {
    return fixture.nativeElement.querySelector(
      `[data-testid="focus-under-test-card-${albumId}"]`,
    ) as HTMLElement;
  }

  function focusState(): string | null {
    return (
      fixture.nativeElement
        .querySelector('[data-testid="focus-under-test"]')
        ?.getAttribute('data-focus-state') ?? null
    );
  }

  async function advanceToReady(): Promise<void> {
    await flushFrame(); // scheduleFocus callback
    await settleMicrotasks();
    await flushFrame(); // waitForStage1AlbumImages final animation frame
    await settleMicrotasks();
    fixture.detectChanges();
    expect(component.focusReady()).toBe(true);
  }

  async function flushFrame(advanceMs = 16): Promise<void> {
    const frame = queuedFrames.shift();
    if (!frame) return;
    now += advanceMs;
    frame.callback(now);
    await settleMicrotasks();
    fixture.detectChanges();
  }

  async function flushFrameBatch(advanceMs = 16): Promise<void> {
    const batch = queuedFrames.splice(0, queuedFrames.length);
    if (batch.length === 0) {
      await settleMicrotasks();
      return;
    }
    now += advanceMs;
    for (const frame of batch) {
      frame.callback(now);
    }
    await settleMicrotasks();
  }

  async function flushAllFrames(limit = 30): Promise<void> {
    for (let index = 0; index < limit && queuedFrames.length > 0; index += 1) {
      await flushFrame(index > 2 ? 2400 : 16);
    }
  }
});

function syncOffsetsToCommittedLayout(root: HTMLElement): void {
  for (const card of Array.from(root.querySelectorAll<HTMLElement>('.stage1-focus-album-card'))) {
    const width = Number.parseFloat(card.style.getPropertyValue('--album-size'));
    const left = Number.parseFloat(card.style.left);
    const top = Number.parseFloat(card.style.top);
    if (![width, left, top].every(Number.isFinite)) continue;

    const albumId = card.dataset['albumId'];
    const source = BASE_LAYOUT.cards.find((candidate) => candidate.albumId === albumId);
    const aspectRatio = source ? source.height / source.width : 1;
    Object.defineProperty(card, 'offsetLeft', { configurable: true, value: left });
    Object.defineProperty(card, 'offsetTop', { configurable: true, value: top });
    Object.defineProperty(card, 'offsetWidth', { configurable: true, value: width });
    Object.defineProperty(card, 'offsetHeight', {
      configurable: true,
      value: width * aspectRatio,
    });
  }
}

function parseSceneTransform(transform: string): {
  readonly x: number;
  readonly y: number;
  readonly scale: number;
} {
  const match = transform.match(
    /^translate3d\((-?[\d.]+)px, (-?[\d.]+)px, 0\) scale\((-?[\d.]+)\)$/,
  );
  if (!match) throw new Error(`Unexpected Stage 1 focus transform: ${transform}`);
  const [, x, y, scale] = match;
  if (x === undefined || y === undefined || scale === undefined) {
    throw new Error(`Incomplete Stage 1 focus transform: ${transform}`);
  }
  return {
    x: Number.parseFloat(x),
    y: Number.parseFloat(y),
    scale: Number.parseFloat(scale),
  };
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

function applyFocusMeasurements(
  root: HTMLElement,
  layout: AlbumFocusLayout,
  imagesReady: boolean,
): void {
  const viewport = root.querySelector<HTMLElement>('.stage1-focus-viewport');
  const scene = root.querySelector<HTMLElement>('.stage1-focus-scene');
  if (!viewport || !scene) return;

  Object.defineProperty(viewport, 'clientWidth', { configurable: true, value: 420 });
  Object.defineProperty(viewport, 'clientHeight', { configurable: true, value: 420 });
  Object.defineProperty(viewport, 'getBoundingClientRect', {
    configurable: true,
    value: () => ({ left: 0, top: 0, right: 420, bottom: 420, width: 420, height: 420 }),
  });
  Object.defineProperty(scene, 'getBoundingClientRect', {
    configurable: true,
    value: () => ({ left: 0, top: 0, right: 420, bottom: 420, width: 420, height: 420 }),
  });

  const cardById = new Map(layout.cards.map((card) => [card.albumId, card]));
  for (const card of Array.from(root.querySelectorAll<HTMLElement>('.stage1-focus-album-card'))) {
    const albumId = card.dataset['albumId'] ?? '';
    const rect = cardById.get(albumId) ?? layout.selected;
    Object.defineProperty(card, 'offsetLeft', { configurable: true, value: rect.left });
    Object.defineProperty(card, 'offsetTop', { configurable: true, value: rect.top });
    Object.defineProperty(card, 'offsetWidth', { configurable: true, value: rect.width });
    Object.defineProperty(card, 'offsetHeight', { configurable: true, value: rect.height });
    Object.defineProperty(card, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({
        left: rect.left,
        top: rect.top,
        right: rect.left + rect.width,
        bottom: rect.top + rect.height,
        width: rect.width,
        height: rect.height,
      }),
    });

    const name = card.querySelector<HTMLElement>('.stage1-album-name');
    if (name) {
      Object.defineProperty(name, 'getBoundingClientRect', {
        configurable: true,
        value: () => ({
          left: rect.left,
          top: rect.top + rect.width + 10,
          right: rect.left + rect.width,
          bottom: rect.top + rect.width + 34,
          width: rect.width,
          height: 24,
        }),
      });
    }
  }

  for (const image of Array.from(root.querySelectorAll<HTMLImageElement>('img.stage1-album-art'))) {
    Object.defineProperty(image, 'complete', { configurable: true, value: imagesReady });
    Object.defineProperty(image, 'naturalWidth', {
      configurable: true,
      value: imagesReady ? 120 : 0,
    });
    image.decode = vi.fn().mockResolvedValue(undefined);
  }
}
