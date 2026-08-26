import type { AlbumFocusOrigin, Stage1NeighborDirection } from './stage1-album-focus.types';

export interface Stage1FocusGeometryCard {
  readonly albumId: string;
  readonly sourceRect: AlbumFocusOrigin;
}

interface RenderedRow<T extends Stage1FocusGeometryCard> {
  centerY: number;
  cards: T[];
}

/**
 * Finds the 3x3 neighborhood from rendered card rectangles, not array indexes.
 *
 * This keeps wrapped grids honest: the last album in one rendered row is not treated as the
 * left/right neighbor of the first album in the next row, and incomplete rows simply omit missing
 * diagonal/direct neighbors.
 */
export function findStage1FocusNeighbors<T extends Stage1FocusGeometryCard>(
  selected: T,
  cards: readonly T[],
): Map<string, Stage1NeighborDirection> {
  const selectedCenterX = centerX(selected);
  const neighbors = new Map<string, Stage1NeighborDirection>();
  const rows = getRenderedRows(cards, selected.sourceRect.height);
  const selectedRowIndex = rows.findIndex((row) =>
    row.cards.some((card) => card.albumId === selected.albumId),
  );

  if (selectedRowIndex === -1) {
    return neighbors;
  }

  const add = (card: T | null, direction: Stage1NeighborDirection): void => {
    if (!card || card.albumId === selected.albumId || neighbors.has(card.albumId)) {
      return;
    }
    neighbors.set(card.albumId, direction);
  };

  const selectedRow = rows[selectedRowIndex];
  add(nearestHorizontalNeighbor(selectedRow.cards, selectedCenterX, -1), 'left');
  add(nearestHorizontalNeighbor(selectedRow.cards, selectedCenterX, 1), 'right');

  const above = rows[selectedRowIndex - 1];
  if (above) {
    const top = nearestVerticalNeighbor(above.cards, selected);
    add(top, 'top');
    add(nearestHorizontalNeighbor(above.cards, selectedCenterX, -1, top), 'top-left');
    add(nearestHorizontalNeighbor(above.cards, selectedCenterX, 1, top), 'top-right');
  }

  const below = rows[selectedRowIndex + 1];
  if (below) {
    const bottom = nearestVerticalNeighbor(below.cards, selected);
    add(bottom, 'bottom');
    add(nearestHorizontalNeighbor(below.cards, selectedCenterX, -1, bottom), 'bottom-left');
    add(nearestHorizontalNeighbor(below.cards, selectedCenterX, 1, bottom), 'bottom-right');
  }

  neighbors.delete(selected.albumId);
  return neighbors;
}

/**
 * Returns the direction in which the transparency wave should travel across a card.
 *
 * The angle points from the card's far side toward the selected album, so the final visible
 * remnant is always the edge or corner physically closest to the selection.
 */
export function getStage1SpatialMaskAngle(
  card: AlbumFocusOrigin,
  selected: AlbumFocusOrigin,
): string {
  const cardCenterX = card.left + card.width / 2;
  const cardCenterY = card.top + card.height / 2;
  const selectedCenterX = selected.left + selected.width / 2;
  const selectedCenterY = selected.top + selected.height / 2;
  const dx = selectedCenterX - cardCenterX;
  const dy = selectedCenterY - cardCenterY;
  const absX = Math.abs(dx);
  const absY = Math.abs(dy);

  if (absX > absY * 1.35) {
    return dx > 0 ? '90deg' : '270deg';
  }

  if (absY > absX * 1.35) {
    return dy > 0 ? '180deg' : '0deg';
  }

  if (dx >= 0 && dy >= 0) return '135deg';
  if (dx < 0 && dy >= 0) return '225deg';
  if (dx >= 0 && dy < 0) return '45deg';
  return '315deg';
}

/**
 * Diagonal neighbors dissolve on both axes at once. CSS combines these two linear masks so, for
 * example, a top-left card naturally leaves a bottom-right corner instead of a horizontal strip.
 */
export function getStage1DiagonalMaskAxes(angle: string): readonly [string, string] | null {
  switch (angle) {
    case '135deg':
      return ['90deg', '180deg'];
    case '225deg':
      return ['270deg', '180deg'];
    case '45deg':
      return ['90deg', '0deg'];
    case '315deg':
      return ['270deg', '0deg'];
    default:
      return null;
  }
}

function getRenderedRows<T extends Stage1FocusGeometryCard>(
  cards: readonly T[],
  selectedHeight: number,
): RenderedRow<T>[] {
  // Row grouping deliberately uses physical Y centers with tolerance instead of a fixed column
  // count. Admin grids, wrapped desktop layouts, and TV carousel snapshots can all have different
  // resolved rows for the same album array.
  const rowTolerance = Math.max(4, selectedHeight * 0.35);
  const rows: RenderedRow<T>[] = [];

  for (const card of [...cards].sort((a, b) => centerY(a) - centerY(b))) {
    const cardCenterY = centerY(card);
    const row = rows.find((candidate) => Math.abs(candidate.centerY - cardCenterY) <= rowTolerance);
    if (row) {
      row.cards.push(card);
      row.centerY =
        row.cards.reduce((sum, rowCard) => sum + centerY(rowCard), 0) / row.cards.length;
    } else {
      rows.push({ centerY: cardCenterY, cards: [card] });
    }
  }

  return rows
    .sort((a, b) => a.centerY - b.centerY)
    .map((row) => ({
      ...row,
      cards: row.cards.sort((a, b) => centerX(a) - centerX(b)),
    }));
}

function nearestVerticalNeighbor<T extends Stage1FocusGeometryCard>(
  cards: readonly T[],
  selected: T,
): T | null {
  const selectedCenterX = centerX(selected);
  const maxCenterOffset = selected.sourceRect.width * 0.58;
  const candidate =
    [...cards].sort(
      (a, b) => Math.abs(centerX(a) - selectedCenterX) - Math.abs(centerX(b) - selectedCenterX),
    )[0] ?? null;

  if (!candidate || Math.abs(centerX(candidate) - selectedCenterX) > maxCenterOffset) {
    return null;
  }

  return candidate;
}

function nearestHorizontalNeighbor<T extends Stage1FocusGeometryCard>(
  cards: readonly T[],
  selectedCenterX: number,
  side: -1 | 1,
  except?: T | null,
): T | null {
  return (
    cards
      .filter((card) => card !== except)
      .filter((card) =>
        side < 0 ? centerX(card) < selectedCenterX : centerX(card) > selectedCenterX,
      )
      .sort(
        (a, b) => Math.abs(centerX(a) - selectedCenterX) - Math.abs(centerX(b) - selectedCenterX),
      )[0] ?? null
  );
}

function centerX(card: Stage1FocusGeometryCard): number {
  return card.sourceRect.left + card.sourceRect.width / 2;
}

function centerY(card: Stage1FocusGeometryCard): number {
  return card.sourceRect.top + card.sourceRect.height / 2;
}
