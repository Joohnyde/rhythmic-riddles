import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GameSession } from '../../../core/session/game-session.service';
import { AlbumCardVm } from '../../../domain/game/models/album.model';
import { AlbumSelectionStore } from '../../../domain/game/state/album-selection.store';
import { Stage1AlbumFocusComponent } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-focus.component';
import { AdminAlbumSelectionPage } from './admin-album-selection.page';

interface AdminAlbumSelectionVm {
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

function album(id: string, name = id, picked = false): AlbumCardVm {
  return {
    id,
    name,
    image: id,
    pickedByTeam: picked ? TEAM.image : null,
    ordinalNumber: picked ? 1 : null,
  };
}

function selectedVm(albums: readonly AlbumCardVm[], categoryId = 'album-b'): AdminAlbumSelectionVm {
  const selected = albums.find((candidate) => candidate.id === categoryId);
  return {
    albums,
    pickedByTeam: TEAM,
    selectedAlbum: {
      categoryId,
      chosenCategoryPreview: {
        title: selected?.name ?? categoryId,
        image: selected?.image ?? categoryId,
      },
      pickedByTeam: TEAM,
      started: false,
      ordinalNumber: selected?.ordinalNumber ?? 1,
    },
    loaded: true,
    inTransit: false,
  };
}

class ResizeObserverMock {
  observe = vi.fn();
  disconnect = vi.fn();
}

describe('AdminAlbumSelectionPage Stage 1 page contract', () => {
  let fixture: ComponentFixture<AdminAlbumSelectionPage>;
  let queuedFrames: Array<{ id: number; callback: FrameRequestCallback }>;
  let frameId: number;
  let showModal: HTMLDialogElement['showModal'] | undefined;
  let close: HTMLDialogElement['close'] | undefined;
  let originalResizeObserver: typeof ResizeObserver | undefined;
  let originalDomMatrixReadOnly: typeof DOMMatrixReadOnly | undefined;

  const messages$ = new Subject<never>();
  const session: { code: string | null; messages$: Subject<never> | null } = {
    code: 'AKKU',
    messages$,
  };
  const connect = vi.fn();
  const disconnect = vi.fn();
  const pickAlbum = vi.fn().mockResolvedValue(undefined);
  const start = vi.fn().mockResolvedValue(undefined);
  const navigate = vi.fn().mockResolvedValue(true);
  const vm = signal<AdminAlbumSelectionVm>({
    albums: [album('album-a', 'Alpha'), album('album-b', 'Bravo')],
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
    pickAlbum.mockReset().mockResolvedValue(undefined);
    start.mockReset().mockResolvedValue(undefined);
    navigate.mockClear();
    session.code = 'AKKU';
    session.messages$ = messages$;
    vm.set({
      albums: [album('album-a', 'Alpha'), album('album-b', 'Bravo')],
      pickedByTeam: TEAM,
      selectedAlbum: null,
      loaded: true,
      inTransit: false,
    });

    showModal = HTMLDialogElement.prototype.showModal;
    close = HTMLDialogElement.prototype.close;
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.removeAttribute('open');
    });

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
      imports: [AdminAlbumSelectionPage],
      providers: [
        { provide: GameSession, useValue: session },
        { provide: AlbumSelectionStore, useValue: { vm, connect, disconnect, pickAlbum, start } },
        { provide: Router, useValue: { navigate } },
      ],
    }).compileComponents();

