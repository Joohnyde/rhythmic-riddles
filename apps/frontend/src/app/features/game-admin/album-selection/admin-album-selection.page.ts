import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { AlbumSelectionStore } from '../../../domain/game/state/album-selection.store';
import { Stage1AlbumCardComponent } from '../../../shared/ui/stage1-album-selection/album-card/stage1-album-card.component';
import { Stage1AlbumFocusComponent } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-focus.component';
import type { AlbumFocusLayout } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-focus.types';
import { Stage1FocusPresentationCoordinator } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-focus-coordinator';
import type { Stage1FocusRequest } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-focus-coordinator';
import { waitForStage1AlbumImages } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-images';
import { captureStage1AlbumLayout } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-origin';
import {
  Stage1AbortError,
  isStage1AbortError,
  waitForStage1AnimationFrame,
} from '../../../shared/ui/stage1-album-selection/album-focus/stage1-focus-async';
import { Stage1CategoryHeaderComponent } from '../../../shared/ui/stage1-album-selection/category-header/stage1-category-header.component';
import { getStage1AlbumImageUrl } from '../../../shared/ui/stage1-album-selection/stage1-album-image-url';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'rr-admin-album-selection-page',
  imports: [
    Stage1CategoryHeaderComponent,
    Stage1AlbumCardComponent,
    Stage1AlbumFocusComponent,
    ConfirmDialogComponent,
  ],
  templateUrl: './admin-album-selection.page.html',
  styleUrl: './admin-album-selection.page.scss',
})
export class AdminAlbumSelectionPage implements OnInit, OnDestroy {
  readonly session = inject(GameSession);
  readonly store = inject(AlbumSelectionStore);
  readonly focus = new Stage1FocusPresentationCoordinator();
  readonly focusLayout = this.focus.layout;
  readonly focusPhase = this.focus.phase;
  readonly focusSceneReady = this.focus.sceneReady;
  readonly pickPreparing = signal(false);
  readonly showNormalAlbums = computed(() => {
    const vm = this.store.vm();
    const phase = this.focusPhase();
    return (
      vm.loaded &&
      (!vm.selectedAlbum ||
        phase === 'idle' ||
        phase === 'measuring' ||
        (phase === 'animating' && !this.focusSceneReady()))
    );
  });
  readonly showFocusScene = computed(() => {
    const phase = this.focusPhase();
    return !!this.store.vm().selectedAlbum && (phase === 'animating' || phase === 'settled');
  });

  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly pageAbort = new AbortController();
  private pendingFocusLayout: {
    readonly albumId: string;
    readonly layout: AlbumFocusLayout;
  } | null = null;
  private scrollFrame?: number;

