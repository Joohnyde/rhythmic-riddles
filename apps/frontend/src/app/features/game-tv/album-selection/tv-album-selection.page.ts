import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { GameSession } from '../../../core/session/game-session.service';
import { AlbumSelectionStore } from '../../../domain/game/state/album-selection.store';
import { Stage1AlbumFocusComponent } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-focus.component';
import type { AlbumFocusLayout } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-focus.types';
import { captureStage1AlbumLayout } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-origin';
import { Stage1CategoryHeaderComponent } from '../../../shared/ui/stage1-album-selection/category-header/stage1-category-header.component';
import { Stage1TvAlbumMarqueeComponent } from '../../../shared/ui/stage1-album-selection/tv-album-marquee/stage1-tv-album-marquee.component';

type AlbumFocusPhase = 'idle' | 'measuring' | 'animating' | 'settled';

@Component({
  selector: 'rr-tv-album-selection-page',
  imports: [
    Stage1CategoryHeaderComponent,
    Stage1TvAlbumMarqueeComponent,
    Stage1AlbumFocusComponent,
  ],
  templateUrl: './tv-album-selection.page.html',
  styleUrl: './tv-album-selection.page.scss',
})
export class TvAlbumSelectionPage implements OnInit, OnDestroy {
  @ViewChild(Stage1TvAlbumMarqueeComponent)
  private readonly marquee?: Stage1TvAlbumMarqueeComponent;

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

  constructor() {
    effect(() => {
      const vm = this.store.vm();
      const selectedId = vm.selectedAlbum?.categoryId ?? null;
      if (!vm.loaded) {
        return;
      }

      if (!selectedId) {
        this.resetFocusRequest();
        return;
      }

      if (selectedId !== this.requestedFocusAlbumId) {
        this.requestAlbumFocus(selectedId);
      }
    });
  }

  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router.navigate(['tv']);
      return;
    }
    this.store.connect(this.session.messages$, 'tv');
  }

  ngOnDestroy(): void {
    this.store.disconnect();
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

  private requestAlbumFocus(albumId: string): void {
    const token = ++this.focusRequestToken;
    this.requestedFocusAlbumId = albumId;
    this.focusSceneReady.set(false);
    this.focusLayout.set(null);
    this.focusPhase.set('measuring');
    void this.prepareAlbumFocus(albumId, token);
  }

  private async prepareAlbumFocus(albumId: string, token: number): Promise<void> {
    await this.nextFrame();
    await this.nextFrame();
    if (!this.isCurrentFocusRequest(albumId, token)) return;

    const layout =
      (await this.marquee?.prepareFocusLayout(albumId)) ??
      captureStage1AlbumLayout(this.host.nativeElement, albumId);

    if (!layout || !this.isCurrentFocusRequest(albumId, token)) {
      return;
    }

    this.focusLayout.set(layout);
    this.focusSceneReady.set(false);
    this.focusPhase.set('animating');
    this.changeDetector.detectChanges();
  }

  private nextFrame(): Promise<void> {
    return new Promise((resolve) => requestAnimationFrame(() => resolve()));
  }

  private isCurrentFocusRequest(albumId: string, token: number): boolean {
    return this.focusRequestToken === token && this.requestedFocusAlbumId === albumId;
  }

  private resetFocusRequest(): void {
    this.requestedFocusAlbumId = null;
    this.focusRequestToken += 1;
    this.focusLayout.set(null);
    this.focusSceneReady.set(false);
    this.focusPhase.set('idle');
  }
}
