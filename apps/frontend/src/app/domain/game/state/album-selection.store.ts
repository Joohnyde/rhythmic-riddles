import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, Subscription } from 'rxjs';
import { ClientSurface } from '../../../core/realtime/game-realtime.service';
import { CategoryApiService } from '../data-access/category-api.service';
import { DefaultMessage } from '../messages/default.messages';
import { S1AlbumPickedMessage, S1WelcomeMessage } from '../messages/stage1.messages';
import { CategorySimple } from '../models/album.model';
import { LastCategory } from '../models/selected-album.model';
import { Team } from '../models/team.model';

interface AlbumSelectionState {
  albums: CategorySimple[];
  pickedByTeam: Team | null;
  selectedAlbum: LastCategory | null;
  loaded: boolean;
  inTransit: boolean;
}
const initialState: AlbumSelectionState = {
  albums: [],
  pickedByTeam: null,
  selectedAlbum: null,
  loaded: false,
  inTransit: false,
};
@Injectable({ providedIn: 'root' })
export class AlbumSelectionStore {
  private readonly router = inject(Router);
  private readonly categoryApi = inject(CategoryApiService);
  private readonly state = signal<AlbumSelectionState>(initialState);
  private sub?: Subscription;
  readonly vm = computed(() => ({
    ...this.state(),
    showStartButton: this.state().selectedAlbum !== null,
  }));
  connect(messages$: Observable<DefaultMessage>, surface: ClientSurface): void {
    this.sub?.unsubscribe();
    this.sub = messages$.subscribe((m) => this.handleMessage(m, surface));
  }
  disconnect(): void {
    this.sub?.unsubscribe();
    this.sub = undefined;
  }
  private patch(p: Partial<AlbumSelectionState>) {
    this.state.update((s) => ({ ...s, ...p }));
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
      this.router.navigate(surface === 'admin' ? ['admin', message.stage] : [message.stage]);
      return;
    }
    if (message.albums)
      this.patch({
        albums: message.albums,
        pickedByTeam: message.team ?? null,
        selectedAlbum: null,
        loaded: true,
      });
    else
      this.patch({
        selectedAlbum: message.selected ?? null,
        pickedByTeam: message.selected?.pickedByTeam ?? null,
        loaded: true,
      });
  }
  private applyPickedAlbum(message: S1AlbumPickedMessage): void {
    this.patch({
      selectedAlbum: message.selected ?? null,
      pickedByTeam: message.selected?.pickedByTeam ?? null,
      loaded: true,
    });
  }
  async pickAlbum(categoryId: string): Promise<void> {
    if (this.state().inTransit) return;
    const teamId = this.state().pickedByTeam?.id ?? null;
    this.patch({ inTransit: true });
    try {
      const selectedAlbum = await firstValueFrom(this.categoryApi.pickAlbum(categoryId, teamId));
      this.patch({ selectedAlbum, pickedByTeam: selectedAlbum.pickedByTeam });
    } finally {
      this.patch({ inTransit: false });
    }
  }
  async start(): Promise<void> {
    const selected = this.state().selectedAlbum;
    if (this.state().inTransit || !selected) return;
    this.patch({ inTransit: true });
    try {
      await firstValueFrom(this.categoryApi.start(selected.categoryId));
      this.router.navigate(['admin', 'songs']);
    } finally {
      this.patch({ inTransit: false });
    }
  }
}
