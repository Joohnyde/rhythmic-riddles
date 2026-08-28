import { describe, expect, it, vi } from 'vitest';
import { CategorySimple } from './album.model';
import { stableAlbumOrder } from './stage1-album-order';

function album(id: string, name: string): CategorySimple {
  return {
    id,
    name,
    image: `${id}.png`,
    pickedByTeam: null,
    ordinalNumber: null,
  };
}

describe('stableAlbumOrder', () => {
  it('returns the same canonical order for the same albums regardless of backend input order', () => {
    const first = [album('3', 'Bravo'), album('1', 'Alpha'), album('2', 'alpha')];
    const second = [album('2', 'alpha'), album('3', 'Bravo'), album('1', 'Alpha')];

    expect(stableAlbumOrder(first).map((value) => value.id)).toEqual(['1', '2', '3']);
    expect(stableAlbumOrder(second).map((value) => value.id)).toEqual(['1', '2', '3']);
  });

  it('uses the album id as a deterministic tie-breaker for duplicate names', () => {
    expect(
      stableAlbumOrder([album('b-id', 'Same Name'), album('a-id', 'Same Name')]).map(
        (value) => value.id,
      ),
    ).toEqual(['a-id', 'b-id']);
  });

  it('does not depend on localeCompare at runtime', () => {
    const localeCompare = String.prototype.localeCompare;
    const spy = vi.spyOn(String.prototype, 'localeCompare').mockImplementation(() => {
      throw new Error('localeCompare should not be used');
    });

    try {
      expect(
        stableAlbumOrder([album('2', 'Bravo'), album('1', 'Alpha')]).map((value) => value.id),
      ).toEqual(['1', '2']);
    } finally {
      spy.mockRestore();
      String.prototype.localeCompare = localeCompare;
    }
  });
});
