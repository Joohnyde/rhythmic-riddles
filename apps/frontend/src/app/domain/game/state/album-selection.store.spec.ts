import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Observable, Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CategoryApiService } from '../data-access/category-api.service';
import { GameServerMessage } from '../messages/game-server-message.types';
import { CategorySimple } from '../models/album.model';
import { LastCategory } from '../models/selected-album.model';
import { Team } from '../models/team.model';
import { AlbumSelectionStore } from './album-selection.store';

function albumImageId(seed: string): string {
  const checksum = [...seed].reduce((value, char) => (value * 31 + char.charCodeAt(0)) >>> 0, 0);
  return `00000000-0000-4000-8000-${checksum.toString(16).padStart(12, '0')}`;
}

function album(id: string, name = id, overrides: Partial<CategorySimple> = {}): CategorySimple {
  return {
    id,
    name,
    image: albumImageId(id),
    pickedByTeam: null,
    ordinalNumber: null,
    ...overrides,
  };
}

function selectedAlbum(
  categoryId: string,
  picker: Team,
  overrides: Partial<LastCategory> = {},
): LastCategory {
  return {
    categoryId,
    chosenCategoryPreview: {
      title: overrides.chosenCategoryPreview?.title ?? categoryId,
      image: overrides.chosenCategoryPreview?.image ?? albumImageId(categoryId),
    },
    pickedByTeam: picker,
    started: false,
    ordinalNumber: 1,
    ...overrides,
  };
}

