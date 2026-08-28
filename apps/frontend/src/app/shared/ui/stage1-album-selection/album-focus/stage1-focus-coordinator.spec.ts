import { describe, expect, it } from 'vitest';
import type { AlbumFocusLayout } from './stage1-album-focus.types';
import { Stage1FocusPresentationCoordinator } from './stage1-focus-coordinator';

const LAYOUT: AlbumFocusLayout = {
  selected: { albumId: 'album-a', left: 10, top: 20, width: 100, height: 120 },
  cards: [{ albumId: 'album-a', left: 10, top: 20, width: 100, height: 120 }],
};

describe('Stage1FocusPresentationCoordinator', () => {
  it('supersedes an older request and accepts state changes only from the current request', () => {
    const coordinator = new Stage1FocusPresentationCoordinator();
    const first = coordinator.begin('album-a');
    const second = coordinator.begin('album-b');

    expect(first.signal.aborted).toBe(true);
    expect(coordinator.isCurrent(first)).toBe(false);
    expect(coordinator.isCurrent(second)).toBe(true);
    expect(coordinator.commitLayout(first, LAYOUT)).toBe(false);
    expect(coordinator.phase()).toBe('measuring');

    expect(coordinator.commitLayout(second, LAYOUT)).toBe(true);
    expect(coordinator.phase()).toBe('animating');
    coordinator.markReady();
    expect(coordinator.sceneReady()).toBe(true);
    coordinator.markSettled();
    expect(coordinator.phase()).toBe('settled');
  });

  it('aborts pending work on reset and returns to a clean idle presentation state', () => {
    const coordinator = new Stage1FocusPresentationCoordinator();
    const request = coordinator.begin('album-a');
    coordinator.commitLayout(request, LAYOUT);
    coordinator.markReady();

    coordinator.reset();

    expect(request.signal.aborted).toBe(true);
    expect(coordinator.requestedAlbumId).toBeNull();
    expect(coordinator.layout()).toBeNull();
    expect(coordinator.sceneReady()).toBe(false);
    expect(coordinator.phase()).toBe('idle');
  });

  it('prevents late completion after destroy', () => {
    const coordinator = new Stage1FocusPresentationCoordinator();
    const request = coordinator.begin('album-a');

    coordinator.commitLayout(request, LAYOUT);
    coordinator.markReady();
    coordinator.destroy();

    expect(request.signal.aborted).toBe(true);
    expect(coordinator.isCurrent(request)).toBe(false);
    expect(coordinator.commitLayout(request, LAYOUT)).toBe(false);
    expect(coordinator.requestedAlbumId).toBeNull();
    expect(coordinator.layout()).toBeNull();
    expect(coordinator.sceneReady()).toBe(false);
    expect(coordinator.phase()).toBe('idle');
  });

  it('returns a failed current child scene to idle without retrying the same selection', () => {
    const coordinator = new Stage1FocusPresentationCoordinator();
    const request = coordinator.begin('album-a');
    coordinator.commitLayout(request, LAYOUT);
    coordinator.markReady();

    coordinator.markFailed();

    expect(coordinator.requestedAlbumId).toBe('album-a');
    expect(coordinator.layout()).toBeNull();
    expect(coordinator.sceneReady()).toBe(false);
    expect(coordinator.phase()).toBe('idle');
  });
});
