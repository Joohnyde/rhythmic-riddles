import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, Subscription } from 'rxjs';
import { CategoryApiService } from '../data-access/category-api.service';
import { GameServerMessage } from '../messages/game-server-message.types';
import { S1AlbumPickedMessage, S1WelcomeMessage } from '../messages/stage1.messages';
import { AlbumCardVm, CategorySimple, toAlbumCardVm } from '../models/album.model';
import { ClientSurface } from '../models/client-surface.model';
import { routeForStage } from '../models/game-stage.model';
import { LastCategory } from '../models/selected-album.model';
import { stableAlbumOrder } from '../models/stage1-album-order';
import { Team } from '../models/team.model';

interface AlbumSelectionState {
  readonly albums: readonly CategorySimple[];
  readonly pickedByTeam: Team | null;
  readonly selectedAlbum: LastCategory | null;
  readonly loaded: boolean;
  readonly inTransit: boolean;
}

interface AlbumSelectionVm {
  readonly albums: readonly AlbumCardVm[];
  readonly pickedByTeam: Team | null;
  readonly selectedAlbum: LastCategory | null;
  readonly loaded: boolean;
  readonly inTransit: boolean;
}

function createInitialAlbumSelectionState(): AlbumSelectionState {
  return {
    albums: [],
    pickedByTeam: null,
    selectedAlbum: null,
    loaded: false,
    inTransit: false,
  };
}

function syncSelectedAlbumMetadata(
  albums: readonly CategorySimple[],
  selectedAlbum: LastCategory | null,
): readonly CategorySimple[] {
  if (!selectedAlbum) {
    return albums;
  }

  return albums.map((album) =>
    album.id === selectedAlbum.categoryId
      ? {
          ...album,
          pickedByTeam: selectedAlbum.pickedByTeam?.image ?? null,
          ordinalNumber: selectedAlbum.ordinalNumber,
        }
      : album,
  );
}

@Injectable({ providedIn: 'root' })
export class AlbumSelectionStore {
  private readonly router = inject(Router);
  private readonly categoryApi = inject(CategoryApiService);
  private readonly state = signal<AlbumSelectionState>(createInitialAlbumSelectionState());
  private sub?: Subscription;
  private connectionGeneration = 0;

  readonly vm = computed<AlbumSelectionVm>(() => {
    const state = this.state();
    return {
      albums: stableAlbumOrder(state.albums).map(toAlbumCardVm),
      pickedByTeam: state.pickedByTeam,
      selectedAlbum: state.selectedAlbum,
      loaded: state.loaded,
      inTransit: state.inTransit,
    };
  });

  connect(messages$: Observable<GameServerMessage>, surface: ClientSurface): void {
    this.sub?.unsubscribe();
    this.connectionGeneration += 1;
    this.state.set(createInitialAlbumSelectionState());
    this.sub = messages$.subscribe((message) => this.handleMessage(message, surface));
  }

  disconnect(): void {
    this.sub?.unsubscribe();
    this.sub = undefined;
    this.connectionGeneration += 1;
    this.state.set(createInitialAlbumSelectionState());
  }

  private patchState(patch: Partial<AlbumSelectionState>): void {
    this.state.update((state) => ({ ...state, ...patch }));
  }

  private handleMessage(message: GameServerMessage, surface: ClientSurface): void {
    if (message.type === 'welcome') {
      if (message.stage !== 'albums') {
        // Navigation can render asynchronously; clear Stage 1 immediately so stale album state can
        // never remain visible while the router moves this surface to the authoritative stage.
        this.state.set(createInitialAlbumSelectionState());
        this.disconnect();
        void this.router
          .navigate(routeForStage(surface, message.stage))
          .catch((error: unknown) =>
            console.error('Stage 1 wrong-stage navigation failed.', error),
          );
        return;
      }

      this.handleWelcome(message as S1WelcomeMessage);
      return;
    }

    if (message.type === 'album_picked') {
      this.applyPickedAlbum(message as S1AlbumPickedMessage);
    }
  }

  private handleWelcome(message: S1WelcomeMessage): void {
    const selectedAlbum = message.selected ?? null;
    this.patchState({
      albums: syncSelectedAlbumMetadata(message.albums, selectedAlbum),
      selectedAlbum,
      pickedByTeam: selectedAlbum?.pickedByTeam ?? message.team ?? null,
      loaded: true,
    });
  }

  private applyPickedAlbum(message: S1AlbumPickedMessage): void {
    const current = this.state();
    const selectedAlbum = message.selected;
    if (
      !current.loaded ||
      !current.albums.some((album) => album.id === selectedAlbum.categoryId) ||
      (current.selectedAlbum !== null &&
        current.selectedAlbum.categoryId !== selectedAlbum.categoryId)
    ) {
      // A live pick is meaningful only inside the currently hydrated Stage 1 collection. Welcome is
      // authoritative for recovery, so an out-of-order/foreign frame or a conflicting second pick
      // while another selection is awaiting start must not manufacture/replace UI state.
      return;
    }

    this.patchState({
      albums: syncSelectedAlbumMetadata(current.albums, selectedAlbum),
      selectedAlbum,
      pickedByTeam: selectedAlbum.pickedByTeam,
    });
  }

  async pickAlbum(categoryId: string): Promise<void> {
    const current = this.state();
    const target = current.albums.find((album) => album.id === categoryId);
    if (
      !current.loaded ||
      current.inTransit ||
      current.selectedAlbum !== null ||
      !target ||
      target.ordinalNumber !== null
    ) {
      return;
    }

    const generation = this.connectionGeneration;
    const teamId = current.pickedByTeam?.id ?? null;
    this.patchState({ inTransit: true });
    try {
      const selectedAlbum = await firstValueFrom(this.categoryApi.pickAlbum(categoryId, teamId));
      if (generation !== this.connectionGeneration) {
        return;
      }
      this.patchState({
        albums: syncSelectedAlbumMetadata(this.state().albums, selectedAlbum),
        selectedAlbum,
        pickedByTeam: selectedAlbum.pickedByTeam,
      });
    } catch (error) {
      // A failure from an operation owned by an older connection is stale information. It must not
      // surface into a new page lifecycle; current-generation failures remain explicit to callers.
      if (generation !== this.connectionGeneration) {
        return;
      }
      throw error;
    } finally {
      if (generation === this.connectionGeneration) {
        this.patchState({ inTransit: false });
      }
    }
  }

  async start(): Promise<void> {
    const current = this.state();
    const selected = current.selectedAlbum;
    const selectedExists = selected
      ? current.albums.some((album) => album.id === selected.categoryId)
      : false;
    if (!current.loaded || current.inTransit || !selected || selected.started || !selectedExists) {
      return;
    }

    const generation = this.connectionGeneration;
    this.patchState({ inTransit: true });
    try {
      await firstValueFrom(this.categoryApi.start(selected.categoryId));
      if (generation !== this.connectionGeneration) {
        return;
      }
      await this.router.navigate(['admin', 'songs']);
    } catch (error) {
      if (generation !== this.connectionGeneration) {
        return;
      }
      throw error;
    } finally {
      if (generation === this.connectionGeneration) {
        this.patchState({ inTransit: false });
      }
    }
  }
}
