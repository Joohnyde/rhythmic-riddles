import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { areStage1AlbumImagesReady, waitForStage1AlbumImages } from './stage1-album-images';

function createHost(): HTMLElement {
  const host = document.createElement('section');
  document.body.append(host);
  return host;
}

function appendAlbumImage(host: HTMLElement): HTMLImageElement {
  const card = document.createElement('article');
  card.className = 'stage1-album-card';
  card.dataset['albumId'] = `album-${host.children.length + 1}`;
  const image = document.createElement('img');
  image.className = 'stage1-album-art';
  card.append(image);
  host.append(card);
  return image;
}

function setImageState(image: HTMLImageElement, complete: boolean, naturalWidth: number): void {
  Object.defineProperty(image, 'complete', { configurable: true, value: complete });
  Object.defineProperty(image, 'naturalWidth', { configurable: true, value: naturalWidth });
}

describe('Stage 1 album image readiness', () => {
  let animationFrame: typeof requestAnimationFrame;

  beforeEach(() => {
    animationFrame = window.requestAnimationFrame;
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(performance.now());
      return 1;
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    window.requestAnimationFrame = animationFrame;
    document.body.replaceChildren();
    vi.restoreAllMocks();
  });

  it('reports ready only when every rendered album image completed with dimensions', () => {
    const host = createHost();
    const first = appendAlbumImage(host);
    const second = appendAlbumImage(host);
    setImageState(first, true, 120);
    setImageState(second, true, 0);

    expect(areStage1AlbumImagesReady(host)).toBe(false);

    setImageState(second, true, 120);

    expect(areStage1AlbumImagesReady(host)).toBe(true);
  });

  it('waits for pending album image load and decode before resolving', async () => {
    const host = createHost();
    const image = appendAlbumImage(host);
    const decode = vi.fn().mockResolvedValue(undefined);
    setImageState(image, false, 0);
    image.decode = decode;

    let resolved = false;
    const promise = waitForStage1AlbumImages(host).then(() => {
      resolved = true;
    });
    await Promise.resolve();

    expect(resolved).toBe(false);

    setImageState(image, true, 120);
    image.dispatchEvent(new Event('load'));
    await promise;

    expect(decode).toHaveBeenCalledTimes(1);
    expect(resolved).toBe(true);
  });

  it('treats failed image loads as settled so focus measurement is not permanently blocked', async () => {
    const host = createHost();
    const image = appendAlbumImage(host);
    setImageState(image, false, 0);

    const promise = waitForStage1AlbumImages(host);
    await Promise.resolve();

    setImageState(image, true, 0);
    image.dispatchEvent(new Event('error'));

    await expect(promise).resolves.toBeUndefined();
  });

  it('keeps going when decode rejects after a successful image load', async () => {
    const host = createHost();
    const image = appendAlbumImage(host);
    setImageState(image, true, 120);
    image.decode = vi.fn().mockRejectedValue(new Error('decode failed'));

    await expect(waitForStage1AlbumImages(host)).resolves.toBeUndefined();
  });

  it('resolves immediately for an already complete image after decode and cancels the losing timeout', async () => {
    vi.useFakeTimers();
    const host = createHost();
    const image = appendAlbumImage(host);
    const decode = vi.fn().mockResolvedValue(undefined);
    setImageState(image, true, 120);
    image.decode = decode;

    await expect(waitForStage1AlbumImages(host, { timeoutMs: 2500 })).resolves.toBeUndefined();

    expect(decode).toHaveBeenCalledOnce();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('uses a bounded timeout when an album image never settles', async () => {
    vi.useFakeTimers();
    const host = createHost();
    const image = appendAlbumImage(host);
    setImageState(image, false, 0);

    const promise = waitForStage1AlbumImages(host, { timeoutMs: 25 });
    await vi.advanceTimersByTimeAsync(25);

    await expect(promise).resolves.toBeUndefined();
  });

  it('removes pending listeners when timeout wins before a late load arrives', async () => {
    vi.useFakeTimers();
    const host = createHost();
    const image = appendAlbumImage(host);
    setImageState(image, false, 0);
    const addEventListener = vi.spyOn(image, 'addEventListener');
    const removeEventListener = vi.spyOn(image, 'removeEventListener');

    const promise = waitForStage1AlbumImages(host, { timeoutMs: 25 });
    await vi.advanceTimersByTimeAsync(25);
    await promise;

    setImageState(image, true, 120);
    image.dispatchEvent(new Event('load'));

    expect(addEventListener).toHaveBeenCalled();
    expect(removeEventListener).toHaveBeenCalledWith('load', expect.any(Function));
    expect(removeEventListener).toHaveBeenCalledWith('error', expect.any(Function));
  });

  it('ignores a late load after timeout already settled and cleaned its listeners', async () => {
    vi.useFakeTimers();
    const host = createHost();
    const image = appendAlbumImage(host);
    setImageState(image, false, 0);
    const decode = vi.fn().mockResolvedValue(undefined);
    image.decode = decode;

    const promise = waitForStage1AlbumImages(host, { timeoutMs: 25 });
    await vi.advanceTimersByTimeAsync(25);
    await promise;

    setImageState(image, true, 120);
    image.dispatchEvent(new Event('load'));
    await Promise.resolve();

    expect(decode).not.toHaveBeenCalled();
  });

  it('rejects immediately when image preparation starts with an already-aborted signal', async () => {
    const host = createHost();
    const abort = new AbortController();
    abort.abort();

    await expect(waitForStage1AlbumImages(host, { signal: abort.signal })).rejects.toThrow(
      'Stage 1 focus preparation was aborted.',
    );
  });

  it('can cancel a pending image wait during focus teardown', async () => {
    const host = createHost();
    const image = appendAlbumImage(host);
    const abort = new AbortController();
    setImageState(image, false, 0);

    const promise = waitForStage1AlbumImages(host, { signal: abort.signal });
    await Promise.resolve();
    abort.abort();

    await expect(promise).rejects.toThrow('Stage 1 focus preparation was aborted.');
  });
});
