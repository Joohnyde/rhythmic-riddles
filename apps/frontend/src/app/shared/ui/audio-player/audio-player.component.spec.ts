import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AudioPlayerComponent } from './audio-player.component';
import { AudioPlayerService } from './audio-player.service';

/** Browser media events are driven independently of the mocked currentTime clock. */
describe('AudioPlayerComponent playback progress', () => {
  let fixture: ComponentFixture<AudioPlayerComponent>;
  let component: AudioPlayerComponent;
  let position: number;
  let playing$: Subject<void>;
  let ended$: Subject<void>;

  beforeEach(async () => {
    vi.useFakeTimers();
    position = 0;
    playing$ = new Subject<void>();
    ended$ = new Subject<void>();
    await TestBed.configureTestingModule({
      imports: [AudioPlayerComponent],
      providers: [
        {
          provide: AudioPlayerService,
          useValue: {
            playing$,
            ended$,
            load: vi.fn(),
            seek: vi.fn(),
            pause: vi.fn(),
            play: vi.fn().mockResolvedValue(undefined),
            getCurrentTime: () => position,
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AudioPlayerComponent);
    component = fixture.componentInstance;
    component.src = '/snippet.mp3';
    component.scheduleId = 'schedule-one';
    component.playbackDelayId = 'attempt-one';
    component.remaining = 10;
    component.seek = 0;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('does not acknowledge the playing event until the media clock actually advances', () => {
    const acknowledged = vi.fn();
    component.playbackStarted.subscribe(acknowledged);
    playing$.next();
    vi.advanceTimersByTime(200);
    expect(acknowledged).not.toHaveBeenCalled();
    position = 0.04;
    vi.advanceTimersByTime(20);
    expect(acknowledged).toHaveBeenCalledTimes(1);
    playing$.next();
    position = 0.09;
    vi.advanceTimersByTime(20);
    expect(acknowledged).toHaveBeenCalledTimes(1);
  });

  it('requests a TV delay before calling audio.play()', () => {
    const second = TestBed.createComponent(AudioPlayerComponent);
    const player = second.componentInstance;
    player.src = '/snippet.mp3';
    player.scheduleId = 'another-schedule';
    player.remaining = 10;
    const requested = vi.fn();
    player.playbackRequested.subscribe(requested);
    const play = vi.mocked(TestBed.inject(AudioPlayerService).play);
    const playedBefore = play.mock.calls.length;
    second.detectChanges();
    expect(requested).toHaveBeenCalledTimes(1);
    expect(requested).toHaveBeenCalledWith('another-schedule');
    expect(play.mock.calls.length).toBe(playedBefore);
    second.componentRef.setInput('playbackDelayId', 'registered-attempt');
    second.detectChanges();
    expect(play.mock.calls.length).toBe(playedBefore + 1);
    second.destroy();
  });

  it('emits completion once even when countdown and ended fire in the same tick', () => {
    const complete = vi.fn();
    component.completed.subscribe(complete);
    playing$.next();
    position = 0.05;
    vi.advanceTimersByTime(20);
    position = 10;
    vi.advanceTimersByTime(100);
    ended$.next();
    expect(complete).toHaveBeenCalledTimes(1);
  });

  it('does not acknowledge after component teardown', () => {
    const acknowledged = vi.fn();
    component.playbackStarted.subscribe(acknowledged);
    playing$.next();
    fixture.destroy();
    position = 1;
    vi.advanceTimersByTime(100);
    expect(acknowledged).not.toHaveBeenCalled();
  });
});