    createFixture();
  });

  afterEach(() => {
    fixture?.destroy();
    if (showModal) HTMLDialogElement.prototype.showModal = showModal;
    else delete (HTMLDialogElement.prototype as Partial<HTMLDialogElement>).showModal;
    if (close) HTMLDialogElement.prototype.close = close;
    else delete (HTMLDialogElement.prototype as Partial<HTMLDialogElement>).close;
    if (originalResizeObserver) globalThis.ResizeObserver = originalResizeObserver;
    else delete (globalThis as Partial<typeof globalThis>).ResizeObserver;
    if (originalDomMatrixReadOnly) globalThis.DOMMatrixReadOnly = originalDomMatrixReadOnly;
    else delete (globalThis as Partial<typeof globalThis>).DOMMatrixReadOnly;
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('connects once for a valid Admin session and disconnects once on destroy', () => {
    expect(connect).toHaveBeenCalledOnce();
    expect(connect).toHaveBeenCalledWith(messages$, 'admin');
    fixture.destroy();
    expect(disconnect).toHaveBeenCalledOnce();
  });

  it('routes away without connecting when session data is missing', () => {
    fixture.destroy();
    connect.mockClear();
    disconnect.mockClear();
    session.code = null;
    session.messages$ = null;
    createFixture();

    expect(connect).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['admin']);
  });

  it('renders loading for a fresh connection and canonical VM order once loaded', () => {
    fixture.destroy();
    vm.set({
      albums: [],
      pickedByTeam: null,
      selectedAlbum: null,
      loaded: false,
      inTransit: false,
    });
    createFixture();
    expect(
      fixture.nativeElement.querySelector('[data-testid="admin-albums-loading"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="admin-album-list"]')).toBeFalsy();

    vm.set({
      albums: [album('album-a', 'Alpha'), album('album-b', 'Bravo')],
      pickedByTeam: TEAM,
      selectedAlbum: null,
      loaded: true,
      inTransit: false,
    });
    fixture.detectChanges();
    const ids = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(
        '[data-testid^="admin-album-card-"]',
      ),
    )
      .map((card) => card.dataset['albumId'])
      .filter(Boolean);
    expect(ids).toEqual(['album-a', 'album-b']);
    expect(fixture.nativeElement.textContent).toContain('Tempo');
  });

  it('opens the exact album dialog, supports cancel, and confirms only that album once', async () => {
    openPickDialog('album-b');
    const dialog = openDialogContaining('Bravo');
    expect(dialog.textContent).toContain('Choose Bravo?');

    dialogButton(dialog, 'CANCEL').click();
    expect(dialog.open).toBe(false);
    expect(pickAlbum).not.toHaveBeenCalled();

    openPickDialog('album-b');
    const reopened = openDialogContaining('Bravo');
    dialogButton(reopened, 'YES').click();
    dialogButton(reopened, 'YES').click();
    await driveUntil(() => pickAlbum.mock.calls.length === 1);

    expect(pickAlbum).toHaveBeenCalledOnce();
    expect(pickAlbum).toHaveBeenCalledWith('album-b');
  });

  it('blocks picked albums, transit, and selected state from opening another selection action', () => {
    fixture.destroy();
    vm.set({
      albums: [album('album-a', 'Alpha', true), album('album-b', 'Bravo')],
      pickedByTeam: TEAM,
      selectedAlbum: null,
      loaded: true,
      inTransit: true,
    });
    createFixture();

    const picked = cardButton('album-a');
    const available = cardButton('album-b');
    expect(picked.disabled).toBe(true);
    expect(available.disabled).toBe(true);
    picked.click();
    available.click();
    expect(anyDialogOpen()).toBe(false);

    vm.set(selectedVm([album('album-a', 'Alpha'), album('album-b', 'Bravo', true)]));
    fixture.detectChanges();
    expect(cardButton('album-a').disabled).toBe(true);
  });

  it('catches a failed pick, clears page preparation state, and leaves the UI usable without a duplicate request', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    pickAlbum.mockRejectedValueOnce(new Error('pick failed'));
    openPickDialog('album-a');
    dialogButton(openDialogContaining('Alpha'), 'YES').click();
    await driveUntil(() => pickAlbum.mock.calls.length === 1);
    await settleMicrotasks();
    fixture.detectChanges();

    expect(pickAlbum).toHaveBeenCalledOnce();
    expect(fixture.componentInstance.pickPreparing()).toBe(false);
    expect(cardButton('album-a').disabled).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
    expect(consoleError).toHaveBeenCalledWith(
      'Stage 1 Admin album pick failed.',
      expect.any(Error),
    );
  });

  it('discards failed pre-pick geometry so a later selection measures its own album origin', async () => {
    prepareAdminGeometry(fixture.nativeElement);
    pickAlbum.mockRejectedValueOnce(new Error('pick failed'));
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    openPickDialog('album-a');
    dialogButton(openDialogContaining('Alpha'), 'YES').click();
    await driveUntil(() => pickAlbum.mock.calls.length === 1);
    await settleMicrotasks();
    expect(pickAlbum).toHaveBeenCalledOnce();

    const host = fixture.nativeElement as HTMLElement;
    const cards = Array.from(
      host.querySelectorAll<HTMLElement>('.stage1-album-card[data-album-id]'),
    );
    mockRect(cards[0], 40, 100, 100, 130);
    mockRect(cards[1], 420, 100, 100, 130);
    vm.set(selectedVm([album('album-a', 'Alpha'), album('album-b', 'Bravo', true)]));
    fixture.detectChanges();

    expect(pagePhase()).toBe('measuring');
    await flushFrame();
    await flushFrame();
    await flushFrame();
    await flushFrame();
    await settleMicrotasks();
    fixture.detectChanges();

    expect(fixture.componentInstance.focusLayout()?.selected.albumId).toBe('album-b');
    expect(fixture.componentInstance.focusLayout()?.selected.left).toBe(420);
  });

  it('aborts pending image preparation on destroy so a dead page cannot issue a late REST pick', async () => {
    setImagesPending(fixture.nativeElement);
    openPickDialog('album-a');
    dialogButton(openDialogContaining('Alpha'), 'YES').click();
    await settleMicrotasks();
    const pendingImages = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLImageElement>(
        'img.stage1-album-art',
      ),
    );

    fixture.destroy();
    for (const image of pendingImages) image.dispatchEvent(new Event('load'));
    await settleMicrotasks();

    expect(pickAlbum).not.toHaveBeenCalled();
  });

  it('automatically orchestrates a selected VM change through measuring, child-ready, and settled state', async () => {
    prepareAdminGeometry(fixture.nativeElement);
    vm.set(selectedVm([album('album-a', 'Alpha'), album('album-b', 'Bravo', true)]));
    fixture.detectChanges();
    expect(pagePhase()).toBe('measuring');

    // Live selection: two paint frames, image-ready frame, visibility frame, then layout commit.
    await flushFrame();
    await flushFrame();
    await flushFrame();
    await flushFrame();
    await settleMicrotasks();
    fixture.detectChanges();

    expect(pagePhase()).toBe('animating');
    const focus = findFocus();
    expect(focus.selectedId()).toBe('album-b');
    focus.ready.emit();
    fixture.detectChanges();
    expect(fixture.componentInstance.focusSceneReady()).toBe(true);
    expect(fixture.componentInstance.showNormalAlbums()).toBe(false);

    focus.animationSettled.emit();
    fixture.detectChanges();
    expect(pagePhase()).toBe('settled');
    expect(startButton().disabled).toBe(false);
  });

  it('replays a recovered selected VM that already exists when the Admin page is created', async () => {
    fixture.destroy();
    vm.set(selectedVm([album('album-a', 'Alpha'), album('album-b', 'Bravo', true)]));
    createFixture();
    prepareAdminGeometry(fixture.nativeElement);

    expect(pagePhase()).toBe('measuring');
    await driveUntil(() => pagePhase() === 'animating');

    const focus = findFocus();
    expect(focus.selectedId()).toBe('album-b');
    focus.ready.emit();
    fixture.detectChanges();
    focus.animationSettled.emit();
    fixture.detectChanges();

    expect(pagePhase()).toBe('settled');
    expect(startButton().disabled).toBe(false);
  });

  it('supersedes an in-flight focus preparation when a newer selected album arrives', async () => {
    const albums = [
      album('album-a', 'Alpha'),
      album('album-b', 'Bravo', true),
      album('album-c', 'Charlie', true),
    ];
    vm.set({ ...selectedVm(albums, 'album-b'), selectedAlbum: null });
    fixture.detectChanges();
    setImagesReady(fixture.nativeElement);
    prepareAdminGeometry(fixture.nativeElement);

    vm.set(selectedVm(albums, 'album-b'));
    fixture.detectChanges();
    expect(pagePhase()).toBe('measuring');
    expect(fixture.componentInstance.focus.requestedAlbumId).toBe('album-b');
    expect(queuedFrames).toHaveLength(1);

    vm.set(selectedVm(albums, 'album-c'));
    fixture.detectChanges();
    setImagesReady(fixture.nativeElement);
    prepareAdminGeometry(fixture.nativeElement);

    expect(fixture.componentInstance.focus.requestedAlbumId).toBe('album-c');
    // Aborting B removes its awaited paint frame; only C owns the active preparation frame.
    expect(queuedFrames).toHaveLength(1);

    await flushFrame();
    await flushFrame();
    await flushFrame();
    await flushFrame();
    await settleMicrotasks();
    fixture.detectChanges();

    expect(pagePhase()).toBe('animating');
    expect(findFocus().selectedId()).toBe('album-c');
  });

  it('falls back to the normal album scene when the child focus scene reports a current preparation failure', async () => {
    prepareAdminGeometry(fixture.nativeElement);
    vm.set(selectedVm([album('album-a', 'Alpha'), album('album-b', 'Bravo', true)]));
    fixture.detectChanges();
    await flushFrame();
    await flushFrame();
    await flushFrame();
    await flushFrame();
    await settleMicrotasks();
    fixture.detectChanges();

    expect(pagePhase()).toBe('animating');
    findFocus().failed.emit();
    fixture.detectChanges();

    expect(pagePhase()).toBe('idle');
    expect(fixture.componentInstance.showNormalAlbums()).toBe(true);
    expect(fixture.componentInstance.showFocusScene()).toBe(false);
  });

  it('cancels an active off-screen album scroll RAF when the page is destroyed', async () => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation(() => ({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    });
    const viewport = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.stage1-admin-album-viewport',
    );
    if (!viewport) throw new Error('Expected Admin album viewport');
    Object.defineProperty(viewport, 'clientHeight', { configurable: true, value: 200 });
    Object.defineProperty(viewport, 'scrollHeight', { configurable: true, value: 1000 });
    Object.defineProperty(viewport, 'scrollTop', { configurable: true, writable: true, value: 0 });
    mockRect(viewport, 0, 0, 700, 200);
    const cards = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(
        '.stage1-album-card[data-album-id]',
      ),
    );
    mockRect(cards[0], 50, 40, 100, 130);
    mockRect(cards[1], 50, 600, 100, 130);
    setImagesReady(fixture.nativeElement);

    vm.set(selectedVm([album('album-a', 'Alpha'), album('album-b', 'Bravo', true)]));
    fixture.detectChanges();
    await flushFrame();
    await flushFrame();
    await flushFrame();
    await settleMicrotasks();

    expect(pagePhase()).toBe('measuring');
    expect(queuedFrames).toHaveLength(1);

    fixture.destroy();

    expect(queuedFrames).toHaveLength(0);
    expect(fixture.componentInstance.focus.phase()).toBe('idle');
  });

  it('clears pending focus orchestration on destroy so late preparation cannot mutate the dead page', async () => {
    prepareAdminGeometry(fixture.nativeElement);
    vm.set(selectedVm([album('album-a', 'Alpha'), album('album-b', 'Bravo', true)]));
    fixture.detectChanges();
    expect(pagePhase()).toBe('measuring');

    fixture.destroy();
    for (const frame of [...queuedFrames]) frame.callback(performance.now());
    await settleMicrotasks();

    expect(fixture.componentInstance.focus.phase()).toBe('idle');
    expect(fixture.componentInstance.focus.requestedAlbumId).toBeNull();
  });

  it('starts exactly once through the Play dialog and blocks Play while Store transit is active', async () => {
    vm.set(selectedVm([album('album-a', 'Alpha'), album('album-b', 'Bravo', true)]));
    fixture.detectChanges();

    startButton().click();
    fixture.detectChanges();
    const dialog = openDialogContaining('Start the game?');
    dialogButton(dialog, 'YES').click();
    dialogButton(dialog, 'YES').click();
    await settleMicrotasks();
    expect(start).toHaveBeenCalledOnce();

    vm.set({
      ...selectedVm([album('album-a', 'Alpha'), album('album-b', 'Bravo', true)]),
      inTransit: true,
    });
    fixture.detectChanges();
    expect(startButton().disabled).toBe(true);
  });

  it('keeps Play disabled before selection and catches start failure without navigation or duplicate start', async () => {
    expect(startButton().disabled).toBe(true);

    vm.set(selectedVm([album('album-a', 'Alpha'), album('album-b', 'Bravo', true)]));
    fixture.detectChanges();
    start.mockRejectedValueOnce(new Error('start failed'));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    const button = startButton();
    expect(button.disabled).toBe(false);
    button.click();
    fixture.detectChanges();
    const dialog = openDialogContaining('Start the game?');
    dialogButton(dialog, 'YES').click();
    dialogButton(dialog, 'YES').click();
    await settleMicrotasks();

    expect(start).toHaveBeenCalledOnce();
    expect(navigate).not.toHaveBeenCalled();
    expect(startButton().disabled).toBe(false);
    expect(consoleError).toHaveBeenCalledWith('Stage 1 Admin start failed.', expect.any(Error));
  });

  function createFixture(): void {
    queuedFrames = [];
    fixture = TestBed.createComponent(AdminAlbumSelectionPage);
    fixture.detectChanges();
    setImagesReady(fixture.nativeElement);
  }

  function cardButton(id: string): HTMLButtonElement {
    return fixture.nativeElement.querySelector(
      `[data-testid="admin-album-card-${id}"] button`,
    ) as HTMLButtonElement;
  }

  function openPickDialog(id: string): void {
    cardButton(id).click();
    fixture.detectChanges();
  }

  function openDialogContaining(text: string): HTMLDialogElement {
    const dialog = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLDialogElement>('dialog'),
    ).find((candidate) => candidate.open && candidate.textContent?.includes(text));
    if (!dialog) throw new Error(`Expected open dialog containing ${text}`);
    return dialog;
  }

  function dialogButton(dialog: HTMLDialogElement, label: string): HTMLButtonElement {
    const button = Array.from(dialog.querySelectorAll<HTMLButtonElement>('button')).find(
      (candidate) => candidate.textContent?.includes(label),
    );
    if (!button) throw new Error(`Expected ${label} button`);
    return button;
  }

  function anyDialogOpen(): boolean {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLDialogElement>('dialog'),
    ).some((dialog) => dialog.open);
  }

  function startButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector(
      '[data-testid="admin-start-songs-button"]',
    ) as HTMLButtonElement;
  }

  function findFocus(): Stage1AlbumFocusComponent {
    return fixture.debugElement.query(
      (debug) => debug.componentInstance instanceof Stage1AlbumFocusComponent,
    ).componentInstance as Stage1AlbumFocusComponent;
  }

  function pagePhase(): string | null {
    return (
      fixture.nativeElement
        .querySelector('[data-testid="admin-albums-page"]')
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

function setImagesReady(root: HTMLElement): void {
  for (const image of Array.from(root.querySelectorAll<HTMLImageElement>('img.stage1-album-art'))) {
    Object.defineProperty(image, 'complete', { configurable: true, value: true });
    Object.defineProperty(image, 'naturalWidth', { configurable: true, value: 120 });
    image.decode = vi.fn().mockResolvedValue(undefined);
  }
}

function setImagesPending(root: HTMLElement): void {
  for (const image of Array.from(root.querySelectorAll<HTMLImageElement>('img.stage1-album-art'))) {
    Object.defineProperty(image, 'complete', { configurable: true, value: false });
    Object.defineProperty(image, 'naturalWidth', { configurable: true, value: 0 });
    image.decode = vi.fn().mockResolvedValue(undefined);
  }
}

function prepareAdminGeometry(root: HTMLElement): void {
  const viewport = root.querySelector<HTMLElement>('.stage1-admin-album-viewport');
  if (!viewport) return;
  Object.defineProperty(viewport, 'clientHeight', { configurable: true, value: 500 });
  Object.defineProperty(viewport, 'scrollHeight', { configurable: true, value: 500 });
  mockRect(viewport, 0, 0, 700, 500);
  const cards = Array.from(root.querySelectorAll<HTMLElement>('.stage1-album-card[data-album-id]'));
  cards.forEach((card, index) => mockRect(card, 50 + index * 130, 100, 100, 130));
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
