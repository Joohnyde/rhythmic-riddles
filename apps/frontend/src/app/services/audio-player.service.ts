import { Injectable } from '@angular/core';
import { BehaviorSubject, fromEvent, map } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AudioPlayerService {
  private audio = new Audio();

  // Exposed state
  private isPlayingSubject = new BehaviorSubject<boolean>(false);
  isPlaying$ = this.isPlayingSubject.asObservable();

  private currentTimeSubject = new BehaviorSubject<number>(0);
  currentTime$ = this.currentTimeSubject.asObservable();

  private durationSubject = new BehaviorSubject<number>(0);
  duration$ = this.durationSubject.asObservable();

  // Emits once per "ended"
  ended$ = fromEvent(this.audio, 'ended').pipe(
    map(() => true)
  );

  constructor() {
    // Update current time
    fromEvent(this.audio, 'timeupdate')
      .pipe(map(() => this.audio.currentTime))
      .subscribe(t => this.currentTimeSubject.next(t));

    // Update duration when metadata is loaded
    fromEvent(this.audio, 'loadedmetadata')
      .pipe(map(() => this.audio.duration))
      .subscribe(d => this.durationSubject.next(d));

    // Playing / paused state
    fromEvent(this.audio, 'play').subscribe(() => this.isPlayingSubject.next(true));
    fromEvent(this.audio, 'pause').subscribe(() => this.isPlayingSubject.next(false));
  }

  load(src: string): void {
    if (this.audio.src !== src) {
      this.audio.src = src;
      this.audio.load();
      this.currentTimeSubject.next(0);
      this.durationSubject.next(0);
    }
  }

  play(): void {
    this.audio.play();
  }

  pause(): void {
    this.audio.pause();
  }

  togglePlay(): void {
    if (this.audio.paused) {
      this.play();
    } else {
      this.pause();
    }
  }

  seek(seconds: number): void {
    this.audio.currentTime = seconds;
    this.currentTimeSubject.next(seconds);
  }

  // You can pull this if you prefer direct access instead of the observable
  getCurrentTime(): number {
    return this.audio.currentTime;
  }

  getDuration(): number {
    return this.audio.duration;
  }
}
