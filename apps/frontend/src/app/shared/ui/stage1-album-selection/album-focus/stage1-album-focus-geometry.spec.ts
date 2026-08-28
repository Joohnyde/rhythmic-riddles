import { describe, expect, it } from 'vitest';
import {
  findStage1FocusNeighbors,
  getStage1DiagonalMaskAxes,
  getStage1SpatialMaskAngle,
  type Stage1FocusGeometryCard,
} from './stage1-album-focus-geometry';

function card(albumId: string, column: number, row: number): Stage1FocusGeometryCard {
  return {
    albumId,
    sourceRect: {
      left: column * 120,
      top: row * 120,
      width: 100,
      height: 100,
    },
  };
}

function grid(albumIds: readonly string[], columns: number): Stage1FocusGeometryCard[] {
  return albumIds.map((albumId, index) =>
    card(albumId, index % columns, Math.floor(index / columns)),
  );
}

describe('Stage 1 album focus geometry', () => {
  it('finds all eight neighbors from the rendered 3x3 grid around a center selection', () => {
    const cards = grid(['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I'], 3);
    const selected = cards.find((candidate) => candidate.albumId === 'E');

    expect(selected).toBeDefined();
    expect(Object.fromEntries(findStage1FocusNeighbors(selected!, cards))).toEqual({
      D: 'left',
      F: 'right',
      B: 'top',
      A: 'top-left',
      C: 'top-right',
      H: 'bottom',
      G: 'bottom-left',
      I: 'bottom-right',
    });
  });

  it('does not wrap row-edge albums into phantom left or right neighbors', () => {
    const cards = grid(['A', 'B', 'C', 'D', 'E', 'F'], 3);
    const selected = cards.find((candidate) => candidate.albumId === 'D');

    expect(selected).toBeDefined();
    expect(Object.fromEntries(findStage1FocusNeighbors(selected!, cards))).toEqual({
      E: 'right',
      A: 'top',
      B: 'top-right',
    });
  });

  it('uses only existing neighbors when the selected album is in a rendered corner', () => {
    const cards = grid(['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I'], 3);
    const selected = cards.find((candidate) => candidate.albumId === 'A');

    expect(selected).toBeDefined();
    expect(Object.fromEntries(findStage1FocusNeighbors(selected!, cards))).toEqual({
      B: 'right',
      D: 'bottom',
      E: 'bottom-right',
    });
  });

  it('derives diagonal fade directions from physical card positions', () => {
    const selected = card('E', 1, 1).sourceRect;

    expect(getStage1SpatialMaskAngle(card('A', 0, 0).sourceRect, selected)).toBe('135deg');
    expect(getStage1SpatialMaskAngle(card('C', 2, 0).sourceRect, selected)).toBe('225deg');
    expect(getStage1SpatialMaskAngle(card('G', 0, 2).sourceRect, selected)).toBe('45deg');
    expect(getStage1SpatialMaskAngle(card('I', 2, 2).sourceRect, selected)).toBe('315deg');
  });

  it('maps diagonal fade directions to simultaneous horizontal and vertical masks', () => {
    expect(getStage1DiagonalMaskAxes('135deg')).toEqual(['90deg', '180deg']);
    expect(getStage1DiagonalMaskAxes('225deg')).toEqual(['270deg', '180deg']);
    expect(getStage1DiagonalMaskAxes('45deg')).toEqual(['90deg', '0deg']);
    expect(getStage1DiagonalMaskAxes('315deg')).toEqual(['270deg', '0deg']);
    expect(getStage1DiagonalMaskAxes('90deg')).toBeNull();
  });
});
