/**
 * Stage 1 focus measurements are only trustworthy after album art has loaded and decoded.
 * Otherwise a recovered selection can snapshot zero-size or late-shifting image boxes, which shows
 * up as random gaps or the selected album animating from the wrong place after refresh.
 */
export function areStage1AlbumImagesReady(host: ParentNode): boolean {
  return albumArtImages(host).every((image) => image.complete && image.naturalWidth > 0);
}

export async function waitForStage1AlbumImages(host: ParentNode): Promise<void> {
  const images = albumArtImages(host);

  if (images.length === 0) {
    return;
  }

  await Promise.all(images.map((image) => waitForImageDecode(image)));
  await nextAnimationFrame();
}

async function waitForImageDecode(image: HTMLImageElement): Promise<void> {
  if (!image.complete) {
    // Load and error both count as settled. A failed cover should not block the room forever; it
    // just means focus uses the browser's final broken-image geometry.
    await new Promise<void>((resolve) => {
      const finish = (): void => {
        image.removeEventListener('load', finish);
        image.removeEventListener('error', finish);
        resolve();
      };

      image.addEventListener('load', finish, { once: true });
      image.addEventListener('error', finish, { once: true });
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

function nextAnimationFrame(): Promise<void> {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()));
}

function albumArtImages(host: ParentNode): HTMLImageElement[] {
  return Array.from(
    host.querySelectorAll<HTMLImageElement>(
      '.stage1-album-card[data-album-id] img.stage1-album-art',
    ),
  );
}
