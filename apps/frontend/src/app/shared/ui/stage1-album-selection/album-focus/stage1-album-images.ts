import {
  Stage1AbortError,
  throwIfStage1Aborted,
  waitForStage1AnimationFrame,
  waitForStage1Timeout,
} from './stage1-focus-async';

const STAGE1_IMAGE_READY_TIMEOUT_MS = 2500;

/**
 * Stage 1 focus measurements are only trustworthy after album art has loaded and decoded.
 * Otherwise a recovered selection can snapshot zero-size or late-shifting image boxes, which shows
 * up as random gaps or the selected album animating from the wrong place after refresh.
 */
export function areStage1AlbumImagesReady(host: ParentNode): boolean {
  return albumArtImages(host).every((image) => image.complete && image.naturalWidth > 0);
}

export async function waitForStage1AlbumImages(
  host: ParentNode,
  options: { readonly signal?: AbortSignal; readonly timeoutMs?: number } = {},
): Promise<void> {
  throwIfStage1Aborted(options.signal);
  const images = albumArtImages(host);

  if (images.length === 0) {
    return;
  }

  const timeoutMs = options.timeoutMs ?? STAGE1_IMAGE_READY_TIMEOUT_MS;
  const listenerAbort = new AbortController();
  const abortPendingListeners = (): void => listenerAbort.abort();
  options.signal?.addEventListener('abort', abortPendingListeners, { once: true });

  try {
    await Promise.race([
      Promise.all(images.map((image) => waitForImageDecode(image, listenerAbort.signal))),
      // Share the same cancellation domain as image listeners. Whichever side wins the race, the
      // finally block aborts the loser so an early successful decode cannot leave a 2.5s timer alive.
      waitForStage1Timeout(timeoutMs, listenerAbort.signal),
    ]);
  } finally {
    options.signal?.removeEventListener('abort', abortPendingListeners);
    listenerAbort.abort();
  }

  await waitForStage1AnimationFrame(options.signal);
}

async function waitForImageDecode(image: HTMLImageElement, signal?: AbortSignal): Promise<void> {
  if (!image.complete) {
    // Load and error both count as settled. A failed cover should not block the room forever; it
    // just means focus uses the browser's final broken-image geometry.
    await new Promise<void>((resolve, reject) => {
      const finish = (): void => {
        image.removeEventListener('load', finish);
        image.removeEventListener('error', finish);
        signal?.removeEventListener('abort', abort);
        resolve();
      };
      const abort = (): void => {
        image.removeEventListener('load', finish);
        image.removeEventListener('error', finish);
        reject(new Stage1AbortError());
      };

      image.addEventListener('load', finish, { once: true });
      image.addEventListener('error', finish, { once: true });
      signal?.addEventListener('abort', abort, { once: true });
    });
  }

  if (image.naturalWidth === 0 || typeof image.decode !== 'function') {
    return;
  }

  try {
    await image.decode();
  } catch {
    // Broken or cached SVG responses can reject decode in some browsers. At that point the load
    // event has fired, so layout is stable enough for Stage 1 origin measurement.
  }
}

function albumArtImages(host: ParentNode): HTMLImageElement[] {
  return Array.from(
    host.querySelectorAll<HTMLImageElement>(
      '.stage1-album-card[data-album-id] img.stage1-album-art',
    ),
  );
}
