import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  inject,
} from '@angular/core';
import { Subscription, filter, interval, take } from 'rxjs';
import { AudioPlayerService } from './audio-player.service';

/** Renders and plays audio; persistence and playback acknowledgement belong to the round store. */
@Component({
  selector: 'rr-audio-player',
  templateUrl: './audio-player.component.html',
  styleUrl: './audio-player.component.scss',
})
export class AudioPlayerComponent implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) src!: string;
  @Input() scheduleId: string | null = null;
  @Input() playbackDelayId: string | null = null;
  @Input() remaining = 0;
  @Input() seek = 0;

  @Output() playbackRequested = new EventEmitter<string>();
  @Output() playbackStarted = new EventEmitter<void>();
  @Output() playbackFailed = new EventEmitter<void>();
  @Output() completed = new EventEmitter<void>();
  @Output() stateOut = new EventEmitter<{
    seek: number;
    remaining: number;
    scheduleId: string | null;
  }>();

  private readonly cdr = inject(ChangeDetectorRef);
  readonly audio = inject(AudioPlayerService);

  private readonly sub = new Subscription();
  private countdownSub?: Subscription;
  private progressSub?: Subscription;
  private initialized = false;
  private started = false;
  private progressConfirmed = false;
  private finished = false;

  totalDuration = 0;
  remainingDisplay = 0;
  loaderPercent = 0;

  ngOnInit(): void {
    this.totalDuration = (this.remaining ?? 0) + (this.seek ?? 0);
    this.remainingDisplay = this.remaining;
    this.audio.load(this.src);
    if (this.seek > 0) {
      this.audio.seek(this.seek);
    }

    this.sub.add(
      this.audio.playing$.subscribe(() => {
        if (this.progressConfirmed || this.finished) return;
        this.progressSub?.unsubscribe();
        const position = this.audio.getCurrentTime();
        // 'playing' alone is insufficient: on slow devices media can still be stationary.
        this.progressSub = interval(20).pipe(
          filter(() => this.audio.getCurrentTime() >= position + 0.03),
          take(1),
        ).subscribe(() => {
          if (this.finished || this.progressConfirmed) return;
          this.progressConfirmed = true;
          if (!this.started) {
            this.started = true;
            this.startCountdown();
          }
          if (this.scheduleId) this.playbackStarted.emit();
        });
      }),
    );
    this.sub.add(this.audio.ended$.subscribe(() => this.finishPlayback()));

    this.initialized = true;
    if (this.scheduleId) {
      if (this.playbackDelayId) {
        this.playAudio();
      } else {
        this.playbackRequested.emit(this.scheduleId);
      }
    } else {
      // Answer audio has no persisted playback attempt.
      this.playAudio();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.initialized) return;
    if ('src' in changes || 'scheduleId' in changes) {
      // Angular can reuse this component when one playing song is replaced by another.
      this.audio.pause();
      this.stopCountdown();
      this.progressSub?.unsubscribe();
      this.started = false;
      this.progressConfirmed = false;
      this.finished = false;
      this.totalDuration = (this.remaining ?? 0) + (this.seek ?? 0);
      this.remainingDisplay = this.remaining;
      this.audio.load(this.src);
      this.audio.seek(this.seek ?? 0);
      if (this.scheduleId && !this.playbackDelayId) {
        this.playbackRequested.emit(this.scheduleId);
      } else {
        this.playAudio();
      }
      return;
    }
    if ('playbackDelayId' in changes && this.playbackDelayId && !this.started) {
      this.playAudio();
    }
  }

  private playAudio(): void {
    void this.audio.play().catch(() => this.playbackFailed.emit());
  }

  private startCountdown(): void {
    this.stopCountdown();
    this.countdownSub = interval(100).subscribe(() => {
      const remainingNow = Math.max(this.totalDuration - this.audio.getCurrentTime(), 0);
      this.remainingDisplay = Number(remainingNow.toFixed(1));
      this.loaderPercent =
        this.totalDuration > 0 ? (remainingNow / this.totalDuration) * 100 : 0;
      if (remainingNow <= 0) this.finishPlayback();
      this.cdr.markForCheck();
    });
  }

  private finishPlayback(): void {
    if (this.finished) return;
    this.finished = true;
    this.progressSub?.unsubscribe();
    this.stopCountdown();
    this.completed.emit();
  }

  private stopCountdown(): void {
    this.countdownSub?.unsubscribe();
    this.countdownSub = undefined;
  }

  ngOnDestroy(): void {
    this.finished = true;
    this.progressSub?.unsubscribe();
    this.stopCountdown();
    this.sub.unsubscribe();
    const seek = this.audio.getCurrentTime();
    this.stateOut.emit({
      scheduleId: this.scheduleId,
      seek,
      remaining: Math.max(this.totalDuration - seek, 0),
    });
    this.audio.pause();
    this.audio.seek(0);
  }
}
