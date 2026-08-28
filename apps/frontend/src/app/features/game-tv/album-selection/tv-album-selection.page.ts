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
} from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { AlbumSelectionStore } from '../../../domain/game/state/album-selection.store';
import { Stage1AlbumFocusComponent } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-focus.component';
import { Stage1FocusPresentationCoordinator } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-focus-coordinator';
import type { Stage1FocusRequest } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-focus-coordinator';
import { captureStage1AlbumLayout } from '../../../shared/ui/stage1-album-selection/album-focus/stage1-album-origin';
import {
  isStage1AbortError,
  waitForStage1AnimationFrame,
} from '../../../shared/ui/stage1-album-selection/album-focus/stage1-focus-async';
import { Stage1CategoryHeaderComponent } from '../../../shared/ui/stage1-album-selection/category-header/stage1-category-header.component';
import { getStage1AlbumImageUrl } from '../../../shared/ui/stage1-album-selection/stage1-album-image-url';
import { Stage1TvAlbumMarqueeComponent } from '../../../shared/ui/stage1-album-selection/tv-album-marquee/stage1-tv-album-marquee.component';

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
  readonly focus = new Stage1FocusPresentationCoordinator();
  readonly focusLayout = this.focus.layout;
  readonly focusPhase = this.focus.phase;
  readonly focusSceneReady = this.focus.sceneReady;
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

  constructor() {
    effect(() => {
      const vm = this.store.vm();
      const selectedId = vm.selectedAlbum?.categoryId ?? null;
      if (!vm.loaded) {
        return;
      }

      if (!selectedId) {
        this.marquee?.resetFocusPositioning();
        this.focus.reset();
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
        .navigate(['tv'])
        .catch((error: unknown) => console.error('Stage 1 TV fallback navigation failed.', error));
      return;
    }
    this.store.connect(this.session.messages$, 'tv');
  }

  ngOnDestroy(): void {
    this.focus.destroy();
    this.store.disconnect();
  }

  readonly getAlbumImageUrl = (image: string): string => getStage1AlbumImageUrl(image);

  onFocusReady(): void {
    this.focus.markReady();
  }

  onFocusSettled(): void {
    this.focus.markSettled();
  }

  onFocusFailed(): void {
    // The marquee is still mounted until the focus child emits `ready`. If the child fails before
    // that hand-off, release the temporary focus offset so the normal carousel resumes in-place.
    this.marquee?.resetFocusPositioning();
    this.focus.markFailed();
  }

  private requestAlbumFocus(albumId: string): void {
    const request = this.focus.begin(albumId);
    void this.prepareAlbumFocus(request).catch((error: unknown) => {
      if (isStage1AbortError(error)) {
        return;
      }
      this.marquee?.resetFocusPositioning();
      this.focus.fail(request);
      console.error('Stage 1 TV focus preparation failed.', error);
    });
  }

  private async prepareAlbumFocus(request: Stage1FocusRequest): Promise<void> {
    await waitForStage1AnimationFrame(request.signal);
    await waitForStage1AnimationFrame(request.signal);
    if (!this.focus.isCurrent(request)) return;

    const layout =
      (await this.marquee?.prepareFocusLayout(request.albumId, request.signal)) ??
      captureStage1AlbumLayout(this.host.nativeElement, request.albumId);

    if (!layout || !this.focus.commitLayout(request, layout)) {
      return;
    }

    this.changeDetector.detectChanges();
  }
}