  constructor() {
    effect(() => {
      const vm = this.store.vm();
      const selectedId = vm.selectedAlbum?.categoryId ?? null;
      if (!vm.loaded) {
        return;
      }

      if (!selectedId) {
        this.focus.reset();
        // Keep the pre-pick geometry only while the REST pick is in flight. A normal selecting
        // state/recovery snapshot must not inherit geometry from a previous user action.
        if (!vm.inTransit && !this.pickPreparing()) {
          this.pendingFocusLayout = null;
        }
        return;
      }

      if (selectedId !== this.focus.requestedAlbumId) {
        this.requestAlbumFocus(selectedId);
      }
    });
  }

  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router
        .navigate(['admin'])
        .catch((error: unknown) => this.handleAsyncError('fallback navigation', error));
      return;
    }
    this.store.connect(this.session.messages$, 'admin');
  }

  ngOnDestroy(): void {
    this.pageAbort.abort();
    this.focus.destroy();
    this.store.disconnect();
    this.cancelScrollFrame();
  }

  pickAlbum(categoryId: string): void {
    const vm = this.store.vm();
    const album = vm.albums.find((candidate) => candidate.id === categoryId);
    if (
      this.pageAbort.signal.aborted ||
      this.pickPreparing() ||
      !vm.loaded ||
      vm.inTransit ||
      !!vm.selectedAlbum ||
      !album ||
      album.ordinalNumber !== null
    ) {
      return;
    }

    this.pickPreparing.set(true);
    void this.prepareAndPickAlbum(categoryId)
      .catch((error: unknown) => this.handleAsyncError('album pick', error))
      .finally(() => {
        if (!this.pageAbort.signal.aborted) {
          this.pickPreparing.set(false);
        }
      });
  }

  start(): void {
    void this.store.start().catch((error: unknown) => this.handleAsyncError('start', error));
  }

  readonly getAlbumImageUrl = (image: string): string => getStage1AlbumImageUrl(image);

  onFocusReady(): void {
    this.focus.markReady();
  }

  onFocusSettled(): void {
    this.focus.markSettled();
  }

  onFocusFailed(): void {
    this.focus.markFailed();
  }

  private async prepareAndPickAlbum(categoryId: string): Promise<void> {
    await waitForStage1AlbumImages(this.host.nativeElement, { signal: this.pageAbort.signal });

    // Image readiness is asynchronous. Re-check the application state before capturing pre-pick
    // geometry so a selection/reconnect that happened while images were settling cannot leave
    // stale coordinates for a different focus request.
    const vm = this.store.vm();
    const album = vm.albums.find((candidate) => candidate.id === categoryId);
    if (vm.inTransit || vm.selectedAlbum || !vm.loaded || !album || album.ordinalNumber !== null) {
      return;
    }

    const layout = this.captureAlbumLayout(categoryId);
    this.pendingFocusLayout = layout ? { albumId: categoryId, layout } : null;
    try {
      await this.store.pickAlbum(categoryId);
    } catch (error) {
      if (this.pendingFocusLayout?.albumId === categoryId) {
        this.pendingFocusLayout = null;
      }
      throw error;
    }
  }

  private captureAlbumLayout(categoryId: string): AlbumFocusLayout | null {
    return captureStage1AlbumLayout(this.host.nativeElement, categoryId);
  }

  private requestAlbumFocus(albumId: string): void {
    const request = this.focus.begin(albumId);
    void this.prepareAlbumFocus(request).catch((error: unknown) => {
      if (isStage1AbortError(error)) {
        return;
      }
      this.focus.fail(request);
      this.handleAsyncError('focus preparation', error);
    });
  }

  private async prepareAlbumFocus(request: Stage1FocusRequest): Promise<void> {
    const pendingLayout = this.pendingFocusLayout;
    this.pendingFocusLayout = null;

    // Pre-pick geometry is valid only for the exact album that produced it. A live/recovered
    // selection for another album must measure its own rendered position instead of inheriting a
    // stale user-action snapshot.
    let layout = pendingLayout?.albumId === request.albumId ? pendingLayout.layout : null;
    if (!layout) {
      await waitForStage1AnimationFrame(request.signal);
      await waitForStage1AnimationFrame(request.signal);
      if (!this.focus.isCurrent(request)) return;

      await waitForStage1AlbumImages(this.host.nativeElement, { signal: request.signal });
      if (!this.focus.isCurrent(request)) return;

      await this.ensureAlbumVisible(request.albumId, request.signal);
      await waitForStage1AnimationFrame(request.signal);
      if (!this.focus.isCurrent(request)) return;

      layout = this.captureAlbumLayout(request.albumId);
    }

    if (!layout || !this.focus.commitLayout(request, layout)) {
      return;
    }

    this.changeDetector.detectChanges();
  }

  private async ensureAlbumVisible(albumId: string, signal: AbortSignal): Promise<void> {
    const host = this.host.nativeElement as HTMLElement;
    const container = host.querySelector<HTMLElement>('.stage1-admin-album-viewport');
    const card = host.querySelector<HTMLElement>(
      `.stage1-album-card[data-album-id="${CSS.escape(albumId)}"]`,
    );
    if (!container || !card || this.isCardVisibleWithin(card, container)) {
      return;
    }

    const containerRect = container.getBoundingClientRect();
    const cardRect = card.getBoundingClientRect();
    const targetTop = Math.min(
      Math.max(
        0,
        container.scrollTop +
          cardRect.top +
          cardRect.height / 2 -
          (containerRect.top + containerRect.height / 2),
      ),
      container.scrollHeight - container.clientHeight,
    );

    await this.animateScrollTop(container, targetTop, signal);
  }

  private isCardVisibleWithin(card: HTMLElement, container: HTMLElement): boolean {
    const containerRect = container.getBoundingClientRect();
    const rect = card.getBoundingClientRect();
    const visibleWidth = Math.max(
      0,
      Math.min(rect.right, containerRect.right) - Math.max(rect.left, containerRect.left),
    );
    const visibleHeight = Math.max(
      0,
      Math.min(rect.bottom, containerRect.bottom) - Math.max(rect.top, containerRect.top),
    );
    return (visibleWidth * visibleHeight) / Math.max(rect.width * rect.height, 1) >= 0.72;
  }

  private animateScrollTop(
    container: HTMLElement,
    targetTop: number,
    signal: AbortSignal,
  ): Promise<void> {
    const startTop = container.scrollTop;
    if (this.prefersReducedMotion() || Math.abs(targetTop - startTop) < 1) {
      container.scrollTop = targetTop;
      return waitForStage1AnimationFrame(signal);
    }

    this.cancelScrollFrame();
    return new Promise((resolve, reject) => {
      const duration = 240;
      const startedAt = performance.now();
      const abort = (): void => {
        this.cancelScrollFrame();
        signal.removeEventListener('abort', abort);
        reject(new Stage1AbortError());
      };
      const step = (now: number): void => {
        if (signal.aborted) {
          abort();
          return;
        }

        const progress = Math.min(1, Math.max(0, (now - startedAt) / duration));
        const eased = 1 - Math.pow(1 - progress, 3);
        container.scrollTop = startTop + (targetTop - startTop) * eased;

        if (progress < 1) {
          this.scrollFrame = requestAnimationFrame(step);
          return;
        }

        this.scrollFrame = undefined;
        signal.removeEventListener('abort', abort);
        resolve();
      };

      signal.addEventListener('abort', abort, { once: true });
      this.scrollFrame = requestAnimationFrame(step);
    });
  }

  private prefersReducedMotion(): boolean {
    return (
      typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches
    );
  }

  private cancelScrollFrame(): void {
    if (this.scrollFrame !== undefined) {
      cancelAnimationFrame(this.scrollFrame);
      this.scrollFrame = undefined;
    }
  }

  private handleAsyncError(context: string, error: unknown): void {
    if (isStage1AbortError(error)) {
      return;
    }
    console.error(`Stage 1 Admin ${context} failed.`, error);
  }
}
