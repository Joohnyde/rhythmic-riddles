
import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import { interval, Subscription } from 'rxjs';
@Component({
  selector: 'app-seek-timer',
  imports: [CommonModule],
  templateUrl: './seek-timer.html',
  styleUrl: './seek-timer.scss',
})
export class SeekTimer implements OnInit, OnChanges, OnDestroy {
  /** Total length in seconds; if null => infinite timer */
  @Input() duration !: number;

  /** External seek command in seconds */
  @Input() seek: number | null = null;

  /** Optional debug display */
  @Input() showDebug = false;

  /** Fires once when we hit duration (if duration is set) */
  @Output() completed = new EventEmitter<void>();

  /** Emits current time on every tick / seek */
  @Output() timeChange = new EventEmitter<number>();
  @Output() stateOut = new EventEmitter<{ seek: number; remaining: number }>();


  currentTime = 0;
  private tickSub?: Subscription;
  private readonly tickMs = 100;
  private completedEmitted = false;

  get progress(): number {
    return this.duration ? Math.min(1, this.currentTime / this.duration) : 0;
  }

  ngOnInit(): void {
    this.startTimer();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('seek' in changes && this.seek != null) {
      this.seekTo(this.seek);
    }

    if ('duration' in changes && this.duration != null) {
      // if currentTime > new duration, clamp
      if (this.currentTime > this.duration) {
        this.seekTo(this.duration);
      }
    }
  }

  private startTimer() {
    if (this.tickSub) return;

    this.tickSub = interval(this.tickMs).subscribe(() => {
      this.currentTime += this.tickMs / 1000;

      if (this.duration != null && this.currentTime >= this.duration) {
        this.currentTime = this.duration;
        this.timeChange.emit(this.currentTime);

        if (!this.completedEmitted) {
          this.completedEmitted = true;
          this.completed.emit();
        }

        this.stopTimer(); // stop at the end
      } else {
        this.timeChange.emit(this.currentTime);
      }
    });
  }

  private stopTimer() {
    this.tickSub?.unsubscribe();
    this.tickSub = undefined;
  }

  private seekTo(seconds: number) {
    this.currentTime = Math.max(0, seconds);
    this.completedEmitted = false; // allow completion again if you seek back
    this.timeChange.emit(this.currentTime);

    if (this.duration != null && this.currentTime >= this.duration) {
      this.currentTime = this.duration;
      this.timeChange.emit(this.currentTime);
      if (!this.completedEmitted) {
        this.completedEmitted = true;
        this.completed.emit();
      }
      this.stopTimer();
    }
  }

  ngOnDestroy(): void {
    this.stopTimer(); // when *ngIf turns false, timer stops
    const currentSeek = this.currentTime;
    const currentRemaining = Math.max(this.duration - currentSeek, 0);

    this.stateOut.emit({
      seek: currentSeek,
      remaining: currentRemaining
    });
  }
}
