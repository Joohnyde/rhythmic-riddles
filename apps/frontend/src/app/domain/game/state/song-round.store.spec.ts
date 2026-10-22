import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DelayApiService } from '../data-access/delay-api.service';
import { InterruptApiService } from '../data-access/interrupt-api.service';
import { ScheduleApiService } from '../data-access/schedule-api.service';
import { GameServerMessage } from '../messages/game-server-message.types';
import { SongScenario } from './song-scenario';
import { SongRoundStore } from './song-round.store';

/** The TV registers before playing; only backend 'play' releases the Admin timer. */
describe('SongRoundStore playback synchronization', () => {
  let store: SongRoundStore;
  let messages$: Subject<GameServerMessage>;
  let register: ReturnType<typeof vi.fn>;
  let acknowledge: ReturnType<typeof vi.fn>;

  const nextSong = (id = 'schedule-one') => ({
    type: 'song_next', songId: 'song-one', question: 'Question', answer: 'Answer',
    scheduleId: id, answerDuration: 5, remaining: 20, scores: [],
  } as GameServerMessage);

  beforeEach(() => {
    register = vi.fn().mockReturnValue(of('attempt-one'));
    acknowledge = vi.fn().mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      providers: [
        SongRoundStore,
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: InterruptApiService, useValue: {} },
        { provide: ScheduleApiService, useValue: {} },
        { provide: DelayApiService, useValue: { register, acknowledge } },
      ],
    });
    store = TestBed.inject(SongRoundStore);
    messages$ = new Subject<GameServerMessage>();
    store.connect(messages$, 'tv');
    messages$.next(nextSong());
  });

  afterEach(() => {
    store.disconnect();
    TestBed.resetTestingModule();
  });

  it('coalesces overlapping registrations before playback', async () => {
    const pending = new Subject<string>();
    register.mockReturnValue(pending);
    const first = store.requestPlayback('schedule-one');
    const second = store.requestPlayback('schedule-one');
    await Promise.resolve();
    expect(register).toHaveBeenCalledTimes(1);
    pending.next('attempt-one');
    pending.complete();
    await Promise.all([first, second]);
    expect(store.vm().playbackDelayId).toBe('attempt-one');
  });

  it('does not send ACK until registration finishes', async () => {
    const pending = new Subject<string>();
    register.mockReturnValue(pending);
    const registering = store.requestPlayback('schedule-one');
    const started = store.acknowledgePlayback();
    await Promise.resolve();
    expect(acknowledge).not.toHaveBeenCalled();
    pending.next('attempt-one');
    pending.complete();
    await Promise.all([registering, started]);
    expect(acknowledge).toHaveBeenCalledTimes(1);
    expect(acknowledge).toHaveBeenCalledWith('attempt-one');
  });

  it('ignores a late registration after disconnect and accepts the reconnect', async () => {
    const pending = new Subject<string>();
    register.mockReturnValueOnce(pending).mockReturnValueOnce(of('attempt-reconnected'));
    const first = store.requestPlayback('schedule-one');
    await Promise.resolve();
    store.disconnect();
    const reconnect = new Subject<GameServerMessage>();
    store.connect(reconnect, 'tv');
    reconnect.next(nextSong());
    await store.requestPlayback('schedule-one');
    pending.next('old-attempt');
    pending.complete();
    await first;
    expect(store.vm().playbackDelayId).toBe('attempt-reconnected');
  });

  it('re-registers after multiple disconnects without prematurely acknowledging', async () => {
    for (let reconnects = 0; reconnects < 4; reconnects += 1) {
      store.disconnect();
      const reconnect = new Subject<GameServerMessage>();
      store.connect(reconnect, 'tv');
      reconnect.next(nextSong());
      await store.requestPlayback('schedule-one');
    }
    expect(register).toHaveBeenCalledTimes(4);
    expect(acknowledge).not.toHaveBeenCalled();
    await store.acknowledgePlayback();
    expect(acknowledge).toHaveBeenCalledTimes(1);
    expect(acknowledge).toHaveBeenCalledWith('attempt-one');
  });

  it('a pending ACK from the previous TV connection does not block registration', async () => {
    await store.requestPlayback('schedule-one');
    const pendingAck = new Subject<void>();
    acknowledge.mockReturnValueOnce(pendingAck);
    const oldAck = store.acknowledgePlayback();
    await Promise.resolve();
    store.disconnect();
    const reconnect = new Subject<GameServerMessage>();
    store.connect(reconnect, 'tv');
    reconnect.next(nextSong());
    await store.requestPlayback('schedule-one');
    expect(register).toHaveBeenCalledTimes(2);
    pendingAck.next();
    pendingAck.complete();
    await oldAck;
  });

  it('recovers after a presence-rejected ACK on the next TV mount', async () => {
    await store.requestPlayback('schedule-one');
    acknowledge.mockImplementationOnce(() => { throw new Error('Apps disconnected'); });
    await store.acknowledgePlayback();
    expect(store.vm().playbackError).toContain('acknowledge');
    store.disconnect();
    const reconnect = new Subject<GameServerMessage>();
    store.connect(reconnect, 'tv');
    reconnect.next(nextSong());
    await store.requestPlayback('schedule-one');
    await store.acknowledgePlayback();
    expect(store.vm().playbackError).toBeNull();
    expect(acknowledge).toHaveBeenCalledTimes(2);
  });

  it('holds Admin at every new playback boundary until the correct schedule plays', () => {
    store.disconnect();
    store.connect(messages$, 'admin');
    messages$.next(nextSong());
    expect(store.vm().playbackWaiting).toBe(true);
    store.onTrackFinished();
    expect(store.vm().scenario).toBe(SongScenario.Playing);
    messages$.next({ type: 'play', scheduleId: 'other-schedule' } as GameServerMessage);
    expect(store.vm().playbackWaiting).toBe(true);
    messages$.next({ type: 'play', scheduleId: 'schedule-one' } as GameServerMessage);
    expect(store.vm().playbackWaiting).toBe(false);
    messages$.next({ type: 'song_repeat', remaining: 20 } as GameServerMessage);
    expect(store.vm().playbackWaiting).toBe(true);
    messages$.next({ type: 'play', scheduleId: 'schedule-one' } as GameServerMessage);
    expect(store.vm().playbackWaiting).toBe(false);
    store.onTrackFinished();
    expect(store.vm().scenario).toBe(SongScenario.FinishedUnrevealed);
  });

  it('does not require a TV completion handshake', async () => {
    await store.requestPlayback('schedule-one');
    await store.acknowledgePlayback();
    store.onTrackFinished();
    expect(store.vm().scenario).toBe(SongScenario.FinishedUnrevealed);
    expect(register).toHaveBeenCalledTimes(1);
    expect(acknowledge).toHaveBeenCalledTimes(1);
  });
});
