import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { AlbumCardVm } from '../../../../domain/game/models/album.model';
import {
  clampStage1Progress,
  formatStage1FocusTransform,
  interpolateStage1FocusTransform,
  lerpStage1,
  smoothstepStage1,
  transformStage1FocusRect,
} from './stage1-album-focus-animation';
import type { Stage1FocusTransform } from './stage1-album-focus-animation';
import { Stage1AlbumCardComponent } from '../album-card/stage1-album-card.component';
import {
  findStage1FocusNeighbors,
  getStage1DiagonalMaskAxes,
  getStage1SpatialMaskAngle,
} from './stage1-album-focus-geometry';
import type {
  AlbumFocusLayout,
  AlbumFocusOrigin,
  Stage1NeighborDirection,
} from './stage1-album-focus.types';
import { waitForStage1AlbumImages } from './stage1-album-images';
import { isStage1AbortError } from './stage1-focus-async';
import { createStage1DirectionalMask } from './stage1-album-focus-mask';

interface FocusCardMetrics {
  readonly cardWidth: number;
  readonly radius: number;
  readonly frameWidth: number;
  readonly nameFontSize: number;
  readonly nameMarginTop: number;
  readonly nameTop: number;
  readonly iconSize: number | null;
  readonly iconOffset: number | null;
}

interface FocusCardState {
  readonly element: HTMLElement;
  readonly albumId: string;
  readonly selected: boolean;
  readonly neighborDirection: Stage1NeighborDirection | null;
  readonly maskAngle: string;
  readonly fadeStart: number;
  readonly fadeEnd: number;
  readonly sourceRect: AlbumFocusOrigin;
  readonly metrics: FocusCardMetrics;
}

interface FocusAnimationState {
  readonly scene: HTMLElement;
  readonly startTransform: Stage1FocusTransform;
  readonly targetTransform: Stage1FocusTransform;
  readonly cards: readonly FocusCardState[];
}

const FOCUS_DURATION_MS = 2400;
// Keep the target prominent while leaving enough viewport space for nearby album remnants.
const FOCUS_COVERAGE = 0.37;
const IDENTITY_TRANSFORM: Stage1FocusTransform = { x: 0, y: 0, scale: 1 };
const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)';
const NORMAL_FADE_START = 0;
const NORMAL_FADE_END = 1;
// These neighbor tuning values preserve the existing choreography: neighbors fade with the field,
// then collapse to a small inward-facing remnant without changing the selected card target size.
const NEIGHBOR_FADE_START = 0;
const NEIGHBOR_FULL_FADE_END = 0.79;
const NEIGHBOR_COLLAPSE_START = 0.79;
const NEIGHBOR_FINAL_OPACITY = 0.3;
const NEIGHBOR_FINAL_VISIBLE_FRACTION = 0.15;
const NEIGHBOR_SOFT_TAIL_FRACTION = 0.3;

