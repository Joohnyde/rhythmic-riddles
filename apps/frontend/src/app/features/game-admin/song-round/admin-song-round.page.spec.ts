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

describe('AdminSongRoundPage playback timer', () => {
  let fixture: ComponentFixture<AdminSongRoundPage>;
  const vm = signal({
    scenario: SongScenario.Playing,
    question: 'Question',
    seek: 0,
    remaining: 20,
    playbackWaiting: false,
    teams: [],
    lastPlayedSong: 'schedule-one',
    inTransit: false,
  });
  const store = {
    vm,
    connect: vi.fn(),
    disconnect: vi.fn(),
    onTrackFinished: vi.fn(),
    savePlaybackState: vi.fn(),
  };

  beforeEach(async () => {
    vm.set({
      scenario: SongScenario.Playing,
      question: 'Question',
      seek: 0,
      remaining: 20,
      playbackWaiting: false,
      teams: [],
      lastPlayedSong: 'schedule-one',
      inTransit: false,
    });
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

  it('forwards Admin countdown completion to the round store', () => {
    const timer = fixture.debugElement.query(By.directive(SeekTimerComponent));
    expect(timer).not.toBeNull();
    (timer.componentInstance as SeekTimerComponent).completed.emit();
    expect(store.onTrackFinished).toHaveBeenCalledOnce();
  });

  it('does not mount the Admin timer while playback acknowledgement is pending', () => {
    vm.update((state) => ({ ...state, playbackWaiting: true }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('rr-seek-timer')).toBeNull();
    expect(fixture.nativeElement.querySelector('rr-song-loading-panel')).not.toBeNull();
  });
});
