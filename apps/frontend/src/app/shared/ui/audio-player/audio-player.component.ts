import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  inject,
} from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { AudioPlayerService } from './audio-player.service';
@Component({
  selector: 'rr-audio-player',
  templateUrl: './audio-player.component.html',
  styleUrl: './audio-player.component.scss',
})
export class AudioPlayerComponent implements OnInit, OnDestroy {
  @Input({ required: true }) src!: string;
  @Input() remaining = 0;
  @Input() seek = 0;
  @Output() completed = new EventEmitter<void>();
  @Output() stateOut = new EventEmitter<{ seek: number; remaining: number }>();
  private readonly cdr = inject(ChangeDetectorRef);
  readonly audio = inject(AudioPlayerService);
  private sub = new Subscription();
  private countdownSub?: Subscription;
  totalDuration = 0;
  remainingDisplay = 0;
  loaderPercent = 0;
  ngOnInit(): void {
    this.audio.load(this.src);
    this.totalDuration = (this.remaining ?? 0) + (this.seek ?? 0);
    if (this.seek > 0) this.audio.seek(this.seek);
    this.audio.play();
    this.sub.add(
      this.audio.ended$.subscribe(() => {
        this.completed.emit();
        this.stopCountdown();
      }),
    );
    this.startCountdown();
  }
  private startCountdown() {
    this.stopCountdown();
    this.countdownSub = interval(100).subscribe(() => {
      const current = this.audio.getCurrentTime();
      const remainingNow = Math.max(this.totalDuration - current, 0);
      this.remainingDisplay = Number(remainingNow.toFixed(1));
      this.loaderPercent = this.totalDuration > 0 ? (remainingNow / this.totalDuration) * 100 : 0;
      if (remainingNow <= 0) {
        this.completed.emit();
        this.stopCountdown();
      }
      this.cdr.markForCheck();
    });
  }
  private stopCountdown() {
    this.countdownSub?.unsubscribe();
    this.countdownSub = undefined;
  }
  ngOnDestroy(): void {
    this.stopCountdown();
    this.sub.unsubscribe();
    const seek = this.audio.getCurrentTime();
    this.stateOut.emit({ seek, remaining: Math.max(this.totalDuration - seek, 0) });
    this.audio.pause();
    this.audio.seek(0);
  }
}