@Component({
  selector: 'rr-stage1-album-focus',
  standalone: true,
  imports: [Stage1AlbumCardComponent],
  templateUrl: './stage1-album-focus.component.html',
  styleUrl: './stage1-album-focus.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Stage1AlbumFocusComponent implements AfterViewInit, OnDestroy {
  private readonly changeDetector = inject(ChangeDetectorRef);

  @ViewChild('viewport') private readonly viewport?: ElementRef<HTMLElement>;
  @ViewChild('scene') private readonly scene?: ElementRef<HTMLElement>;
  @ViewChild('nameLayer') private readonly nameLayer?: ElementRef<HTMLElement>;

  readonly albums = input.required<readonly AlbumCardVm[]>();
  readonly selectedId = input.required<string>();
  readonly imageUrl = input.required<(image: string) => string>();
  readonly testId = input('stage1-album-focus');
  readonly animateInitialFocus = input(false);
  readonly originRect = input<AlbumFocusOrigin | null>(null);
  readonly originLayout = input<AlbumFocusLayout | null>(null);
  readonly focusAlbums = computed(() => {
    const layout = this.originLayout();
    if (!layout?.cards.length) {
      return this.albums();
    }

    const visibleAlbumIds = new Set(layout.cards.map((card) => card.albumId));
    return this.albums().filter((album) => visibleAlbumIds.has(album.id));
  });

  readonly ready = output<void>();
  readonly animationSettled = output<void>();
  readonly failed = output<void>();

  readonly focused = signal(false);
  readonly focusReady = signal(false);
  readonly absoluteLayout = signal(false);
  readonly settled = signal(false);
  readonly glowActive = signal(false);
  readonly neighborDirections = signal<ReadonlyMap<string, Stage1NeighborDirection>>(new Map());

  private viewInitialized = false;
  private focusFrame?: number;
  private resizeFrame?: number;
  private focusLoopFrame?: number;
  private resizeObserver?: ResizeObserver;
  private resizeListener?: () => void;
  private focusPrepareToken = 0;
  private focusPrepareAbort?: AbortController;
  private destroyed = false;
  private lastViewportWidth = 0;
  private lastViewportHeight = 0;
  private lastFocusedSelectionId: string | null = null;
  private resizeQueuedDuringAnimation = false;

  constructor() {
    effect(() => {
      const selectedId = this.selectedId();
      if (!this.viewInitialized || !selectedId || selectedId === this.lastFocusedSelectionId) {
        return;
      }

      this.scheduleFocus(true);
    });
  }

  ngAfterViewInit(): void {
    this.viewInitialized = true;
    const viewport = this.viewport?.nativeElement;
    if (!viewport) return;

    this.lastViewportWidth = viewport.clientWidth;
    this.lastViewportHeight = viewport.clientHeight;
    this.resizeObserver = new ResizeObserver((entries) => {
      const entry = entries[0];
      const width = entry?.contentRect.width ?? viewport.clientWidth;
      const height = entry?.contentRect.height ?? viewport.clientHeight;

      // ResizeObserver always delivers an initial observation. Treat only a real viewport-size
      // change as a resize; otherwise that first delivery can race the reveal animation and snap
      // the camera straight to its final transform.
      if (
        Math.abs(width - this.lastViewportWidth) < 1 &&
        Math.abs(height - this.lastViewportHeight) < 1
      ) {
        return;
      }

      this.lastViewportWidth = width;
      this.lastViewportHeight = height;
      this.scheduleResize();
    });
    this.resizeObserver.observe(viewport);

    this.resizeListener = () => this.scheduleResize();
    window.addEventListener('resize', this.resizeListener, { passive: true });

    // The parent page decides whether this mounted focus scene should replay the visible focus
    // movement. Live picks and recovered welcome selections now both pass animateInitialFocus.
    this.scheduleFocus(this.animateInitialFocus());
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.focusPrepareToken += 1;
    this.abortFocusPrepare();
    this.resizeObserver?.disconnect();
    if (this.resizeListener) {
      window.removeEventListener('resize', this.resizeListener);
    }
    this.cancelFocusFrame();
    this.cancelResizeFrame();
    this.cancelFocusLoop();
  }

  albumImage(album: AlbumCardVm): string {
    return this.imageUrl()(album.image);
  }

  neighborDirection(albumId: string): Stage1NeighborDirection | null {
    return this.neighborDirections().get(albumId) ?? null;
  }

  private scheduleFocus(animate: boolean): void {
    // Supersession is immediate: preparation, startup RAF, active animation RAF, deferred resize,
    // and stale visual state from the previous selection all belong to the old request.
    this.abortFocusPrepare();
    this.cancelFocusFrame();
    this.cancelFocusLoop();
    this.cancelResizeFrame();
    this.resizeQueuedDuringAnimation = false;
    this.resetFocusVisualState();

    const token = ++this.focusPrepareToken;
    const abortController = new AbortController();
    this.focusPrepareAbort = abortController;
    const frame = window.requestAnimationFrame(() => {
      if (this.focusFrame === frame) {
        this.focusFrame = undefined;
      }
      if (!this.isCurrentFocusPrepare(token)) return;
      void this.prepareFocus(animate, token, abortController.signal);
    });
    this.focusFrame = frame;
  }

  private async prepareFocus(animate: boolean, token: number, signal: AbortSignal): Promise<void> {
    try {
      const viewport = this.viewport?.nativeElement;
      const scene = this.scene?.nativeElement;
      if (!viewport || !scene) {
        this.failFocus(token);
        return;
      }

      await waitForStage1AlbumImages(scene, { signal });
      if (!this.isCurrentFocusPrepare(token)) return;

      const selected = this.findSelectedCard();
      if (!selected) {
        this.failFocus(token);
        return;
      }

      const wasReady = this.focusReady();
      const originState = this.createOriginState(viewport, scene, selected);
      const targetTransform = this.getTargetTransform(viewport, originState.selected.sourceRect);
      const startTransform =
        wasReady && !originState.absoluteLayout
          ? this.captureRenderedTransform(scene)
          : originState.startTransform;
      const neighborDirections = this.findImmediateNeighbors(
        originState.selected,
        originState.cards,
      );
      const cards = this.withNeighborDirections(originState.cards, neighborDirections);
      const animationState: FocusAnimationState = {
        scene,
        startTransform,
        targetTransform,
        cards,
      };

      this.cancelFocusLoop();
      this.resetCardEffects(scene);
      this.applySourceLayout(originState.cards);
      this.neighborDirections.set(neighborDirections);
      this.lastFocusedSelectionId = this.selectedId();
      this.settled.set(false);
      this.glowActive.set(false);

      if (!animate || this.prefersReducedMotion()) {
        // Establish the final transform before revealing the recovered focus scene so there is no
        // one-frame flash of the untransformed grid. Ready always precedes settled, even when motion
        // is reduced, so parent orchestration observes one consistent lifecycle contract.
        this.applyFocusProgress(animationState, 1);
        this.focused.set(true);
        this.focusReady.set(true);
        this.changeDetector.detectChanges();
        if (!this.isCurrentFocusPrepare(token)) return;
        this.ready.emit();
        if (!this.isCurrentFocusPrepare(token)) return;
        this.settleFocus(animationState, token);
        return;
      }

      // Put the scene at a concrete starting transform and reveal it first. Starting the focus loop
      // in the next animation frame gives the browser a paint opportunity at the start state instead
      // of batching setup and target into a single visual jump.
      this.applyFocusProgress(animationState, 0);
      this.focused.set(false);
      this.focusReady.set(true);
      this.changeDetector.detectChanges();
      if (!this.isCurrentFocusPrepare(token)) return;
      this.ready.emit();
      if (!this.isCurrentFocusPrepare(token)) return;
      const frame = window.requestAnimationFrame(() => {
        if (this.focusFrame === frame) {
          this.focusFrame = undefined;
        }
        if (!this.isCurrentFocusPrepare(token)) return;
        // Start camera movement, distance fade, and neighbor soft masks from one shared progress.
        this.focused.set(true);
        this.changeDetector.detectChanges();
        this.startFocusLoop(animationState, token);
      });
      this.focusFrame = frame;
    } catch (error) {
      if (!isStage1AbortError(error) && this.isCurrentFocusPrepare(token)) {
        this.failFocus(token);
        console.error('Stage 1 album focus preparation failed.', error);
      }
    }
  }

  private failFocus(token: number): void {
    if (!this.isCurrentFocusPrepare(token)) return;
    this.resetFocusVisualState();
    this.lastFocusedSelectionId = this.selectedId();
    this.failed.emit();
  }

  private captureRenderedTransform(scene: HTMLElement): Stage1FocusTransform {
    const renderedTransform = window.getComputedStyle(scene).transform;
    const transform =
      renderedTransform && renderedTransform !== 'none' ? renderedTransform : scene.style.transform;

    if (!transform || transform === 'none') {
      return IDENTITY_TRANSFORM;
    }

    const matrix = new DOMMatrixReadOnly(transform);
    return {
      x: matrix.m41,
      y: matrix.m42,
      scale: Math.hypot(matrix.a, matrix.b),
    };
  }

  private getOriginTransform(
    viewport: HTMLElement,
    selected: HTMLElement,
  ): Stage1FocusTransform | null {
    const origin = this.originRect();
    if (!origin || origin.width <= 0 || origin.height <= 0 || selected.offsetWidth <= 0) {
      return null;
    }

    // The picker and focus views intentionally use different grid alignment. Map the newly mounted
    // selected card back onto the exact viewport rectangle occupied by the picker card before the
    // state swap. This is the FLIP "first" pose: responsive row changes cannot produce a visible
    // jump because the selected card is painted in precisely the same place before the zoom starts.
    const viewportRect = viewport.getBoundingClientRect();
    const scale = origin.width / selected.offsetWidth;
    const originLeft = origin.left - viewportRect.left;
    const originTop = origin.top - viewportRect.top;
    const translateX = originLeft - selected.offsetLeft * scale;
    const translateY = originTop - selected.offsetTop * scale;

    return { x: translateX, y: translateY, scale };
  }

  private getTargetTransform(
    viewport: HTMLElement,
    selected: AlbumFocusOrigin,
  ): Stage1FocusTransform {
    const selectedLocalCenterX = selected.left + selected.width / 2;
    const selectedLocalCenterY = selected.top + selected.height / 2;
    const targetAlbumSize = Math.min(viewport.clientWidth, viewport.clientHeight) * FOCUS_COVERAGE;
    const scale = Math.max(1, targetAlbumSize / selected.width);
    const cameraX = viewport.clientWidth / 2 - selectedLocalCenterX * scale;
    const cameraY = viewport.clientHeight / 2 - selectedLocalCenterY * scale;

    return { x: cameraX, y: cameraY, scale };
  }

  private startFocusLoop(animationState: FocusAnimationState, token: number): void {
    this.cancelFocusLoop();

    const startedAt = performance.now();
    const scheduleStep = (): void => {
      const frame = window.requestAnimationFrame((now: number) => {
        // A cancelled RAF callback can still be delivered by test/browser queues. Only clear the
        // shared frame slot when this callback still owns it, and never let stale requests touch DOM.
        if (this.focusLoopFrame === frame) {
          this.focusLoopFrame = undefined;
        }
        if (!this.isCurrentFocusPrepare(token)) return;

        const progress = clampStage1Progress((now - startedAt) / FOCUS_DURATION_MS);
        this.applyFocusProgress(animationState, progress);

        if (progress < 1) {
          scheduleStep();
          return;
        }

        this.applyFocusProgress(animationState, 1);
        this.settleFocus(animationState, token);
      });
      this.focusLoopFrame = frame;
    };

    scheduleStep();
  }

  private applyFocusProgress(animationState: FocusAnimationState, rawProgress: number): void {
    // Camera, card fade, neighbor masks, and crisp text overlays all sample this same progress.
    // Keeping one source of truth prevents the staged "fade, then crop" feel from returning.
    const transform = interpolateStage1FocusTransform(
      animationState.startTransform,
      animationState.targetTransform,
      rawProgress,
    );

    animationState.scene.style.transform = formatStage1FocusTransform(transform);
    for (const card of animationState.cards) {
      this.applyCardProgress(card, rawProgress, transform.scale);
      this.applyNameOverlayProgress(card, transform);
    }
  }

  private applyCardProgress(card: FocusCardState, progress: number, cameraScale = 1): void {
    if (card.selected) {
      // Only the selected title needs the crisp overlay. Neighbor titles remain in their own card
      // so they fade in the same visual layer as the neighboring artwork and picker icon.
      this.applyActiveSelectedScale(card, cameraScale);
      card.element.style.setProperty('--stage1-focus-name-opacity', '0');
      card.element.style.opacity = '1';
      card.element.style.setProperty('--stage1-focus-visual-filter', 'none');
      this.clearMask(card.element);
      return;
    }

    this.applySpatialFadeProgress(card, progress);
  }

  private applySpatialFadeProgress(card: FocusCardState, progress: number): void {
    if (card.neighborDirection) {
      this.applyNeighborProgress(card, progress);
      return;
    }

    const wipeProgress = smoothstepStage1(card.fadeStart, card.fadeEnd, progress);
    const opacity = wipeProgress >= 0.999 ? 0 : 1 - wipeProgress;
    const grayscale = lerpStage1(0, 0.72, wipeProgress);
    const saturation = lerpStage1(1, 0.36, wipeProgress);
    const brightness = lerpStage1(1, 0.74, wipeProgress);

    card.element.style.opacity = `${opacity}`;
    card.element.style.setProperty('--stage1-focus-name-opacity', '1');
    card.element.style.setProperty(
      '--stage1-focus-visual-filter',
      `grayscale(${grayscale}) saturate(${saturation}) brightness(${brightness})`,
    );
    this.clearMask(card.element);
  }

  private applyNeighborProgress(card: FocusCardState, progress: number): void {
    const opacityProgress = smoothstepStage1(card.fadeStart, card.fadeEnd, progress);
    const collapseProgress = smoothstepStage1(NEIGHBOR_COLLAPSE_START, 1, progress);
    const opacity = lerpStage1(1, NEIGHBOR_FINAL_OPACITY, opacityProgress);
    const grayscale = lerpStage1(0, 0.68, opacityProgress);
    const saturation = lerpStage1(1, 0.42, opacityProgress);
    const brightness = lerpStage1(1, 0.8, opacityProgress);

    // Only the first left, top, and top-left neighbors keep a previously-picked team's icon
    // completely visible. Those are the close remnants that can sit partially behind the selected
    // album. Every other neighbor lets the icon use the same opacity/mask as its artwork so it
    // dissolves naturally with the cover. The art-wrap filter still makes preserved icons gray.
    const preservePickedIcon = this.shouldPreservePickedIcon(card.neighborDirection);
    card.element.style.opacity = '1';
    card.element.style.setProperty('--stage1-focus-content-opacity', `${opacity}`);
    card.element.style.setProperty('--stage1-focus-name-opacity', `${opacity}`);
    if (preservePickedIcon) {
      card.element.style.setProperty('--stage1-focus-icon-mask', 'none');
      card.element.style.setProperty('--stage1-focus-icon-opacity', '1');
    } else {
      card.element.style.removeProperty('--stage1-focus-icon-mask');
      card.element.style.removeProperty('--stage1-focus-icon-opacity');
    }
    card.element.style.setProperty(
      '--stage1-focus-visual-filter',
      `grayscale(${grayscale}) saturate(${saturation}) brightness(${brightness})`,
    );
    if (collapseProgress < 0.001) {
      this.clearMask(card.element);
      return;
    }

    this.applyCardMaskGeometry(card);
    const diagonalAxes = getStage1DiagonalMaskAxes(card.maskAngle);
    card.element.style.setProperty(
      '--stage1-focus-content-mask',
      createStage1DirectionalMask(
        card.maskAngle,
        collapseProgress,
        NEIGHBOR_FINAL_VISIBLE_FRACTION,
        NEIGHBOR_SOFT_TAIL_FRACTION,
      ),
    );
    if (diagonalAxes) {
      card.element.style.setProperty('--stage1-focus-mask-composite', 'intersect');
      card.element.style.setProperty('--stage1-focus-webkit-mask-composite', 'intersect');
    } else {
      card.element.style.removeProperty('--stage1-focus-mask-composite');
      card.element.style.removeProperty('--stage1-focus-webkit-mask-composite');
    }
  }

  private applyNameOverlayProgress(card: FocusCardState, transform: Stage1FocusTransform): void {
    const label = this.findNameOverlay(card.albumId);
    if (!label) return;

    const scale = Math.max(0.001, transform.scale);
    const left = card.sourceRect.left * transform.scale + transform.x;
    const top =
      card.sourceRect.top * transform.scale +
      transform.y +
      card.sourceRect.width * transform.scale +
      card.metrics.nameMarginTop * scale;
    const width = card.metrics.cardWidth * scale;
    const fontSize = card.metrics.nameFontSize * scale;

    label.style.left = `${this.snapToDevicePixel(left)}px`;
    label.style.top = `${this.snapToDevicePixel(top)}px`;
    label.style.width = `${this.snapToDevicePixel(width)}px`;
    label.style.fontSize = `${fontSize}px`;
    label.style.opacity = `${this.nameOverlayOpacity(card)}`;
  }

  private nameOverlayOpacity(card: FocusCardState): number {
    if (card.selected) return 1;

    return 0;
  }

  private findNameOverlay(albumId: string): HTMLElement | null {
    const layer = this.nameLayer?.nativeElement;
    if (!layer) return null;

    return layer.querySelector<HTMLElement>(`[data-focus-name-album-id="${CSS.escape(albumId)}"]`);
  }

  private snapToDevicePixel(value: number): number {
    const ratio = Math.max(1, window.devicePixelRatio || 1);
    return Math.round(value * ratio) / ratio;
  }

  private createOriginState(
    viewport: HTMLElement,
    scene: HTMLElement,
    selected: HTMLElement,
  ): {
    readonly absoluteLayout: boolean;
    readonly startTransform: Stage1FocusTransform;
    readonly selected: FocusCardState;
    readonly cards: readonly FocusCardState[];
  } {
    const layout = this.originLayout();
    if (layout?.cards.length) {
      // Recovery and carousel focus enter with measured source rectangles from the normal scene.
      // Replaying those rectangles as an absolute layout keeps the first focus frame identical to
      // what the player saw before the focus scene mounted.
      const layoutById = new Map(layout.cards.map((card) => [card.albumId, card]));
      const cards = Array.from(
        scene.querySelectorAll<HTMLElement>('.stage1-focus-album-card[data-album-id]'),
      ).map<FocusCardState>((card) => {
        const albumId = card.dataset['albumId'] ?? '';
        const source = layoutById.get(albumId) ?? {
          albumId,
          left: layout.selected.left,
          top: layout.selected.top,
          width: layout.selected.width,
          height: layout.selected.height,
        };

        return {
          element: card,
          albumId,
          selected: card === selected,
          neighborDirection: null,
          maskAngle: '90deg',
          fadeStart: NORMAL_FADE_START,
          fadeEnd: NORMAL_FADE_END,
          metrics: this.readCardMetrics(card),
          sourceRect: {
            left: source.left,
            top: source.top,
            width: source.width,
            height: source.height,
          },
        };
      });
      const selectedState = cards.find((card) => card.selected) ?? cards[0];
      const withSpatialMasks = this.withSpatialMaskAngles(cards, selectedState);
      this.absoluteLayout.set(true);

      return {
        absoluteLayout: true,
        startTransform: IDENTITY_TRANSFORM,
        selected: withSpatialMasks.find((card) => card.selected) ?? withSpatialMasks[0],
        cards: withSpatialMasks,
      };
    }

    this.absoluteLayout.set(false);
    const startTransform = this.getOriginTransform(viewport, selected) ?? IDENTITY_TRANSFORM;
    const cards = this.getGridFocusCardStates(selected, scene);
    const selectedState = cards.find((card) => card.selected) ?? cards[0];

    return {
      absoluteLayout: false,
      startTransform,
      selected: selectedState,
      cards,
    };
  }

  private getGridFocusCardStates(
    selected: HTMLElement,
    scene: HTMLElement,
  ): readonly FocusCardState[] {
    const cards = Array.from(
      scene.querySelectorAll<HTMLElement>('.stage1-focus-album-card[data-album-id]'),
    );

    const states = cards.map<FocusCardState>((card) => {
      const albumId = card.dataset['albumId'] ?? '';
      return {
        element: card,
        albumId,
        selected: card === selected,
        neighborDirection: null,
        maskAngle: '90deg',
        fadeStart: NORMAL_FADE_START,
        fadeEnd: NORMAL_FADE_END,
        metrics: this.readCardMetrics(card),
        sourceRect: {
          left: card.offsetLeft,
          top: card.offsetTop,
          width: card.offsetWidth,
          height: card.offsetHeight,
        },
      };
    });
    const selectedState = states.find((card) => card.selected) ?? states[0];

    return this.withSpatialMaskAngles(states, selectedState);
  }

  private withSpatialMaskAngles(
    cards: readonly FocusCardState[],
    selected: FocusCardState,
  ): readonly FocusCardState[] {
    return cards.map((card) => ({
      ...card,
      maskAngle: this.getSpatialMaskAngle(card.sourceRect, selected.sourceRect),
    }));
  }

  private withNeighborDirections(
    cards: readonly FocusCardState[],
    neighborDirections: ReadonlyMap<string, Stage1NeighborDirection>,
  ): readonly FocusCardState[] {
    return cards.map((card) => {
      const isNeighbor = neighborDirections.has(card.albumId);
      return {
        ...card,
        neighborDirection: neighborDirections.get(card.albumId) ?? null,
        fadeStart: isNeighbor ? NEIGHBOR_FADE_START : NORMAL_FADE_START,
        fadeEnd: isNeighbor ? NEIGHBOR_FULL_FADE_END : NORMAL_FADE_END,
      };
    });
  }

  private applySourceLayout(cards: readonly FocusCardState[]): void {
    const scene = this.scene?.nativeElement;
    if (scene) {
      scene.style.setProperty('will-change', 'transform');
    }

    if (!this.absoluteLayout()) {
      return;
    }

    for (const card of cards) {
      this.applyCardRect(card.element, card.sourceRect);
    }
  }

  private settleFocus(animationState: FocusAnimationState, token: number): boolean {
    if (!this.isCurrentFocusPrepare(token)) {
      return false;
    }

    for (const card of animationState.cards) {
      this.applyCardProgress(card, 1, animationState.targetTransform.scale);
      const finalRect = transformStage1FocusRect(card.sourceRect, animationState.targetTransform);
      this.applyCommittedCardScale(card, animationState.targetTransform.scale);
      this.applyCardRect(card.element, finalRect);
      this.applyCardMaskGeometry(card, animationState.targetTransform.scale);
    }

    // Commit the final camera scale into the same card nodes so artwork/frame/icon geometry stays
    // sharp after the animated scene transform is removed. Only the selected title remains on the
    // crisp overlay; surrounding titles stay with their masked card content.
    animationState.scene.style.transform = 'none';
    animationState.scene.style.removeProperty('will-change');
    this.settled.set(true);
    this.focused.set(true);
    this.focusReady.set(true);
    this.changeDetector.detectChanges();
    this.glowActive.set(true);
    this.changeDetector.detectChanges();
    if (!this.isCurrentFocusPrepare(token)) {
      return false;
    }
    this.animationSettled.emit();

    if (this.resizeQueuedDuringAnimation && this.isCurrentFocusPrepare(token)) {
      this.resizeQueuedDuringAnimation = false;
      this.scheduleResize();
    }
    return true;
  }

  private applyCardRect(card: HTMLElement, rect: AlbumFocusOrigin): void {
    card.style.position = 'absolute';
    card.style.left = `${rect.left}px`;
    card.style.top = `${rect.top}px`;
    card.style.setProperty('--album-size', `${rect.width}px`);
  }

  private applyActiveSelectedScale(card: FocusCardState, cameraScale: number): void {
    const scale = Math.max(1, cameraScale);
    const counterScale = 1 / scale;
    const nameWidth = card.metrics.cardWidth * scale;
    const nameOffset = (card.metrics.cardWidth - nameWidth) / 2;

    card.element.style.setProperty(
      '--stage1-album-name-font-size',
      `${card.metrics.nameFontSize * scale}px`,
    );
    card.element.style.setProperty('--stage1-album-name-width', `${nameWidth}px`);
    card.element.style.setProperty('--stage1-album-name-offset', `${nameOffset}px`);
    card.element.style.setProperty('--stage1-album-content-counter-scale', `${counterScale}`);
  }

  private applyCommittedCardScale(card: FocusCardState, scale: number): void {
    // Settling commits the camera scale into the same card node. That preserves the magnified
    // title/icon/frame proportions while letting Chromium rasterize text sharply after transform
    // animation has finished.
    card.element.style.setProperty('--album-radius', `${card.metrics.radius * scale}px`);
    card.element.style.setProperty(
      '--stage1-album-frame-width',
      `${card.metrics.frameWidth * scale}px`,
    );
    card.element.style.setProperty(
      '--stage1-album-name-font-size',
      `${card.metrics.nameFontSize * scale}px`,
    );
    card.element.style.setProperty(
      '--stage1-album-name-margin-top',
      `${card.metrics.nameMarginTop * scale}px`,
    );
    card.element.style.removeProperty('--stage1-album-name-width');
    card.element.style.removeProperty('--stage1-album-name-offset');
    card.element.style.removeProperty('--stage1-album-content-counter-scale');

    if (card.metrics.iconSize !== null) {
      card.element.style.setProperty(
        '--stage1-album-team-icon-size',
        `${card.metrics.iconSize * scale}px`,
      );
    }
    if (card.metrics.iconOffset !== null) {
      card.element.style.setProperty(
        '--stage1-album-team-icon-offset',
        `${card.metrics.iconOffset * scale}px`,
      );
    }
  }

  private applyCardMaskGeometry(card: FocusCardState, scale = 1): void {
    // The dissolve mask is a card-space field. Artwork, frame, and title sample the same gradient
    // image at different offsets, so the wave crosses the title only when it reaches the title's
    // actual position below the cover.
    const width = card.sourceRect.width * scale;
    const height = card.sourceRect.height * scale;
    const nameTop = card.metrics.nameTop * scale;

    card.element.style.setProperty('--stage1-focus-content-mask-size', `${width}px ${height}px`);
    card.element.style.setProperty('--stage1-focus-content-mask-position', '0px 0px');
    card.element.style.setProperty('--stage1-focus-name-mask-position', `0px -${nameTop}px`);

    if (card.metrics.iconSize !== null && card.metrics.iconOffset !== null) {
      const iconStart = width - card.metrics.iconSize * scale - card.metrics.iconOffset * scale;
      card.element.style.setProperty(
        '--stage1-focus-icon-mask-position',
        `${-iconStart}px ${-iconStart}px`,
      );
    } else {
      card.element.style.removeProperty('--stage1-focus-icon-mask-position');
    }
  }

  private readCardMetrics(card: HTMLElement): FocusCardMetrics {
    const art = card.querySelector<HTMLElement>('.stage1-album-art');
    const artWrap = card.querySelector<HTMLElement>('.stage1-album-art-wrap');
    const name = card.querySelector<HTMLElement>('.stage1-album-name');
    const icon = card.querySelector<HTMLElement>('.stage1-album-team-icon');
    const cardRect = card.getBoundingClientRect();
    const nameRect = name?.getBoundingClientRect();
    const artStyle = art ? getComputedStyle(art) : null;
    const artWrapFrameStyle = artWrap ? getComputedStyle(artWrap, '::before') : null;
    const nameStyle = name ? getComputedStyle(name) : null;
    const iconStyle = icon ? getComputedStyle(icon) : null;
    const frameInset = artWrapFrameStyle?.inset.split(' ')[0] ?? '';

    return {
      cardWidth: card.getBoundingClientRect().width || card.offsetWidth || 1,
      radius: this.readCssPixelValue(artStyle?.borderTopLeftRadius, 0),
      frameWidth: Math.abs(this.readCssPixelValue(frameInset, 1)),
      nameFontSize: this.readCssPixelValue(nameStyle?.fontSize, 12),
      nameMarginTop: this.readCssPixelValue(nameStyle?.marginTop, 0),
      nameTop:
        nameRect && cardRect.height > 0
          ? Math.max(0, nameRect.top - cardRect.top)
          : card.getBoundingClientRect().width + this.readCssPixelValue(nameStyle?.marginTop, 0),
      iconSize: icon ? this.readCssPixelValue(iconStyle?.width, 0) : null,
      iconOffset: icon ? this.readCssPixelValue(iconStyle?.right, 0) : null,
    };
  }

  private readCssPixelValue(value: string | undefined, fallback: number): number {
    const parsed = Number.parseFloat(value ?? '');
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  private resetFocusVisualState(): void {
    const scene = this.scene?.nativeElement;
    if (scene) {
      this.resetCardEffects(scene);
    }
    this.focused.set(false);
    this.focusReady.set(false);
    this.absoluteLayout.set(false);
    this.settled.set(false);
    this.glowActive.set(false);
    this.neighborDirections.set(new Map());
  }

  private resetCardEffects(scene: HTMLElement): void {
    const cards = scene.querySelectorAll<HTMLElement>('.stage1-focus-album-card[data-album-id]');
    scene.style.transform = '';
    scene.style.removeProperty('will-change');
    for (const card of cards) {
      card.style.opacity = '';
      card.style.filter = '';
      card.style.removeProperty('--stage1-focus-visual-filter');
      card.style.removeProperty('--stage1-focus-content-opacity');
      card.style.removeProperty('--stage1-focus-icon-mask');
      card.style.removeProperty('--stage1-focus-icon-mask-position');
      card.style.removeProperty('--stage1-focus-icon-opacity');
      card.style.removeProperty('--stage1-focus-name-opacity');
      card.style.position = '';
      card.style.left = '';
      card.style.top = '';
      card.style.removeProperty('--album-size');
      card.style.removeProperty('--album-radius');
      card.style.removeProperty('--stage1-album-frame-width');
      card.style.removeProperty('--stage1-album-name-font-size');
      card.style.removeProperty('--stage1-album-name-margin-top');
      card.style.removeProperty('--stage1-album-name-width');
      card.style.removeProperty('--stage1-album-name-offset');
      card.style.removeProperty('--stage1-album-content-counter-scale');
      card.style.removeProperty('--stage1-album-team-icon-size');
      card.style.removeProperty('--stage1-album-team-icon-offset');
      this.clearMask(card);
    }

    const labels = this.nameLayer?.nativeElement.querySelectorAll<HTMLElement>(
      '.stage1-focus-name-overlay',
    );
    for (const label of labels ?? []) {
      label.style.left = '';
      label.style.top = '';
      label.style.width = '';
      label.style.fontSize = '';
      label.style.opacity = '';
    }
  }

  private clearMask(card: HTMLElement): void {
    card.style.removeProperty('--stage1-focus-content-mask');
    card.style.removeProperty('--stage1-focus-mask-composite');
    card.style.removeProperty('--stage1-focus-webkit-mask-composite');
    card.style.removeProperty('--stage1-focus-content-mask-size');
    card.style.removeProperty('--stage1-focus-content-mask-position');
    card.style.removeProperty('--stage1-focus-name-mask-position');
    card.style.removeProperty('--stage1-focus-icon-mask-position');
    card.style.removeProperty('mask-image');
    card.style.removeProperty('-webkit-mask-image');
  }

  private scheduleResize(): void {
    this.cancelResizeFrame();
    const frame = window.requestAnimationFrame(() => {
      if (this.resizeFrame === frame) {
        this.resizeFrame = undefined;
      }
      if (this.destroyed) return;
      this.handleResize();
    });
    this.resizeFrame = frame;
  }

  private handleResize(): void {
    const viewport = this.viewport?.nativeElement;
    const scene = this.scene?.nativeElement;
    if (!viewport || !scene || !this.focused() || !this.selectedId()) return;

    const selected = this.findSelectedCard();
    if (!selected) return;

    // A viewport resize can change the CSS grid arrangement, so derive the surrounding neighbors again
    // from the current untransformed layout positions.
    if (this.focusLoopFrame !== undefined) {
      this.resizeQueuedDuringAnimation = true;
      return;
    }

    this.resizeQueuedDuringAnimation = false;

    const cards = this.getGridFocusCardStates(selected, scene);
    const selectedState = cards.find((card) => card.selected);
    if (!selectedState) return;

    const neighborDirections = this.findImmediateNeighbors(selectedState, cards);
    this.neighborDirections.set(neighborDirections);
    this.changeDetector.detectChanges();

    this.cancelFocusLoop();
    const targetTransform = this.getTargetTransform(viewport, selectedState.sourceRect);
    this.applyFocusProgress(
      {
        scene,
        startTransform: targetTransform,
        targetTransform,
        cards: this.withNeighborDirections(cards, neighborDirections),
      },
      1,
    );
  }

  private findSelectedCard(): HTMLElement | null {
    const scene = this.scene?.nativeElement;
    if (!scene) return null;
    return scene.querySelector<HTMLElement>(`[data-album-id="${CSS.escape(this.selectedId())}"]`);
  }

  private findImmediateNeighbors(
    selected: FocusCardState,
    cards: readonly FocusCardState[],
  ): Map<string, Stage1NeighborDirection> {
    return findStage1FocusNeighbors(selected, cards);
  }

  private shouldPreservePickedIcon(direction: Stage1NeighborDirection | null): boolean {
    return direction === 'left' || direction === 'top' || direction === 'top-left';
  }

  private getSpatialMaskAngle(card: AlbumFocusOrigin, selected: AlbumFocusOrigin): string {
    return getStage1SpatialMaskAngle(card, selected);
  }

  private prefersReducedMotion(): boolean {
    return (
      typeof window.matchMedia === 'function' && window.matchMedia(REDUCED_MOTION_QUERY).matches
    );
  }

  private cancelFocusFrame(): void {
    if (this.focusFrame !== undefined) {
      window.cancelAnimationFrame(this.focusFrame);
      this.focusFrame = undefined;
    }
  }

  private cancelResizeFrame(): void {
    if (this.resizeFrame !== undefined) {
      window.cancelAnimationFrame(this.resizeFrame);
      this.resizeFrame = undefined;
    }
  }

  private cancelFocusLoop(): void {
    if (this.focusLoopFrame !== undefined) {
      window.cancelAnimationFrame(this.focusLoopFrame);
      this.focusLoopFrame = undefined;
    }
  }

  private isCurrentFocusPrepare(token: number): boolean {
    return !this.destroyed && this.focusPrepareToken === token;
  }

  private abortFocusPrepare(): void {
    this.focusPrepareAbort?.abort();
    this.focusPrepareAbort = undefined;
  }
}
