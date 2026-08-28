import { CategorySimple } from './album.model';

function canonicalAlbumName(name: string): string {
  return name.normalize('NFKD').toLowerCase();
}

function compareCanonicalText(left: string, right: string): number {
  const maxIndex = Math.min(left.length, right.length);
  for (let index = 0; index < maxIndex; index += 1) {
    const difference = left.charCodeAt(index) - right.charCodeAt(index);
    if (difference !== 0) {
      return difference;
    }
  }

  return left.length - right.length;
}

function compareAlbums(left: CategorySimple, right: CategorySimple): number {
  const byName = compareCanonicalText(
    canonicalAlbumName(left.name),
    canonicalAlbumName(right.name),
  );
  if (byName !== 0) {
    return byName;
  }

  return compareCanonicalText(left.id, right.id);
}

/**
 * Stage 1 positions are part of the UX, so the frontend normalizes backend album collections into
 * one deterministic order before rendering or animating them.
 */
export function stableAlbumOrder(albums: readonly CategorySimple[]): readonly CategorySimple[] {
  return [...albums].sort(compareAlbums);
}
