import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CategoryApiService } from '../data-access/category-api.service';
import { GameServerMessage } from '../messages/game-server-message.types';
import { CategorySimple } from '../models/album.model';
import { LastCategory } from '../models/selected-album.model';
import { Team } from '../models/team.model';
import { AlbumSelectionStore } from './album-selection.store';

describe('AlbumSelectionStore Stage 1 recovery', () => {
  let store: AlbumSelectionStore;
  let messages$: Subject<GameServerMessage>;
  const navigate = vi.fn();

  const picker: Team = {
    id: '3a67733a-3602-4e60-8e62-6af1c54acc3c',
    name: 'Team Picker',
    image: 'team-picker.png',
  };
  const album: CategorySimple = {
    id: '9743791e-7f66-4d46-b256-39b48f14bb21',
    name: 'YU Rock',
    image: 'b3e6e810-1dbc-41b0-bbdd-cb30b55efc07',
    pickedByTeam: null,
    ordinalNumber: null,
  };

  beforeEach(() => {
    navigate.mockReset().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        AlbumSelectionStore,
        { provide: CategoryApiService, useValue: {} },
        { provide: Router, useValue: { navigate } },
      ],
    });

    store = TestBed.inject(AlbumSelectionStore);
    messages$ = new Subject<GameServerMessage>();
    store.connect(messages$, 'admin');
  });

  afterEach(() => {
    store.disconnect();
    TestBed.resetTestingModule();
  });

  it('hydrates albums and selected state from the combined recovery snapshot', () => {
    const selected: LastCategory = {
      categoryId: album.id,
      chosenCategoryPreview: { title: album.name, image: album.image },
      pickedByTeam: picker,
      started: false,
      ordinalNumber: 1,
    };

    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album],
      selected,
    } as GameServerMessage);

    expect(store.vm().albums).toHaveLength(1);
    expect(store.vm().albums[0].image).toBe(album.image);
    expect(store.vm().selectedAlbum).toEqual(selected);
    expect(store.vm().pickedByTeam).toEqual(picker);
    expect(store.vm().showStartButton).toBe(true);
  });

  it('hydrates picker state from the same always-present album list contract', () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album],
      team: picker,
    } as GameServerMessage);

    expect(store.vm().albums).toHaveLength(1);
    expect(store.vm().selectedAlbum).toBeNull();
    expect(store.vm().pickedByTeam).toEqual(picker);
    expect(store.vm().showStartButton).toBe(false);
  });
});
