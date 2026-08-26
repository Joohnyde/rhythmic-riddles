import type { AlbumFocusCardOrigin, AlbumFocusLayout } from './stage1-album-focus.types';

interface AlbumOriginCandidate {
  readonly albumId: string;
  readonly rect: DOMRect;
  readonly visibleArea: number;
  readonly centerDistance: number;
}

interface ClipRect {
  readonly left: number;
  readonly top: number;
  readonly right: number;
  readonly bottom: number;
}

/**
 * Captures the album cards that are actually painted inside the current Stage 1 viewport.
 *
 * Carousel-specific positioning deliberately does not live here. A static grid must preserve its
 * exact rendered geometry, while a looping carousel may reposition its real duplicated track before
 * calling this function. Keeping those responsibilities separate prevents a normal grid edge from
 * being mistaken for a circular-carousel boundary.
 */
export function captureStage1AlbumLayout(
  host: ParentNode,
  selectedAlbumId: string,
): AlbumFocusLayout | null {
  // The looping TV marquee deliberately renders a second, aria-hidden copy for visual continuity.
  // Those cards are still visually real, so include them when deciding what is currently painted.
  const cards = Array.from(host.querySelectorAll<HTMLElement>('.stage1-album-card[data-album-id]'));

  if (cards.length === 0) {
    return null;
  }

  const clipRect = getVisibleClipRect(host);
  const clipCenterX = (clipRect.left + clipRect.right) / 2;
  const clipCenterY = (clipRect.top + clipRect.bottom) / 2;
  const allCandidates = cards
    .map<AlbumOriginCandidate>((card) => {
      const rect = card.getBoundingClientRect();
      const visibleWidth = Math.max(
        0,
        Math.min(rect.right, clipRect.right) - Math.max(rect.left, clipRect.left),
      );
      const visibleHeight = Math.max(
        0,
        Math.min(rect.bottom, clipRect.bottom) - Math.max(rect.top, clipRect.top),
      );

      return {
        albumId: card.dataset['albumId'] ?? '',
        rect,
        visibleArea: visibleWidth * visibleHeight,
        centerDistance: Math.hypot(
          rect.left + rect.width / 2 - clipCenterX,
          rect.top + rect.height / 2 - clipCenterY,
        ),
      };
    })
    .filter((candidate) => candidate.albumId);

  // A tiny sliver still counts as visible; the focus scene should preserve every album the user
  // could actually see at the instant selection happened.
  const visibleCandidates = allCandidates.filter((candidate) => candidate.visibleArea > 1);
  const candidatesByAlbum = new Map<string, AlbumOriginCandidate>();
  for (const candidate of visibleCandidates) {
    const current = candidatesByAlbum.get(candidate.albumId);
    if (
      !current ||
      candidate.visibleArea > current.visibleArea ||
      (candidate.visibleArea === current.visibleArea &&
        candidate.centerDistance < current.centerDistance)
    ) {
      candidatesByAlbum.set(candidate.albumId, candidate);
    }
  }

  // Normally the selected album is visible. Keep a nearest rendered fallback so recovery cannot
  // dead-end if layout timing catches it just outside the clipping rectangle.
  const selected =
    candidatesByAlbum.get(selectedAlbumId) ??
    nearestAlbumCandidate(allCandidates, selectedAlbumId, clipCenterX, clipCenterY);
  if (!selected) {
    return null;
  }
  candidatesByAlbum.set(selectedAlbumId, selected);

  const toOrigin = (candidate: AlbumOriginCandidate): AlbumFocusCardOrigin => ({
    albumId: candidate.albumId,
    left: candidate.rect.left,
    top: candidate.rect.top,
    width: candidate.rect.width,
    height: candidate.rect.height,
  });

  return {
    selected: toOrigin(selected),
    cards: Array.from(candidatesByAlbum.values()).map(toOrigin),
  };
}

function nearestAlbumCandidate(
  candidates: readonly AlbumOriginCandidate[],
  albumId: string,
  centerXValue: number,
  centerYValue: number,
): AlbumOriginCandidate | null {
  return (
    candidates
      .filter((candidate) => candidate.albumId === albumId)
      .sort((left, right) => {
        const leftDistance = Math.hypot(centerX(left) - centerXValue, centerY(left) - centerYValue);
        const rightDistance = Math.hypot(
          centerX(right) - centerXValue,
          centerY(right) - centerYValue,
        );
        return leftDistance - rightDistance;
      })[0] ?? null
  );
}

function centerX(candidate: AlbumOriginCandidate): number {
  return candidate.rect.left + candidate.rect.width / 2;
}

function centerY(candidate: AlbumOriginCandidate): number {
  return candidate.rect.top + candidate.rect.height / 2;
}

function getVisibleClipRect(host: ParentNode): ClipRect {
  const viewportWidth = window.innerWidth || document.documentElement.clientWidth;
  const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
  const windowRect: ClipRect = { left: 0, top: 0, right: viewportWidth, bottom: viewportHeight };

  if (!(host instanceof HTMLElement)) {
    return windowRect;
  }

  // Admin clips the grid inside its scroll viewport. TV passes the marquee viewport itself.
  const localViewport =
    (host.matches('.stage1-admin-album-viewport, .stage1-tv-album-marquee') ? host : null) ??
    host.querySelector<HTMLElement>('.stage1-admin-album-viewport, .stage1-tv-album-marquee');
  if (!localViewport) {
    return windowRect;
  }

  const rect = localViewport.getBoundingClientRect();
  return {
    left: Math.max(windowRect.left, rect.left),
    top: Math.max(windowRect.top, rect.top),
    right: Math.min(windowRect.right, rect.right),
    bottom: Math.min(windowRect.bottom, rect.bottom),
  };
}
