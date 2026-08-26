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
    expect(store.vm().albums[0].pickedByTeam).toBe(picker.image);
    expect(store.vm().albums[0].ordinalNumber).toBe(1);
    expect(store.vm().selectedAlbum).toEqual(selected);
    expect(store.vm().pickedByTeam).toEqual(picker);
    expect(store.vm().showStartButton).toBe(true);
  });

  it('updates the selected album card metadata when album_picked arrives', () => {
    const selected: LastCategory = {
      categoryId: album.id,
      chosenCategoryPreview: { title: album.name, image: album.image },
      pickedByTeam: picker,
      started: false,
      ordinalNumber: 2,
    };

    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album],
      team: picker,
    } as GameServerMessage);
    messages$.next({
      type: 'album_picked',
      stage: 'albums',
      selected,
    } as GameServerMessage);

    expect(store.vm().albums[0].pickedByTeam).toBe(picker.image);
    expect(store.vm().albums[0].ordinalNumber).toBe(2);
    expect(store.vm().animateSelectionFocus).toBe(true);
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

  it('keeps a deterministic album order when recovery already contains a middle selection', () => {
    const orderedAlbums: CategorySimple[] = [
      { ...album, id: 'album-a', name: 'A' },
      { ...album, id: 'album-b', name: 'B' },
      { ...album, id: 'album-c', name: 'C' },
      { ...album, id: 'album-d', name: 'D' },
      { ...album, id: 'album-e', name: 'E' },
    ];
    const selected: LastCategory = {
      categoryId: 'album-c',
      chosenCategoryPreview: { title: 'C', image: album.image },
      pickedByTeam: picker,
      started: false,
      ordinalNumber: 1,
    };

    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: orderedAlbums,
      selected,
    } as GameServerMessage);

    expect(store.vm().albums.map((value) => value.id)).toEqual([
      'album-a',
      'album-b',
      'album-c',
      'album-d',
      'album-e',
    ]);
    expect(store.vm().albums[2].pickedByTeam).toBe(picker.image);
  });

  it('keeps album positions stable when the backend sends a different order after refresh', () => {
    const shuffledAlbums: CategorySimple[] = [
      { ...album, id: 'album-c', name: 'C' },
      { ...album, id: 'album-a', name: 'A' },
      { ...album, id: 'album-b', name: 'B' },
    ];

    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: shuffledAlbums,
      team: picker,
    } as GameServerMessage);

    expect(store.vm().albums.map((value) => value.id)).toEqual(['album-a', 'album-b', 'album-c']);
  });

  it('updates live picked metadata without moving the selected album', () => {
    const backendOrderedAlbums: CategorySimple[] = [
      { ...album, id: 'album-d', name: 'D' },
      { ...album, id: 'album-a', name: 'A' },
      { ...album, id: 'album-c', name: 'C' },
      { ...album, id: 'album-b', name: 'B' },
    ];
    const selected: LastCategory = {
      categoryId: 'album-c',
      chosenCategoryPreview: { title: 'C', image: album.image },
      pickedByTeam: picker,
      started: false,
      ordinalNumber: 1,
    };

    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: backendOrderedAlbums,
      team: picker,
    } as GameServerMessage);
    messages$.next({
      type: 'album_picked',
      stage: 'albums',
      selected,
    } as GameServerMessage);

    expect(store.vm().albums.map((value) => value.id)).toEqual([
      'album-a',
      'album-b',
      'album-c',
      'album-d',
    ]);
    expect(store.vm().albums[2].pickedByTeam).toBe(picker.image);
    expect(store.vm().albums[2].ordinalNumber).toBe(1);
    expect(store.vm().animateSelectionFocus).toBe(true);
  });
});
