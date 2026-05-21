import { CommonModule } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  computed,
  inject,
  signal,
} from '@angular/core';

@Component({
  selector: 'rr-seek-timer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './seek-timer.component.html',
  styleUrl: './seek-timer.component.scss',
})
export class SeekTimerComponent implements OnChanges, OnDestroy {
  /**
   * Total snippet duration in seconds.
   * For admin this should be calculated as: current seek + remaining.
   */
  @Input({ required: true }) duration!: number | null;

  /** Current elapsed position in seconds, usually received from backend on welcome/pause restore. */
  @Input() seek: number | null = 0;

  /** Optional debug display for admin. */
  @Input() showDebug = false;

  @Output() completed = new EventEmitter<void>();
  @Output() timeChange = new EventEmitter<number>();
  @Output() stateOut = new EventEmitter<{ seek: number; remaining: number }>();

  private readonly cdr = inject(ChangeDetectorRef);

  readonly currentTime = signal(0);
  readonly durationValue = signal(0);

  readonly remainingTime = computed(() => Math.max(this.durationValue() - this.currentTime(), 0));

  readonly progress = computed(() => {
    const duration = this.durationValue();
    return duration <= 0 ? 0 : Math.max(0, this.remainingTime() / duration);
  });

  private intervalId: number | null = null;
  private startedAtMs = 0;
  private baseSeek = 0;
  private completedEmitted = false;

  ngOnChanges(changes: SimpleChanges): void {
    const durationChanged = 'duration' in changes;
    const seekChanged = 'seek' in changes;

    if (!durationChanged && !seekChanged) {
      return;
    }

    this.restartFromInputs();
  }

  ngOnDestroy(): void {
    this.stop();
    this.emitCurrentState();
  }

  private restartFromInputs(): void {
    this.stop();

    const duration = this.cleanSeconds(this.duration, 0);
    const requestedSeek = this.cleanSeconds(this.seek, 0);
    const clampedSeek =
      duration > 0 ? Math.min(requestedSeek, duration) : Math.max(requestedSeek, 0);

    this.durationValue.set(duration);
    this.baseSeek = clampedSeek;
    this.currentTime.set(clampedSeek);
    this.completedEmitted = false;
    this.timeChange.emit(clampedSeek);
    this.cdr.markForCheck();

    if (duration <= 0) {
      return;
    }

    if (clampedSeek >= duration) {
      this.finish();
      return;
    }

    this.startedAtMs = performance.now();

    // Use a wall-clock based interval. The displayed value updates every 100ms,
    // but the actual time calculation does not drift because it is based on performance.now().
    this.intervalId = window.setInterval(() => this.tick(), 100);
  }

  private tick(): void {
    const duration = this.durationValue();
    const elapsedSinceStart = (performance.now() - this.startedAtMs) / 1000;
    const nextCurrentTime = Math.min(duration, this.baseSeek + elapsedSinceStart);

    this.currentTime.set(nextCurrentTime);
    this.timeChange.emit(nextCurrentTime);
    this.cdr.markForCheck();

    if (nextCurrentTime >= duration) {
      this.finish();
    }
  }

  private finish(): void {
    this.stop();

    const duration = this.durationValue();
    this.currentTime.set(duration);
    this.timeChange.emit(duration);
    this.cdr.markForCheck();

    if (!this.completedEmitted) {
      this.completedEmitted = true;
      this.completed.emit();
    }
  }

  private emitCurrentState(): void {
    const duration = this.durationValue();
    const currentTime = this.currentTime();
    const seek = duration > 0 ? Math.min(currentTime, duration) : Math.max(currentTime, 0);

    this.stateOut.emit({
      seek,
      remaining: Math.max(duration - seek, 0),
    });
  }

  private stop(): void {
    if (this.intervalId !== null) {
      window.clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  private cleanSeconds(value: number | null | undefined, fallback: number): number {
    if (typeof value !== 'number' || Number.isNaN(value) || !Number.isFinite(value)) {
      return fallback;
    }

    return Math.max(value, 0);
  }
}
