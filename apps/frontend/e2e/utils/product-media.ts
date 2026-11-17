import type { BrowserContext } from '@playwright/test';

/** Use a deterministic TV media clock while preserving the real playback handshake. */
export async function installDeterministicProductMedia(context: BrowserContext): Promise<void> {
  await context.addInitScript(() => {
    type Running = { startedAt: number; from: number; timer: number };
    const positions = new WeakMap<HTMLMediaElement, number>();
    const running = new WeakMap<HTMLMediaElement, Running>();
    const maximumSeconds = 60;

    const positionOf = (media: HTMLMediaElement): number => {
      const state = running.get(media);
      if (!state) return positions.get(media) ?? 0;
      return Math.min(maximumSeconds, state.from + (performance.now() - state.startedAt) / 1000);
    };

    const stop = (media: HTMLMediaElement): boolean => {
      const state = running.get(media);
      if (!state) return false;
      positions.set(media, positionOf(media));
      window.clearInterval(state.timer);
      running.delete(media);
      return true;
    };

    Object.defineProperty(HTMLMediaElement.prototype, 'currentTime', {
      configurable: true,
      get(this: HTMLMediaElement): number {
        return positionOf(this);
      },
      set(this: HTMLMediaElement, time: number) {
        const next = Number.isFinite(time) ? Math.max(0, time) : 0;
        positions.set(this, next);
        const state = running.get(this);
        if (state) {
          state.from = next;
          state.startedAt = performance.now();
        }
      },
    });

    HTMLMediaElement.prototype.load = function (this: HTMLMediaElement): void {
      stop(this);
      positions.set(this, 0);
      this.dispatchEvent(new Event('loadedmetadata'));
    };

    HTMLMediaElement.prototype.pause = function (this: HTMLMediaElement): void {
      if (stop(this)) this.dispatchEvent(new Event('pause'));
    };

    HTMLMediaElement.prototype.play = function (this: HTMLMediaElement): Promise<void> {
      if (running.has(this)) return Promise.resolve();

      const state: Running = {
        startedAt: performance.now(),
        from: positions.get(this) ?? 0,
        timer: 0,
      };
      running.set(this, state);
      this.dispatchEvent(new Event('play'));
      // Playing must occur after the application's playing$ subscription exists.
      queueMicrotask(() => {
        if (running.get(this) === state) this.dispatchEvent(new Event('playing'));
      });
      state.timer = window.setInterval(() => {
        if (running.get(this) !== state) return;
        this.dispatchEvent(new Event('timeupdate'));
        if (positionOf(this) >= maximumSeconds) {
          stop(this);
          this.dispatchEvent(new Event('ended'));
        }
      }, 50);
      return Promise.resolve();
    };
  });
}

export const productMediaInitializer = installDeterministicProductMedia;
