import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnInit,
  OnDestroy,
  ChangeDetectorRef,
} from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { AudioPlayerService } from '../../services/audio-player.service';

@Component({
  selector: 'app-audio-player',
  templateUrl: './audio-player.html',
  styleUrls: ['./audio-player.scss'],
})
export class AudioPlayer implements OnInit, OnDestroy {
  @Input() src!: string;

  // new inputs for animation logic
  @Input() remaining!: number; // seconds left from current position
  @Input() seek = 0;           // current position in seconds

  @Output() completed = new EventEmitter<void>();
  @Output() stateOut = new EventEmitter<{ seek: number; remaining: number }>();


  private sub = new Subscription();
  private countdownSub?: Subscription;

  totalDuration = 0;        // remaining + seek
  remainingDisplay = 0;     // what we show, e.g. 5.0, 4.9, ...
  loaderPercent = 0;        // 0..100 (remaining / total)

  constructor(public audio: AudioPlayerService, private cdf: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.audio.load(this.src);

    this.totalDuration = (this.remaining ?? 0) + (this.seek ?? 0);
    
    // position audio at initial seek
    if (this.seek && this.seek > 0) {
      this.audio.seek(this.seek);
    }

    // start playing
    this.audio.play();

    // emit completed when song ends
    this.sub.add(
      this.audio.ended$.subscribe(() => {
        this.completed.emit();
        this.stopCountdown();
      })
    );

    // start visual countdown
    this.startCountdown();
  }

  private startCountdown() {
    this.stopCountdown(); // safety

    // update every 100ms
    this.countdownSub = interval(100).subscribe(() => {
      const currentTime = this.audio.getCurrentTime(); // seconds, float
      const remainingNow = Math.max(this.totalDuration - currentTime, 0);

      // one decimal place, e.g. 5.0, 4.9, ...
      this.remainingDisplay = Number(remainingNow.toFixed(1));

      // percent of remaining out of total, 0..100
      this.loaderPercent =
        this.totalDuration > 0
          ? (remainingNow / this.totalDuration) * 100
          : 0;

      // optional: if remaining hits 0, emit completed (if not already)
      if (remainingNow <= 0.0) {
        this.completed.emit();
        this.stopCountdown();
      }
      this.cdf.markForCheck();
    });
  }

  private stopCountdown() {
    this.countdownSub?.unsubscribe();
    this.countdownSub = undefined;
  }

  ngOnDestroy(): void {
    this.stopCountdown();
    this.sub.unsubscribe();

    const currentTime = this.audio.getCurrentTime(); // seconds, float
    const remainingNow = Math.max(this.totalDuration - currentTime, 0);
    this.stateOut.emit({
      seek: currentTime,
      remaining: remainingNow
    });
    this.audio.pause();
    this.audio.seek(0);
  }
}
