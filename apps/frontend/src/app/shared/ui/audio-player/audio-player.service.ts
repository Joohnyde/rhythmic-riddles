import { Injectable } from '@angular/core';
import { BehaviorSubject, fromEvent, map } from 'rxjs';
@Injectable({ providedIn: 'root' })
export class AudioPlayerService {
  private audio = new Audio();
  private isPlayingSubject = new BehaviorSubject(false);
  readonly isPlaying$ = this.isPlayingSubject.asObservable();
  private currentTimeSubject = new BehaviorSubject(0);
  readonly currentTime$ = this.currentTimeSubject.asObservable();
  private durationSubject = new BehaviorSubject(0);
  readonly duration$ = this.durationSubject.asObservable();
  readonly ended$ = fromEvent(this.audio, 'ended').pipe(map(() => true));
  constructor() {
    fromEvent(this.audio, 'timeupdate')
      .pipe(map(() => this.audio.currentTime))
      .subscribe((t) => this.currentTimeSubject.next(t));
    fromEvent(this.audio, 'loadedmetadata')
      .pipe(map(() => this.audio.duration))
      .subscribe((d) => this.durationSubject.next(d));
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
    void this.audio.play();
  }
  pause(): void {
    this.audio.pause();
  }
  seek(seconds: number): void {
    this.audio.currentTime = seconds;
    this.currentTimeSubject.next(seconds);
  }
  getCurrentTime(): number {
    return this.audio.currentTime;
  }
  getDuration(): number {
    return this.audio.duration;
  }
}
