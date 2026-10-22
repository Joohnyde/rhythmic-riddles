import { Injectable, signal } from '@angular/core';

import { getStage1AlbumImageUrl } from '../stage1-album-selection/stage1-album-image-url';

export interface SelectedAlbumArtwork {
  readonly id: string;
  readonly name: string;
  readonly image: string;
  readonly pickedByTeamImage?: string | null;
  readonly pickedByTeamName?: string | null;
}

const STORAGE_KEY = 'rr-tv-selected-album-artwork';

function restoreArtwork(): SelectedAlbumArtwork | null {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null');
    if (!value || typeof value !== 'object') return null;
    const artwork = value as Record<string, unknown>;
    if (
      typeof artwork['id'] !== 'string' ||
      typeof artwork['name'] !== 'string' ||
      typeof artwork['image'] !== 'string'
    ) {
      return null;
    }
    return {
      id: artwork['id'],
      name: artwork['name'],
      image: artwork['image'],
      pickedByTeamImage:
        typeof artwork['pickedByTeamImage'] === 'string' ? artwork['pickedByTeamImage'] : null,
      pickedByTeamName:
        typeof artwork['pickedByTeamName'] === 'string' ? artwork['pickedByTeamName'] : null,
    };
  } catch {
    return null;
  }
}

/**
 * TV-only handoff between Stage 1 and Stage 2. AlbumSelectionStore deliberately
 * clears its state on disconnect, and the Stage 2 WebSocket snapshot has no album.
 * sessionStorage is scoped to this TV tab, so a refresh keeps the artwork.
 */
@Injectable({ providedIn: 'root' })
export class SelectedAlbumArtworkService {
  readonly album = signal<SelectedAlbumArtwork | null>(restoreArtwork());

  capture(artwork: SelectedAlbumArtwork): void {
    this.album.set(artwork);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(artwork));
    } catch {
      // The in-memory handoff still works when storage is blocked.
    }
  }

  clear(): void {
    this.album.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // No-op for disabled storage.
    }
  }

  imageUrl(): string | null {
    const artwork = this.album();
    return artwork ? getStage1AlbumImageUrl(artwork.image) : null;
  }
}
