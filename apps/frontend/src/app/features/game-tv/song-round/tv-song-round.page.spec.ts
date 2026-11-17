import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GameSession } from '../../../core/session/game-session.service';
import { DelayApiService } from '../../../domain/game/data-access/delay-api.service';
import { InterruptApiService } from '../../../domain/game/data-access/interrupt-api.service';
import { ScheduleApiService } from '../../../domain/game/data-access/schedule-api.service';
import { GameServerMessage } from '../../../domain/game/messages/game-server-message.types';
import { AudioPlayerService } from '../../../shared/ui/audio-player/audio-player.service';
import { TvSongRoundPage } from './tv-song-round.page';

const team = { teamId: 'sun', name: 'Team Sun', score: 30, image: '', scheduleId: 'one' };
const otherTeam = { teamId: 'moon', name: 'Team Moon', score: 0, image: '', scheduleId: null };
const welcome = {
  type: 'welcome',
  stage: 'songs',
  scheduleId: 'one',
  songId: 'song-one',
  question: 'Prepoznaj ovu pjesmu!',
  answer: 'First song',
  answerDuration: 5,
  scores: [team, otherTeam],
} satisfies GameServerMessage;

describe('TvSongRoundPage playback', () => {
  let fixture: ComponentFixture<TvSongRoundPage>;
  let messages$: Subject<GameServerMessage>;
  let currentTime: number;
  const playing$ = new Subject<void>();
  const acknowledge = vi.fn(() => of(undefined));
  const register = vi.fn((scheduleId: string, clientAttemptAt: number) =>
    of({ delayId: `attempt-${scheduleId}`, clientAttemptAt }),
  );
  const audio = {
    playing$,
    ended$: new Subject<void>(),
    getCurrentTime: () => currentTime,
    load: vi.fn(() => {
      currentTime = 0;
    }),
    pause: vi.fn(),
    play: vi.fn(async () => playing$.next()),
    seek: vi.fn((seconds: number) => {
      currentTime = seconds;
    }),
  };

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    currentTime = 0;
    messages$ = new Subject<GameServerMessage>();
    await TestBed.configureTestingModule({
      imports: [TvSongRoundPage],
      providers: [
        { provide: GameSession, useValue: { code: 'AKKU', messages$ } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: AudioPlayerService, useValue: audio },
        { provide: DelayApiService, useValue: { register, acknowledge } },
        { provide: InterruptApiService, useValue: {} },
        { provide: ScheduleApiService, useValue: {} },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TvSongRoundPage);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  function element(testId: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testId}"]`);
  }

  function receive(message: GameServerMessage): void {
    messages$.next(message);
    fixture.detectChanges();
  }

  async function advance(milliseconds: number): Promise<void> {
    await vi.advanceTimersByTimeAsync(milliseconds);
    fixture.detectChanges();
  }

  it('preserves both score columns and starts the next snippet from a song_next frame', async () => {
    receive({ ...welcome, bravo: team.teamId, revealed: true });
    expect(audio.load).toHaveBeenLastCalledWith(expect.stringContaining('/answers/song-one'));

    receive({
      type: 'song_next',
      scheduleId: 'two',
      songId: 'song-two',
      question: 'Name the artist',
      answer: 'Second song',
      remaining: 20,
      answerDuration: 5,
    } as GameServerMessage);
    await advance(50);

    expect(register).toHaveBeenCalledWith('two', expect.any(Number));
    expect(element('scoreboard-team-score-sun')?.textContent?.trim()).toBe('30');
    expect(element('scoreboard-team-score-moon')?.textContent?.trim()).toBe('0');
    expect(element('tv-song-question')?.textContent?.trim()).toBe('Name the artist');
    expect(element('tv-snippet-audio')).not.toBeNull();
    expect(audio.load).toHaveBeenLastCalledWith(expect.stringContaining('/snippets/song-two'));
    expect(audio.play).toHaveBeenCalledTimes(2);
    expect(acknowledge).not.toHaveBeenCalled();

    currentTime = 0.1;
    await advance(50);
    expect(acknowledge).toHaveBeenCalledWith('attempt-two', register.mock.calls[0]?.[1]);
  });

  it('restores the paused TV clock and animates the score loss when the same song resumes', async () => {
    receive({
      ...welcome,
      seek: 6,
      remaining: 14,
      answeringTeam: { id: team.teamId, name: team.name, image: team.image },
      interruptId: 'interrupt-one',
    });
    await advance(100);
    expect(element('answering-team-name')?.textContent?.trim()).toBe(team.name);
    expect(element('tv-snippet-audio')).toBeNull();
    expect(register).not.toHaveBeenCalled();
    expect(element('tv-song-clock')?.textContent?.trim()).toBe('14');
    const ring = (fixture.nativeElement as HTMLElement).querySelector(
      '.song-round__clock-progress',
    );
    expect(Number(ring?.getAttribute('stroke-dashoffset'))).toBeCloseTo(30);

    receive({ type: 'answer', scheduleId: 'one', teamId: team.teamId, correct: false });
    await advance(50);
    expect(element('tv-answer-verdict')?.textContent).toContain('INCORRECT!');
    expect(element('scoreboard-team-row-sun')?.textContent).toContain('-10');
    expect(element('scoreboard-team-score-sun')?.textContent?.trim()).toBe('29');
    expect(audio.seek).toHaveBeenLastCalledWith(6);

    currentTime = 7.5;
    await advance(2500);
    expect(element('tv-song-clock')?.textContent?.trim()).toBe('13');
    expect(element('scoreboard-team-score-sun')?.textContent?.trim()).toBe('20');
    expect(element('scoreboard-team-row-sun')?.textContent).not.toContain('-10');
  });
});
