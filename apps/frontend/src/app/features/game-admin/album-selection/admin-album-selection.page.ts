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
import { tap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { GameSession } from '../../../core/session/game-session.service';
import type { GameServerMessage } from '../../../domain/game/messages/game-server-message.types';
import { AlbumSelectionStore } from '../../../domain/game/state/album-selection.store';
import { Stage1AlbumCardComponent } from '../../../shared/ui/stage1-album-selection/album-card/stage1-album-card.component';
import { Stage1AlbumFocusComponent } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-focus.component';
import type { AlbumFocusLayout } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-focus.types';
import {
  areStage1AlbumImagesReady,
  waitForStage1AlbumImages,
} from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-images';
import { captureStage1AlbumLayout } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-origin';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog/confirm-dialog.component';
import { Stage1CategoryHeaderComponent } from '../../../shared/ui/stage1-album-selection/category-header/stage1-category-header.component';

type AlbumFocusPhase = 'idle' | 'measuring' | 'animating' | 'settled';

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
  readonly focusLayout = signal<AlbumFocusLayout | null>(null);
  readonly focusPhase = signal<AlbumFocusPhase>('idle');
  readonly focusSceneReady = signal(false);
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
  private requestedFocusAlbumId: string | null = null;
  private focusRequestToken = 0;
  private pendingFocusLayout: AlbumFocusLayout | null = null;
  private scrollFrame?: number;

  constructor() {
    effect(() => {
      const vm = this.store.vm();
      const selectedId = vm.selectedAlbum?.categoryId ?? null;
      if (!vm.loaded) {
        return;
      }

      if (!selectedId) {
        this.resetFocusRequest(!vm.inTransit);
        return;
      }

      if (selectedId !== this.requestedFocusAlbumId) {
        this.requestAlbumFocus(selectedId);
      }
    });
  }

  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router.navigate(['admin']);
      return;
    }
    this.store.connect(
      this.session.messages$.pipe(tap((message) => this.captureFocusOriginFromMessage(message))),
      'admin',
    );
  }

  ngOnDestroy(): void {
    this.store.disconnect();
    this.cancelScrollFrame();
  }

  async pickAlbum(categoryId: string): Promise<void> {
    await waitForStage1AlbumImages(this.host.nativeElement);
    this.pendingFocusLayout = this.captureAlbumLayout(categoryId);
    void this.store.pickAlbum(categoryId);
  }

  getAlbumImageUrl(image: string): string {
    const fileName = image.split('/').pop() ?? image;
    const albumId = fileName.replace(/\.[^.]+$/, '');
    return `${environment.apiUrl}/assets/v1/image/albums/${albumId}`;
  }

  onFocusReady(): void {
    this.focusSceneReady.set(true);
  }

  onFocusSettled(): void {
    this.focusPhase.set('settled');
  }

  private captureAlbumLayout(categoryId: string): AlbumFocusLayout | null {
    return captureStage1AlbumLayout(this.host.nativeElement, categoryId);
  }

  private captureFocusOriginFromMessage(message: GameServerMessage): void {
    if (message.type !== 'album_picked') {
      return;
    }

    const selectedId = message.selected?.categoryId;
    if (selectedId) {
      if (!areStage1AlbumImagesReady(this.host.nativeElement)) {
        this.pendingFocusLayout = null;
        return;
      }
      this.pendingFocusLayout = this.captureAlbumLayout(selectedId);
    }
  }

  private requestAlbumFocus(albumId: string): void {
    const token = ++this.focusRequestToken;
    this.requestedFocusAlbumId = albumId;
    this.focusSceneReady.set(false);
    this.focusLayout.set(null);
    this.focusPhase.set('measuring');
    void this.prepareAlbumFocus(albumId, token);
  }

  private async prepareAlbumFocus(albumId: string, token: number): Promise<void> {
    const pendingLayout = this.pendingFocusLayout;
    this.pendingFocusLayout = null;

    let layout = pendingLayout;
    if (!layout) {
      await this.nextFrame();
      await this.nextFrame();
      if (!this.isCurrentFocusRequest(albumId, token)) return;

      await waitForStage1AlbumImages(this.host.nativeElement);
      if (!this.isCurrentFocusRequest(albumId, token)) return;

      await this.ensureAlbumVisible(albumId);
      await this.nextFrame();
      if (!this.isCurrentFocusRequest(albumId, token)) return;

      layout = this.captureAlbumLayout(albumId);
    }

    if (!layout || !this.isCurrentFocusRequest(albumId, token)) {
      return;
    }

    this.focusLayout.set(layout);
    this.focusSceneReady.set(false);
    this.focusPhase.set('animating');
    this.changeDetector.detectChanges();
  }

  private async ensureAlbumVisible(albumId: string): Promise<void> {
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

    await this.animateScrollTop(container, targetTop);
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

  private animateScrollTop(container: HTMLElement, targetTop: number): Promise<void> {
    const startTop = container.scrollTop;
    if (this.prefersReducedMotion() || Math.abs(targetTop - startTop) < 1) {
      container.scrollTop = targetTop;
      return this.nextFrame();
    }

    this.cancelScrollFrame();
    return new Promise((resolve) => {
      const duration = 240;
      const startedAt = performance.now();
      const step = (now: number): void => {
        const progress = Math.min(1, Math.max(0, (now - startedAt) / duration));
        const eased = 1 - Math.pow(1 - progress, 3);
        container.scrollTop = startTop + (targetTop - startTop) * eased;

        if (progress < 1) {
          this.scrollFrame = requestAnimationFrame(step);
          return;
        }

        this.scrollFrame = undefined;
        resolve();
      };

      this.scrollFrame = requestAnimationFrame(step);
    });
  }

  private nextFrame(): Promise<void> {
    return new Promise((resolve) => requestAnimationFrame(() => resolve()));
  }

  private isCurrentFocusRequest(albumId: string, token: number): boolean {
    return this.focusRequestToken === token && this.requestedFocusAlbumId === albumId;
  }

  private resetFocusRequest(clearPendingLayout = true): void {
    this.requestedFocusAlbumId = null;
    this.focusRequestToken += 1;
    if (clearPendingLayout) {
      this.pendingFocusLayout = null;
    }
    this.focusLayout.set(null);
    this.focusSceneReady.set(false);
    this.focusPhase.set('idle');
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
}
