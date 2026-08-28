import { signal } from '@angular/core';
import type { AlbumFocusLayout, AlbumFocusPhase } from './stage1-album-focus.types';

export interface Stage1FocusRequest {
  readonly albumId: string;
  readonly token: number;
  readonly signal: AbortSignal;
}

/**
 * Presentation-only lifecycle coordinator shared by the Admin and TV Stage 1 pages.
 *
 * It owns request identity, cancellation, and focus-phase state, but intentionally knows nothing
 * about DOM geometry, scrolling, ResizeObserver, CSS transforms, or Store/domain state.
 */
export class Stage1FocusPresentationCoordinator {
  readonly layout = signal<AlbumFocusLayout | null>(null);
  readonly phase = signal<AlbumFocusPhase>('idle');
  readonly sceneReady = signal(false);

  private token = 0;
  private requestedAlbum: string | null = null;
  private abortController?: AbortController;
  private destroyed = false;

  get requestedAlbumId(): string | null {
    return this.requestedAlbum;
  }

  begin(albumId: string): Stage1FocusRequest {
    this.abortCurrent();
    const abortController = new AbortController();
    const request: Stage1FocusRequest = {
      albumId,
      token: ++this.token,
      signal: abortController.signal,
    };

    this.abortController = abortController;
    this.requestedAlbum = albumId;
    this.layout.set(null);
    this.sceneReady.set(false);
    this.phase.set('measuring');
    return request;
  }

  isCurrent(request: Stage1FocusRequest): boolean {
    return (
      !this.destroyed &&
      !request.signal.aborted &&
      request.token === this.token &&
      request.albumId === this.requestedAlbum
    );
  }

  commitLayout(request: Stage1FocusRequest, layout: AlbumFocusLayout): boolean {
    if (!this.isCurrent(request)) {
      return false;
    }

    this.layout.set(layout);
    this.sceneReady.set(false);
    this.phase.set('animating');
    return true;
  }

  markReady(): void {
    if (this.phase() === 'animating') {
      this.sceneReady.set(true);
    }
  }

  markSettled(): void {
    if (this.phase() === 'animating') {
      this.phase.set('settled');
    }
  }

  markFailed(): void {
    if (this.phase() !== 'animating') {
      return;
    }

    // Keep the selected album identity so a deterministic child-render failure does not trigger an
    // effect retry loop. The normal album scene remains visible and the page stays usable.
    this.layout.set(null);
    this.sceneReady.set(false);
    this.phase.set('idle');
  }

  fail(request: Stage1FocusRequest): void {
    if (!this.isCurrent(request)) {
      return;
    }

    // Keep the requested album identity so the reactive page effect does not spin retrying a
    // deterministic layout failure. The normal album scene remains available and the page usable.
    this.layout.set(null);
    this.sceneReady.set(false);
    this.phase.set('idle');
  }

  reset(): void {
    this.abortCurrent();
    this.requestedAlbum = null;
    this.token += 1;
    this.layout.set(null);
    this.sceneReady.set(false);
    this.phase.set('idle');
  }

  destroy(): void {
    this.destroyed = true;
    this.abortCurrent();
    this.token += 1;
    this.requestedAlbum = null;
    this.layout.set(null);
    this.sceneReady.set(false);
    this.phase.set('idle');
  }

  private abortCurrent(): void {
    this.abortController?.abort();
    this.abortController = undefined;
  }
}
