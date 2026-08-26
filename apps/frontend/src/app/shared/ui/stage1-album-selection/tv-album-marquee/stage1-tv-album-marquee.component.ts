import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  effect,
  input,
  signal,
} from '@angular/core';
import { AlbumCardVm } from '../../../../domain/game/models/album.model';
import { Stage1AlbumCardComponent } from '../album-card/stage1-album-card.component';
import type { AlbumFocusLayout } from '../album-focus/stage1-album-focus.types';
import { waitForStage1AlbumImages } from '../album-focus/stage1-album-images';
import { captureStage1AlbumLayout } from '../album-focus/stage1-album-origin';
import { planStage1TvAlbumMarqueeLayout } from './stage1-tv-album-marquee-layout';

@Component({
  selector: 'rr-stage1-tv-album-marquee',
  standalone: true,
  imports: [Stage1AlbumCardComponent],
  templateUrl: './stage1-tv-album-marquee.component.html',
  styleUrl: './stage1-tv-album-marquee.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Stage1TvAlbumMarqueeComponent implements AfterViewInit, OnDestroy {
  @ViewChild('viewport') private readonly viewport?: ElementRef<HTMLElement>;
  @ViewChild('group') private readonly group?: ElementRef<HTMLElement>;

  readonly albums = input.required<readonly AlbumCardVm[]>();
  readonly imageUrl = input.required<(image: string) => string>();

  readonly layoutColumns = signal(2);
  readonly layoutRows = signal(2);
  readonly shouldLoop = signal(false);
  readonly loopDistance = signal(0);
  readonly positioningForFocus = signal(false);
  readonly focusOffset = signal(0);

  private resizeObserver?: ResizeObserver;
  private measureFrame?: number;
  private positionFrame?: number;

  constructor() {
    effect(() => {
      this.albums();
      if (this.viewport) {
        queueMicrotask(() => this.recalculate());
      }
    });
  }

  ngAfterViewInit(): void {
    const viewport = this.viewport?.nativeElement;
    if (!viewport) {
      return;
    }

    this.resizeObserver = new ResizeObserver(() => this.recalculate());
    this.resizeObserver.observe(viewport);
    this.recalculate();
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.cancelMeasureFrame();
    this.cancelPositionFrame();
  }

  albumImage(album: AlbumCardVm): string {
    return this.imageUrl()(album.image);
  }

  async prepareFocusLayout(albumId: string): Promise<AlbumFocusLayout | null> {
    const viewport = this.viewport?.nativeElement;
    const track = this.group?.nativeElement.parentElement;
    if (!viewport || !track) {
      return null;
    }

    await waitForStage1AlbumImages(viewport);
    await this.nextFrame();
    const currentOffset = this.currentTrackOffset(track);
    this.cancelPositionFrame();
    this.positioningForFocus.set(true);
    this.focusOffset.set(currentOffset);
    await this.nextFrame();

    let focusOffset = currentOffset;
    if (!this.isAlbumSufficientlyVisible(viewport, albumId)) {
      const candidate = this.bestAlbumCandidate(viewport, albumId);
      if (candidate) {
        const viewportRect = viewport.getBoundingClientRect();
        const rect = candidate.getBoundingClientRect();
        focusOffset =
          currentOffset + viewportRect.left + viewportRect.width / 2 - (rect.left + rect.width / 2);
        await this.animateFocusOffset(currentOffset, focusOffset);
      }
    }

    // Circular behavior belongs only to a real looping carousel. When looping, reveal the actual
    // neighboring column from the duplicated track before taking the focus snapshot. Moving the
    // whole track means all rows in that column come along together; we never synthesize a lone
    // first/last album next to the selection. A non-looping grid skips this entirely and therefore
    // keeps every card in its original rendered slot.
    if (this.shouldLoop()) {
      await this.revealAdjacentCarouselColumns(viewport, albumId, focusOffset);
    }

    await this.nextFrame();
    return captureStage1AlbumLayout(viewport, albumId);
  }

  private async revealAdjacentCarouselColumns(
    viewport: HTMLElement,
    albumId: string,
    currentOffset: number,
  ): Promise<void> {
    const selected = this.bestAlbumCandidate(viewport, albumId);
    if (!selected) {
      return;
    }

    const viewportRect = viewport.getBoundingClientRect();
    const selectedRect = selected.getBoundingClientRect();
    const columns = this.renderedColumnBounds(viewport, selectedRect);
    const left = columns.left;
    const right = columns.right;

    if (!left && !right) {
      return;
    }

    const desiredLeft = left?.left ?? selectedRect.left;
    const desiredRight = right?.right ?? selectedRect.right;
    const desiredSpan = desiredRight - desiredLeft;
    let delta = 0;

    if (desiredSpan <= viewportRect.width) {
      // Find the smallest translation that keeps the selected card plus both adjacent rendered
      // columns inside the viewport. This preserves as much of the current carousel position as
      // possible while revealing a complete missing column across all rows.
      const minimumDelta = viewportRect.left - desiredLeft;
      const maximumDelta = viewportRect.right - desiredRight;
      if (minimumDelta > 0) {
        delta = minimumDelta;
      } else if (maximumDelta < 0) {
        delta = maximumDelta;
      }
    } else {
      // Extremely narrow viewports cannot contain both adjacent columns at once. Center the
      // selected column rather than inventing geometry; the snapshot will then contain whichever
      // real neighboring columns are actually visible.
      delta =
        viewportRect.left + viewportRect.width / 2 - (selectedRect.left + selectedRect.width / 2);
    }

    if (Math.abs(delta) < 0.5) {
      return;
    }

    await this.animateFocusOffset(currentOffset, currentOffset + delta);
  }

  private renderedColumnBounds(
    viewport: HTMLElement,
    selectedRect: DOMRect,
  ): {
    readonly left: { readonly left: number; readonly right: number } | null;
    readonly right: { readonly left: number; readonly right: number } | null;
  } {
    const selectedCenterX = selectedRect.left + selectedRect.width / 2;
    const tolerance = Math.max(2, selectedRect.width * 0.35);
    const candidates = Array.from(
      viewport.querySelectorAll<HTMLElement>('.stage1-album-card[data-album-id]'),
    ).map((card) => card.getBoundingClientRect());

    const distinctCenters = candidates
      .map((rect) => rect.left + rect.width / 2)
      .filter((centerX) => Math.abs(centerX - selectedCenterX) > selectedRect.width * 0.55)
      .sort((a, b) => a - b);

    const leftCenter = [...distinctCenters]
      .filter((centerX) => centerX < selectedCenterX)
      .sort((a, b) => b - a)[0];
    const rightCenter = distinctCenters.find((centerX) => centerX > selectedCenterX);

    const boundsForCenter = (centerX: number | undefined) => {
      if (centerX === undefined) {
        return null;
      }

      const column = candidates.filter(
        (rect) => Math.abs(rect.left + rect.width / 2 - centerX) <= tolerance,
      );
      if (column.length === 0) {
        return null;
      }

      return {
        left: Math.min(...column.map((rect) => rect.left)),
        right: Math.max(...column.map((rect) => rect.right)),
      };
    };

    return {
      left: boundsForCenter(leftCenter),
      right: boundsForCenter(rightCenter),
    };
  }

  private recalculate(): void {
    const viewport = this.viewport?.nativeElement;
    const group = this.group?.nativeElement;
    if (!viewport || !group) {
      return;
    }

    const styles = getComputedStyle(viewport);
    const albumSize = Number.parseFloat(styles.getPropertyValue('--album-size')) || 128;
    const gap = Number.parseFloat(styles.getPropertyValue('--album-gap')) || 12;
    const layout = planStage1TvAlbumMarqueeLayout({
      albumCount: this.albums().length,
      availableWidth: viewport.clientWidth,
      albumSize,
      gap,
    });

    this.layoutColumns.set(layout.layoutColumns);
    this.layoutRows.set(layout.layoutRows);
    this.shouldLoop.set(layout.shouldLoop);

    this.cancelMeasureFrame();
    this.measureFrame = requestAnimationFrame(() => {
      this.measureFrame = undefined;
      const track = group.parentElement;
      const trackGap = track ? Number.parseFloat(getComputedStyle(track).gap) || 0 : 0;
      const distance = group.getBoundingClientRect().width + trackGap;
      this.loopDistance.set(distance);
    });
  }

  private cancelMeasureFrame(): void {
    if (this.measureFrame !== undefined) {
      cancelAnimationFrame(this.measureFrame);
      this.measureFrame = undefined;
    }
  }

  private isAlbumSufficientlyVisible(viewport: HTMLElement, albumId: string): boolean {
    const viewportRect = viewport.getBoundingClientRect();
    return this.albumCandidates(viewport, albumId).some((candidate) => {
      const rect = candidate.getBoundingClientRect();
      const visibleWidth = Math.max(
        0,
        Math.min(rect.right, viewportRect.right) - Math.max(rect.left, viewportRect.left),
      );
      const visibleHeight = Math.max(
        0,
        Math.min(rect.bottom, viewportRect.bottom) - Math.max(rect.top, viewportRect.top),
      );
      return (visibleWidth * visibleHeight) / Math.max(rect.width * rect.height, 1) >= 0.72;
    });
  }

  private bestAlbumCandidate(viewport: HTMLElement, albumId: string): HTMLElement | null {
    const viewportRect = viewport.getBoundingClientRect();
    const viewportCenterX = viewportRect.left + viewportRect.width / 2;
    const viewportCenterY = viewportRect.top + viewportRect.height / 2;

    return (
      this.albumCandidates(viewport, albumId).sort((a, b) => {
        const aRect = a.getBoundingClientRect();
        const bRect = b.getBoundingClientRect();
        const aDistance = Math.hypot(
          aRect.left + aRect.width / 2 - viewportCenterX,
          aRect.top + aRect.height / 2 - viewportCenterY,
        );
        const bDistance = Math.hypot(
          bRect.left + bRect.width / 2 - viewportCenterX,
          bRect.top + bRect.height / 2 - viewportCenterY,
        );
        return aDistance - bDistance;
      })[0] ?? null
    );
  }

  private albumCandidates(viewport: HTMLElement, albumId: string): HTMLElement[] {
    // The second marquee group is aria-hidden for accessibility, but it is still visually real.
    // Include both copies when deciding whether the selected album is already on screen so a
    // wrap-around copy is not unnecessarily scrolled away before the focus animation starts.
    return Array.from(
      viewport.querySelectorAll<HTMLElement>(
        `.stage1-album-card[data-album-id="${CSS.escape(albumId)}"]`,
      ),
    );
  }

  private currentTrackOffset(track: HTMLElement): number {
    const transform = getComputedStyle(track).transform;
    if (!transform || transform === 'none') {
      return 0;
    }

    return new DOMMatrixReadOnly(transform).m41;
  }

  private animateFocusOffset(from: number, to: number): Promise<void> {
    if (this.prefersReducedMotion() || Math.abs(to - from) < 0.5) {
      this.focusOffset.set(to);
      return this.nextFrame();
    }

    return new Promise((resolve) => {
      const duration = 260;
      const startedAt = performance.now();
      const step = (now: number): void => {
        const progress = Math.min(1, Math.max(0, (now - startedAt) / duration));
        const eased = 1 - Math.pow(1 - progress, 3);
        this.focusOffset.set(from + (to - from) * eased);

        if (progress < 1) {
          this.positionFrame = requestAnimationFrame(step);
          return;
        }

        this.positionFrame = undefined;
        resolve();
      };

      this.positionFrame = requestAnimationFrame(step);
    });
  }

  private nextFrame(): Promise<void> {
    return new Promise((resolve) => requestAnimationFrame(() => resolve()));
  }

  private prefersReducedMotion(): boolean {
    return (
      typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches
    );
  }

  private cancelPositionFrame(): void {
    if (this.positionFrame !== undefined) {
      cancelAnimationFrame(this.positionFrame);
      this.positionFrame = undefined;
    }
  }
}