describe('AlbumSelectionStore Stage 1 state machine', () => {
  let store: AlbumSelectionStore;
  let messages$: Subject<GameServerMessage>;
  let navigate: ReturnType<typeof vi.fn>;
  let categoryApi: {
    pickAlbum: ReturnType<typeof vi.fn>;
    start: ReturnType<typeof vi.fn>;
  };

  const picker: Team = {
    id: '3a67733a-3602-4e60-8e62-6af1c54acc3c',
    name: 'Team Picker',
    image: 'team-picker.png',
  };

  beforeEach(() => {
    navigate = vi.fn().mockResolvedValue(true);
    categoryApi = {
      pickAlbum: vi.fn(),
      start: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        AlbumSelectionStore,
        { provide: CategoryApiService, useValue: categoryApi },
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

  it('starts from an empty Stage 1 state', () => {
    expect(store.vm()).toEqual({
      albums: [],
      pickedByTeam: null,
      selectedAlbum: null,
      loaded: false,
      inTransit: false,
    });
  });

  it('clears root Stage 1 state on disconnect so a fresh page cannot render stale albums', () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);

    store.disconnect();

    expect(store.vm()).toEqual({
      albums: [],
      pickedByTeam: null,
      selectedAlbum: null,
      loaded: false,
      inTransit: false,
    });
  });

  it('starts a new connection from a clean Stage 1 state and unsubscribes the previous stream', () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-b', 'Bravo')],
      selected: selectedAlbum('album-b', picker),
    } as GameServerMessage);

    const staleStream = messages$;
    const nextMessages$ = new Subject<GameServerMessage>();
    store.connect(nextMessages$, 'admin');

    expect(store.vm()).toEqual({
      albums: [],
      pickedByTeam: null,
      selectedAlbum: null,
      loaded: false,
      inTransit: false,
    });

    staleStream.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-z', 'Zulu')],
      team: picker,
    } as GameServerMessage);

    expect(store.vm().albums).toEqual([]);
  });

  it('hydrates picker recovery from a valid Stage 1 welcome', () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-b', 'Bravo'), album('album-a', 'Alpha')],
      team: picker,
    } as GameServerMessage);

    expect(store.vm().albums.map((value) => value.id)).toEqual(['album-a', 'album-b']);
    expect(store.vm().pickedByTeam).toEqual(picker);
    expect(store.vm().selectedAlbum).toBeNull();
  });

  it('hydrates selected recovery and merges selected metadata into the canonical album order', () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-b', 'Bravo'), album('album-a', 'Alpha')],
      selected: selectedAlbum('album-b', picker, {
        chosenCategoryPreview: { title: 'Bravo', image: albumImageId('album-b') },
        ordinalNumber: 2,
      }),
    } as GameServerMessage);

    expect(store.vm().albums.map((value) => value.id)).toEqual(['album-a', 'album-b']);
    expect(store.vm().albums[1].pickedByTeam).toBe(picker.image);
    expect(store.vm().albums[1].ordinalNumber).toBe(2);
    expect(store.vm().pickedByTeam).toEqual(picker);
    expect(store.vm().selectedAlbum?.categoryId).toBe('album-b');
  });

  it('normalizes the same albums into the same stable order even when welcome input order changes', () => {
    const firstOrder = [
      album('album-c', 'Charlie'),
      album('album-a', 'Alpha'),
      album('album-b', 'alpha'),
    ];
    const secondOrder = [
      album('album-b', 'alpha'),
      album('album-c', 'Charlie'),
      album('album-a', 'Alpha'),
    ];

    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: firstOrder,
      team: picker,
    } as GameServerMessage);
    const firstVmOrder = store.vm().albums.map((value) => value.id);

    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: secondOrder,
      team: picker,
    } as GameServerMessage);

    expect(firstVmOrder).toEqual(['album-a', 'album-b', 'album-c']);
    expect(store.vm().albums.map((value) => value.id)).toEqual(firstVmOrder);
  });

  it('ignores album_picked before a Stage 1 welcome has hydrated the collection', () => {
    messages$.next({
      type: 'album_picked',
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);

    expect(store.vm()).toEqual({
      albums: [],
      pickedByTeam: null,
      selectedAlbum: null,
      loaded: false,
      inTransit: false,
    });
  });

  it('ignores album_picked for an album outside the currently hydrated Stage 1 collection', () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      team: picker,
    } as GameServerMessage);

    messages$.next({
      type: 'album_picked',
      selected: selectedAlbum('album-foreign', picker),
    } as GameServerMessage);

    expect(store.vm().albums.map((value) => value.id)).toEqual(['album-a']);
    expect(store.vm().selectedAlbum).toBeNull();
    expect(store.vm().pickedByTeam).toEqual(picker);
  });

  it('ignores a conflicting second album_picked while the current selection is waiting to start', () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha'), album('album-b', 'Bravo')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);

    messages$.next({
      type: 'album_picked',
      selected: selectedAlbum('album-b', picker, { ordinalNumber: 2 }),
    } as GameServerMessage);

    expect(store.vm().selectedAlbum?.categoryId).toBe('album-a');
    expect(store.vm().albums.find((value) => value.id === 'album-b')?.ordinalNumber).toBeNull();
  });

  it('preserves canonical ordering when album_picked arrives', () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-d', 'Delta'), album('album-a', 'Alpha'), album('album-c', 'Charlie')],
      team: picker,
    } as GameServerMessage);

    messages$.next({
      type: 'album_picked',
      selected: selectedAlbum('album-c', picker, { ordinalNumber: 4 }),
    } as GameServerMessage);

    expect(store.vm().albums.map((value) => value.id)).toEqual(['album-a', 'album-c', 'album-d']);
    expect(store.vm().albums[1].pickedByTeam).toBe(picker.image);
    expect(store.vm().albums[1].ordinalNumber).toBe(4);
    expect(store.vm().pickedByTeam).toEqual(picker);
  });

  it('clears hydrated Stage 1 state immediately when welcome reports another stage', () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);
    expect(store.vm().loaded).toBe(true);

    messages$.next({ type: 'welcome', stage: 'songs' } as GameServerMessage);

    expect(store.vm()).toEqual({
      albums: [],
      pickedByTeam: null,
      selectedAlbum: null,
      loaded: false,
      inTransit: false,
    });
    expect(navigate).toHaveBeenCalledOnce();
  });

  it('does not issue a pick before Stage 1 is loaded', async () => {
    await store.pickAlbum('album-a');

    expect(categoryApi.pickAlbum).not.toHaveBeenCalled();
  });

  it('does not issue a pick for an album that is not in the current Stage 1 state', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      team: picker,
    } as GameServerMessage);

    await store.pickAlbum('album-missing');

    expect(categoryApi.pickAlbum).not.toHaveBeenCalled();
  });

  it('does not issue a pick for an album that was already picked', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha', { ordinalNumber: 1, pickedByTeam: picker.image })],
      team: picker,
    } as GameServerMessage);

    await store.pickAlbum('album-a');

    expect(categoryApi.pickAlbum).not.toHaveBeenCalled();
  });

  it('does not issue another pick while a selected album is waiting to start', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha'), album('album-b', 'Bravo')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);

    await store.pickAlbum('album-b');

    expect(categoryApi.pickAlbum).not.toHaveBeenCalled();
  });

  it('picks an album and clears transit after success', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      team: picker,
    } as GameServerMessage);

    categoryApi.pickAlbum.mockReturnValue(
      new Observable<LastCategory>((subscriber) => {
        subscriber.next(
          selectedAlbum('album-a', picker, {
            chosenCategoryPreview: { title: 'Alpha', image: albumImageId('album-a') },
          }),
        );
        subscriber.complete();
      }),
    );

    await store.pickAlbum('album-a');

    expect(categoryApi.pickAlbum).toHaveBeenCalledWith('album-a', picker.id);
    expect(store.vm().selectedAlbum?.categoryId).toBe('album-a');
    expect(store.vm().inTransit).toBe(false);
  });

  it('ignores duplicate pick requests while a pick is already in transit', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      team: picker,
    } as GameServerMessage);

    const pending = new Subject<LastCategory>();
    categoryApi.pickAlbum.mockReturnValue(pending.asObservable());

    const first = store.pickAlbum('album-a');
    const second = store.pickAlbum('album-a');

    expect(categoryApi.pickAlbum).toHaveBeenCalledOnce();

    pending.next(selectedAlbum('album-a', picker));
    pending.complete();
    await Promise.all([first, second]);
  });

  it('restores a usable state when pickAlbum fails', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      team: picker,
    } as GameServerMessage);

    categoryApi.pickAlbum.mockReturnValue(throwError(() => new Error('pick failed')));

    await expect(store.pickAlbum('album-a')).rejects.toThrow('pick failed');
    expect(store.vm().inTransit).toBe(false);
    expect(store.vm().selectedAlbum).toBeNull();
  });

  it('ignores stale pick success that resolves after reconnect', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      team: picker,
    } as GameServerMessage);

    const pending = new Subject<LastCategory>();
    categoryApi.pickAlbum.mockReturnValue(pending.asObservable());

    const stalePick = store.pickAlbum('album-a');

    const nextMessages$ = new Subject<GameServerMessage>();
    store.connect(nextMessages$, 'admin');
    nextMessages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-b', 'Bravo')],
      team: picker,
    } as GameServerMessage);

    pending.next(selectedAlbum('album-a', picker));
    pending.complete();
    await stalePick;

    expect(store.vm().albums.map((value) => value.id)).toEqual(['album-b']);
    expect(store.vm().selectedAlbum).toBeNull();
    expect(store.vm().inTransit).toBe(false);
  });

  it('ignores stale pick failure that resolves after reconnect', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      team: picker,
    } as GameServerMessage);

    let rejectPick: ((error: unknown) => void) | undefined;
    categoryApi.pickAlbum.mockReturnValue(
      new Observable<LastCategory>((subscriber) => {
        rejectPick = (error) => subscriber.error(error);
      }),
    );

    const stalePick = store.pickAlbum('album-a').catch((error) => error);

    const nextMessages$ = new Subject<GameServerMessage>();
    store.connect(nextMessages$, 'admin');
    nextMessages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-b', 'Bravo')],
      team: picker,
    } as GameServerMessage);

    rejectPick?.(new Error('stale pick failed'));
    await stalePick;

    expect(store.vm().albums.map((value) => value.id)).toEqual(['album-b']);
    expect(store.vm().inTransit).toBe(false);
  });

  it('does not start without a selected album', async () => {
    await store.start();

    expect(categoryApi.start).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not start a selected snapshot that is already marked started', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      selected: selectedAlbum('album-a', picker, { started: true }),
    } as GameServerMessage);

    await store.start();

    expect(categoryApi.start).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not start when the selected snapshot is not a member of the current album collection', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-b', 'Bravo')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);

    await store.start();

    expect(categoryApi.start).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ignores duplicate start requests while start is in transit', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);

    const pending = new Subject<void>();
    categoryApi.start.mockReturnValue(pending.asObservable());

    const first = store.start();
    const second = store.start();

    expect(categoryApi.start).toHaveBeenCalledOnce();

    pending.next();
    pending.complete();
    await Promise.all([first, second]);
    expect(navigate).toHaveBeenCalledOnce();
  });

  it('starts the selected album and navigates once on success', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);

    categoryApi.start.mockReturnValue(
      new Observable<void>((subscriber) => {
        subscriber.next();
        subscriber.complete();
      }),
    );

    await store.start();

    expect(categoryApi.start).toHaveBeenCalledWith('album-a');
    expect(navigate).toHaveBeenCalledWith(['admin', 'songs']);
    expect(store.vm().inTransit).toBe(false);
  });

  it('propagates navigation failure without leaving Stage 1 stuck in transit', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);
    categoryApi.start.mockReturnValue(
      new Observable<void>((subscriber) => {
        subscriber.next();
        subscriber.complete();
      }),
    );
    navigate.mockRejectedValueOnce(new Error('navigation failed'));

    await expect(store.start()).rejects.toThrow('navigation failed');

    expect(categoryApi.start).toHaveBeenCalledOnce();
    expect(store.vm().inTransit).toBe(false);
  });

  it('restores a usable state when start fails', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);

    categoryApi.start.mockReturnValue(throwError(() => new Error('start failed')));

    await expect(store.start()).rejects.toThrow('start failed');
    expect(store.vm().inTransit).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ignores stale start completion after reconnect', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);

    const pending = new Subject<void>();
    categoryApi.start.mockReturnValue(pending.asObservable());

    const staleStart = store.start();

    const nextMessages$ = new Subject<GameServerMessage>();
    store.connect(nextMessages$, 'admin');
    nextMessages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-b', 'Bravo')],
      team: picker,
    } as GameServerMessage);

    pending.next();
    pending.complete();
    await staleStart;

    expect(navigate).not.toHaveBeenCalled();
    expect(store.vm().albums.map((value) => value.id)).toEqual(['album-b']);
    expect(store.vm().selectedAlbum).toBeNull();
  });

  it('ignores stale start failure after reconnect', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'albums',
      albums: [album('album-a', 'Alpha')],
      selected: selectedAlbum('album-a', picker),
    } as GameServerMessage);

    let rejectStart: ((error: unknown) => void) | undefined;
    categoryApi.start.mockReturnValue(
      new Observable<void>((subscriber) => {
        rejectStart = (error) => subscriber.error(error);
      }),
    );

    const staleStart = store.start().catch((error) => error);

    store.connect(new Subject<GameServerMessage>(), 'admin');
    rejectStart?.(new Error('stale start failed'));
    await staleStart;

    expect(navigate).not.toHaveBeenCalled();
    expect(store.vm().inTransit).toBe(false);
  });
});
