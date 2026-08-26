import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, Subscription } from 'rxjs';
import { CategoryApiService } from '../data-access/category-api.service';
import { DefaultMessage } from '../messages/default.messages';
import { GameServerMessage } from '../messages/game-server-message.types';
import { S1AlbumPickedMessage, S1WelcomeMessage } from '../messages/stage1.messages';
import { AlbumCardVm, CategorySimple, toAlbumCardVm } from '../models/album.model';
import { ClientSurface } from '../models/client-surface.model';
import { routeForStage } from '../models/game-stage.model';
import { LastCategory } from '../models/selected-album.model';
import { Team } from '../models/team.model';

interface AlbumSelectionState {
  readonly albums: readonly CategorySimple[];
  readonly pickedByTeam: Team | null;
  readonly selectedAlbum: LastCategory | null;
  readonly loaded: boolean;
  readonly inTransit: boolean;
  readonly animateSelectionFocus: boolean;
}

interface AlbumSelectionVm {
  readonly albums: readonly AlbumCardVm[];
  readonly pickedByTeam: Team | null;
  readonly selectedAlbum: LastCategory | null;
  readonly loaded: boolean;
  readonly inTransit: boolean;
  readonly showStartButton: boolean;
  readonly animateSelectionFocus: boolean;
}

function stableAlbumOrder(albums: readonly CategorySimple[]): readonly CategorySimple[] {
  return [...albums].sort((left, right) => {
    const byName = left.name.localeCompare(right.name, undefined, { sensitivity: 'base' });
    return byName !== 0 ? byName : left.id.localeCompare(right.id);
  });
}

function createInitialAlbumSelectionState(): AlbumSelectionState {
  return {
    albums: [],
    pickedByTeam: null,
    selectedAlbum: null,
    loaded: false,
    inTransit: false,
    animateSelectionFocus: false,
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

  readonly vm = computed<AlbumSelectionVm>(() => {
    const state = this.state();
    return {
      albums: stableAlbumOrder(state.albums).map(toAlbumCardVm),
      pickedByTeam: state.pickedByTeam,
      selectedAlbum: state.selectedAlbum,
      loaded: state.loaded,
      inTransit: state.inTransit,
      showStartButton: state.selectedAlbum !== null,
      animateSelectionFocus: state.animateSelectionFocus,
    };
  });

  connect(messages$: Observable<GameServerMessage>, surface: ClientSurface): void {
    this.sub?.unsubscribe();
    this.sub = messages$.subscribe((message) => this.handleMessage(message, surface));
  }

  disconnect(): void {
    this.sub?.unsubscribe();
    this.sub = undefined;
  }

  private patchState(patch: Partial<AlbumSelectionState>): void {
    this.state.update((state) => ({ ...state, ...patch }));
  }

  private handleMessage(message: DefaultMessage, surface: ClientSurface): void {
    switch (message.type) {
      case 'welcome':
        this.handleWelcome(message as S1WelcomeMessage, surface);
        break;
      case 'album_picked':
        this.applyPickedAlbum(message as S1AlbumPickedMessage);
        break;
    }
  }

  private handleWelcome(message: S1WelcomeMessage, surface: ClientSurface): void {
    if (message.stage !== 'albums') {
      this.disconnect();
      void this.router.navigate(routeForStage(surface, message.stage));
      return;
    }

    const selectedAlbum = message.selected ?? null;
    this.patchState({
      albums: syncSelectedAlbumMetadata(message.albums ?? [], selectedAlbum),
      selectedAlbum,
      pickedByTeam: selectedAlbum?.pickedByTeam ?? message.team ?? null,
      animateSelectionFocus: false,
      loaded: true,
    });
  }

  private applyPickedAlbum(message: S1AlbumPickedMessage): void {
    const selectedAlbum = message.selected ?? null;
    this.patchState({
      albums: syncSelectedAlbumMetadata(this.state().albums, selectedAlbum),
      selectedAlbum,
      pickedByTeam: selectedAlbum?.pickedByTeam ?? null,
      animateSelectionFocus: true,
      loaded: true,
    });
  }

  async pickAlbum(categoryId: string): Promise<void> {
    if (this.state().inTransit) {
      return;
    }

    const teamId = this.state().pickedByTeam?.id ?? null;
    this.patchState({ inTransit: true });
    try {
      const selectedAlbum = await firstValueFrom(this.categoryApi.pickAlbum(categoryId, teamId));
      this.patchState({
        albums: syncSelectedAlbumMetadata(this.state().albums, selectedAlbum),
        selectedAlbum,
        pickedByTeam: selectedAlbum.pickedByTeam,
        animateSelectionFocus: true,
      });
    } finally {
      this.patchState({ inTransit: false });
    }
  }

  async start(): Promise<void> {
    const selected = this.state().selectedAlbum;
    if (this.state().inTransit || !selected) {
      return;
    }

    this.patchState({ inTransit: true });
    try {
      await firstValueFrom(this.categoryApi.start(selected.categoryId));
      void this.router.navigate(['admin', 'songs']);
    } finally {
      this.patchState({ inTransit: false });
    }
  }
}
