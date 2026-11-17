import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GameSession } from '../../../core/session/game-session.service';
import { SongScenario } from '../../../domain/game/state/song-scenario';
import { SongRoundStore } from '../../../domain/game/state/song-round.store';
import { SeekTimerComponent } from '../../../shared/ui/seek-timer/seek-timer.component';
import { AdminSongRoundPage } from './admin-song-round.page';

describe('AdminSongRoundPage playback and answering', () => {
  let fixture: ComponentFixture<AdminSongRoundPage>;
  const initialVm = () => ({
    scenario: SongScenario.Playing,
    question: 'Guess the title',
    answer: 'Example Artist — Example Song',
    seek: 0,
    remaining: 20,
    playbackWaiting: false,
    playbackError: null as string | null,
    teams: [],
    lastPlayedSong: 'schedule-one',
    inTransit: false,
    interruptId: null as string | null,
    answeringTeam: null as { name: string; image: string } | null,
    bravo: null as { name: string } | null,
    lastJudgement: null as { teamId: string; teamName: string; correct: boolean; sequence: number } | null,
  });
  const vm = signal(initialVm());
  const store = {
    vm,
    connect: vi.fn(),
    disconnect: vi.fn(),
    onTrackFinished: vi.fn(),
    savePlaybackState: vi.fn(),
    teamAnswered: vi.fn(),
    advanceGame: vi.fn(),
    nextSong: vi.fn(),
    resolveError: vi.fn(),
  };

  beforeEach(async () => {
    vm.set(initialVm());
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [AdminSongRoundPage],
      providers: [
        { provide: GameSession, useValue: { code: 'AKKU', messages$: of() } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: SongRoundStore, useValue: store },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminSongRoundPage);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('shows the actual song to the Admin and has no scoreboard or obsolete timer heading', () => {
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[data-testid="admin-song-question"]')?.textContent).toContain('GUESS THE SONG');
    expect(page.querySelector('[data-testid="admin-playing-song-name"]')?.textContent).toContain('Example Artist — Example Song');
    expect(page.querySelector('rr-team-scoreboard')).toBeNull();
    expect(page.textContent).not.toContain('Listen to the song');
    expect(page.textContent).not.toContain('Song playback in progress');
    expect(page.textContent).not.toContain('Snippet timer');
    // The hidden, shared timer still advances state without showing another card.
    expect(page.querySelector('.round-timer-driver')).not.toBeNull();
  });

  it('uses the linear green-to-yellow-to-pink timer and shared button styles', async () => {
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.round-clock-ring')).toBeNull();
    expect(page.querySelector('.round-timer-track[role="progressbar"]')).not.toBeNull();
    expect(page.textContent).not.toContain('STAGE 2');
    expect(page.textContent).not.toContain('SONG ROUND');
    const initialColor = fixture.componentInstance.timerColor();
    fixture.componentInstance.onTimerProgress(10, 20);
    await Promise.resolve();
    fixture.detectChanges();
    const halfwayColor = fixture.componentInstance.timerColor();
    fixture.componentInstance.onTimerProgress(19, 20);
    await Promise.resolve();
    fixture.detectChanges();
    const urgentColor = fixture.componentInstance.timerColor();
    expect(new Set([initialColor, halfwayColor, urgentColor]).size).toBe(3);
    expect(page.querySelector('[data-testid="admin-song-timer-progress"]')).not.toBeNull();

    vm.update((state) => ({ ...state, scenario: SongScenario.TeamAnswering, interruptId: 'one' }));
    fixture.detectChanges();
    const buttons = page.querySelectorAll<HTMLButtonElement>('.round-actions button');
    expect(buttons[0]?.classList.contains('btn-primary')).toBe(true);
    expect(buttons[1]?.classList.contains('btn-secondary')).toBe(true);
    expect(page.querySelector('.round-button')).toBeNull();
  });

  it('formats the Admin countdown in seconds and milliseconds', async () => {
    fixture.componentInstance.onTimerProgress(1.234, 20);
    await Promise.resolve();
    fixture.detectChanges();
    expect(fixture.componentInstance.timerDisplay()).toBe('18.766');
    expect(fixture.nativeElement.querySelector('[data-testid="admin-song-seconds-remaining"]')?.textContent)
      .toContain('18.766');
    const timer = fixture.debugElement.query(By.directive(SeekTimerComponent));
    expect((timer.componentInstance as SeekTimerComponent).tickIntervalMs).toBe(25);
  });

  it('forwards Admin countdown completion to the round store', () => {
    const timer = fixture.debugElement.query(By.directive(SeekTimerComponent));
    expect(timer).not.toBeNull();
    (timer.componentInstance as SeekTimerComponent).completed.emit();
    expect(store.onTrackFinished).toHaveBeenCalledOnce();
  });

  it('shows a TV readiness message and holds the Admin timer until a TV play acknowledgement', () => {
    vm.update((state) => ({ ...state, playbackWaiting: true }));
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('rr-seek-timer')).toBeNull();
    expect(page.querySelector('[data-testid="admin-waiting-for-tv"]')?.textContent).toContain('Waiting for the TV to be ready');
    vm.update((state) => ({ ...state, playbackWaiting: false }));
    fixture.detectChanges();
    expect(page.querySelector('rr-seek-timer')).not.toBeNull();
  });

  it('pauses the timer for the buzzing team and sends judgements to the store', () => {
    vm.update((state) => ({
      ...state,
      scenario: SongScenario.TeamAnswering,
      answeringTeam: { name: 'Team Sun', image: '/icons/sun_FF8800.png' },
      interruptId: 'interrupt-one',
    }));
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('rr-seek-timer')).toBeNull();
    expect(page.querySelector('[data-testid="admin-team-answering"]')?.textContent).toContain('Team Sun');
    expect(page.querySelector('[data-testid="admin-team-answering"]')?.textContent).toContain('is answering now');
    expect(page.querySelector('[data-testid="admin-answering-song-name"]')?.textContent)
      .toContain('Example Artist — Example Song');
    expect(page.querySelector('.round-answer-song > span')).toBeNull();
    expect((page.querySelector('.round-answering-icon') as HTMLImageElement)?.src)
      .toContain('sun_FF8800.png');
    expect((page.querySelector('[data-testid="answering-team-name"]') as HTMLElement)?.style.color)
      .toBe('rgb(255, 136, 0)');
    (page.querySelector('[data-testid="admin-answer-correct-button"]') as HTMLButtonElement).click();
    (page.querySelector('[data-testid="admin-answer-wrong-button"]') as HTMLButtonElement).click();
    expect(store.teamAnswered).toHaveBeenNthCalledWith(1, true);
    expect(store.teamAnswered).toHaveBeenNthCalledWith(2, false);
  });

  it('shows feedback from the server answer, rather than optimistically celebrating a button click', () => {
    vm.update((state) => ({
      ...state,
      lastJudgement: { teamId: 'team-one', teamName: 'Team Sun', correct: false, sequence: 1 },
    }));
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[data-testid="admin-answer-verdict"]')?.textContent).toContain('INCORRECT!');
    expect(page.querySelector('[data-testid="admin-answer-verdict"]')?.textContent).toContain('Team Sun');
  });
});
