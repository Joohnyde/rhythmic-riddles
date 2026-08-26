import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GameSession } from '../../../core/session/game-session.service';
import { AlbumCardVm } from '../../../domain/game/models/album.model';
import { AlbumSelectionStore } from '../../../domain/game/state/album-selection.store';
import { AdminAlbumSelectionPage } from './admin-album-selection.page';

function album(id: string, name = id, picked = false): AlbumCardVm {
  return {
    id,
    name,
    image: id,
    pickedByTeam: picked ? '/team-icons/picker.png' : null,
    ordinalNumber: picked ? 1 : null,
    disabled: picked,
    pickedByAdmin: false,
  };
}

function setRenderedImagesReady(root: HTMLElement): void {
  for (const image of Array.from(root.querySelectorAll<HTMLImageElement>('img.stage1-album-art'))) {
    Object.defineProperty(image, 'complete', { configurable: true, value: true });
    Object.defineProperty(image, 'naturalWidth', { configurable: true, value: 120 });
    image.decode = vi.fn().mockResolvedValue(undefined);
  }
}

describe('AdminAlbumSelectionPage Stage 1 selection flow', () => {
  let fixture: ComponentFixture<AdminAlbumSelectionPage>;
  let animationFrame: typeof requestAnimationFrame;
  let showModal: HTMLDialogElement['showModal'] | undefined;
  let close: HTMLDialogElement['close'] | undefined;
  const messages$ = new Subject<never>();
  const pickAlbum = vi.fn().mockResolvedValue(undefined);
  const start = vi.fn().mockResolvedValue(undefined);
  const vm = signal({
    albums: [album('album-a', 'Alpha'), album('album-b', 'Bravo')],
    pickedByTeam: { id: 'team-a', name: 'Tempo', image: '/team-icons/team-a.png' },
    selectedAlbum: null,
    loaded: true,
    inTransit: false,
    showStartButton: false,
    animateSelectionFocus: false,
  });

  beforeEach(async () => {
    pickAlbum.mockClear();
    start.mockClear();
    vm.set({
      albums: [album('album-a', 'Alpha'), album('album-b', 'Bravo')],
      pickedByTeam: { id: 'team-a', name: 'Tempo', image: '/team-icons/team-a.png' },
      selectedAlbum: null,
      loaded: true,
      inTransit: false,
      showStartButton: false,
      animateSelectionFocus: false,
    });
    showModal = HTMLDialogElement.prototype.showModal;
    close = HTMLDialogElement.prototype.close;
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.removeAttribute('open');
    });
    animationFrame = window.requestAnimationFrame;
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(performance.now());
      return 1;
    });

    await TestBed.configureTestingModule({
      imports: [AdminAlbumSelectionPage],
      providers: [
        {
          provide: GameSession,
          useValue: { code: 'AKKU', messages$ },
        },
        {
          provide: AlbumSelectionStore,
          useValue: {
            vm,
            connect: vi.fn(),
            disconnect: vi.fn(),
            pickAlbum,
            start,
          },
        },
        { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminAlbumSelectionPage);
    fixture.detectChanges();
    setRenderedImagesReady(fixture.nativeElement);
  });

  afterEach(() => {
    fixture?.destroy();
    if (showModal) {
      HTMLDialogElement.prototype.showModal = showModal;
    } else {
      delete (HTMLDialogElement.prototype as Partial<HTMLDialogElement>).showModal;
    }
    if (close) {
      HTMLDialogElement.prototype.close = close;
    } else {
      delete (HTMLDialogElement.prototype as Partial<HTMLDialogElement>).close;
    }
    window.requestAnimationFrame = animationFrame;
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('opens confirmation for the clicked album and confirms that exact album once', async () => {
    const secondCard: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="admin-album-card-album-b"]',
    );
    secondCard.querySelector<HTMLButtonElement>('button')?.click();
    fixture.detectChanges();

    const dialog = [...fixture.nativeElement.querySelectorAll('dialog')].find(
      (candidate: HTMLDialogElement) => candidate.textContent?.includes('Bravo'),
    ) as HTMLDialogElement;
    const confirm = [...dialog.querySelectorAll('button')].find((button: HTMLButtonElement) =>
      button.textContent?.includes('YES'),
    ) as HTMLButtonElement;
    confirm.click();
    await fixture.whenStable();

    expect(dialog.open).toBe(false);
    expect(pickAlbum).toHaveBeenCalledOnce();
    expect(pickAlbum).toHaveBeenCalledWith('album-b');
  });

  it('keeps already-picked albums disabled so they cannot open a pick dialog', () => {
    fixture.destroy();
    vm.set({
      albums: [album('album-a', 'Alpha', true), album('album-b', 'Bravo')],
      pickedByTeam: { id: 'team-a', name: 'Tempo', image: '/team-icons/team-a.png' },
      selectedAlbum: null,
      loaded: true,
      inTransit: false,
      showStartButton: false,
      animateSelectionFocus: false,
    });
    fixture = TestBed.createComponent(AdminAlbumSelectionPage);
    fixture.detectChanges();
    setRenderedImagesReady(fixture.nativeElement);

    const pickedButton: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      '[data-testid="admin-album-card-album-a"] button',
    );
    pickedButton?.click();
    fixture.detectChanges();

    expect(pickedButton?.disabled).toBe(true);
    expect(
      [...fixture.nativeElement.querySelectorAll('dialog')].some(
        (dialog: HTMLDialogElement) => dialog.open,
      ),
    ).toBe(false);
  });
});
