import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GameSession } from '../../../core/session/game-session.service';
import { DelayApiService } from '../../../domain/game/data-access/delay-api.service';
import { InterruptApiService } from '../../../domain/game/data-access/interrupt-api.service';
import { ScheduleApiService } from '../../../domain/game/data-access/schedule-api.service';
import { GameServerMessage } from '../../../domain/game/messages/game-server-message.types';
import { AdminSongRoundPage } from './admin-song-round.page';

const scheduleId = 'schedule-one';
const team = {
  teamId: 'team-one',
  name: 'Team Sun',
  image: '/icons/sun_FF8800.png',
  score: 10,
  scheduleId: null,
};

describe('AdminSongRoundPage playback timer', () => {
  let fixture: ComponentFixture<AdminSongRoundPage>;
  let messages$: Subject<GameServerMessage>;
  let elapsedMs: number;
  const answer = vi.fn(() => of(undefined));
  const next = vi.fn(() => of(undefined));

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    elapsedMs = 0;
    vi.spyOn(performance, 'now').mockImplementation(() => elapsedMs);
    messages$ = new Subject<GameServerMessage>();
    await TestBed.configureTestingModule({
      imports: [AdminSongRoundPage],
      providers: [
        { provide: GameSession, useValue: { code: 'AKKU', messages$ } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: DelayApiService, useValue: {} },
        { provide: InterruptApiService, useValue: { answer } },
        { provide: ScheduleApiService, useValue: { next } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminSongRoundPage);
    fixture.detectChanges();
    receive({
      type: 'welcome',
      stage: 'songs',
      scheduleId,
      songId: 'song-one',
      question: 'Prepoznaj ovu pjesmu!',
      answer: 'Example Artist — Example Song',
      seek: 0,
      remaining: 20,
      answerDuration: 5,
      scores: [team],
    });
  });

  afterEach(() => {
    fixture.destroy();
    vi.restoreAllMocks();
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

  function advance(milliseconds: number): void {
    elapsedMs += milliseconds;
    vi.advanceTimersByTime(milliseconds);
    fixture.detectChanges();
  }

  it('starts the countdown on the current TV acknowledgement and finishes locally', () => {
    expect(element('admin-waiting-for-tv')?.textContent).toContain('Waiting for the TV');
    receive({ type: 'play', scheduleId: 'previous-schedule' });
    expect(element('admin-song-seconds-remaining')).toBeNull();

    receive({ type: 'play', scheduleId });
    expect(element('admin-waiting-for-tv')).toBeNull();
    expect(element('admin-song-question')?.textContent).toContain('GUESS THE SONG');
    expect(element('admin-playing-song-name')?.textContent).toContain(
      'Example Artist — Example Song',
    );
    expect(element('scoreboard-team-score-team-one')?.textContent?.trim()).toBe('10');
    expect(element('admin-song-seconds-remaining')?.textContent).toContain('20.000');
    expect(element('admin-song-timer-progress')?.style.backgroundColor).toBe('rgb(69, 189, 142)');

    advance(1234);
    expect(element('admin-song-seconds-remaining')?.textContent).toContain('18.766');
    advance(8766);
    expect(element('admin-song-timer-progress')?.style.width).toBe('50%');
    expect(element('admin-song-timer-progress')?.style.backgroundColor).toBe('rgb(255, 206, 78)');
    advance(9000);
    expect(element('admin-song-timer-progress')?.style.backgroundColor).toBe('rgb(242, 21, 146)');

    advance(1000);
    expect(element('song-finished-panel')?.textContent).toContain("Time's up");
    expect(element('admin-repeat-song-button')).toBeInstanceOf(HTMLButtonElement);
    expect(element('admin-reveal-answer-button')).toBeInstanceOf(HTMLButtonElement);
    expect(element('admin-song-seconds-remaining')).toBeNull();
  });

  it('waits for the TV again after a wrong answer and resumes from the paused position', () => {
    receive({ type: 'play', scheduleId });
    advance(1250);
    receive({ type: 'pause', answeringTeamId: team.teamId, interruptId: 'interrupt-one' });
    expect(element('admin-song-seconds-remaining')).toBeNull();
    expect(element('answering-team-name')?.textContent?.trim()).toBe(team.name);
    expect(element('admin-answering-song-name')?.textContent).toContain(
      'Example Artist — Example Song',
    );

    element('admin-answer-wrong-button')?.click();
    expect(answer).toHaveBeenCalledWith('interrupt-one', false);
    expect(element('admin-answer-verdict')).toBeNull();

    receive({ type: 'answer', scheduleId, teamId: team.teamId, correct: false });
    expect(element('admin-answer-verdict')?.textContent).toContain('INCORRECT!');
    expect(element('admin-waiting-for-tv')).not.toBeNull();
    advance(2700);
    expect(element('admin-answer-verdict')).toBeNull();
    expect(element('admin-song-seconds-remaining')).toBeNull();

    receive({ type: 'play', scheduleId });
    expect(element('admin-song-seconds-remaining')?.textContent).toContain('18.750');
    expect(element('scoreboard-team-score-team-one')?.textContent?.trim()).toBe('0');
  });

  it('reveals a confirmed correct answer and clears the verdict on the next song', () => {
    receive({ type: 'pause', answeringTeamId: team.teamId, interruptId: 'interrupt-one' });
    receive({ type: 'answer', scheduleId, teamId: team.teamId, correct: true });
    expect(element('song-answer')?.textContent).toContain('Example Artist — Example Song');
    expect(element('admin-answer-verdict')?.textContent).toContain('CORRECT!');
    expect(element('admin-answer-verdict')?.textContent).toContain(team.name);

    element('admin-next-song-after-reveal-button')?.click();
    expect(next).toHaveBeenCalledOnce();
    receive({
      type: 'song_next',
      scheduleId: 'schedule-two',
      songId: 'song-two',
      question: 'Name the artist',
      answer: 'Second song',
      remaining: 20,
      answerDuration: 5,
    } as GameServerMessage);
    expect(element('admin-answer-verdict')).toBeNull();
    expect(element('admin-waiting-for-tv')).not.toBeNull();
    expect(element('admin-song-question')?.textContent).toContain('Name the artist');
  });
});
