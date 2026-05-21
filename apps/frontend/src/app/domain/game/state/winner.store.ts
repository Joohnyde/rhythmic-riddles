import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subscription } from 'rxjs';
import { ClientSurface } from '../../../core/realtime/game-realtime.service';
import { DefaultMessage } from '../messages/default.messages';
import { S3WelcomeMessage } from '../messages/stage3.messages';
import { TeamScore } from '../models/team-score.model';
@Injectable({ providedIn: 'root' })
export class WinnerStore {
  private readonly router = inject(Router);
  private readonly scores = signal<TeamScore[]>([]);
  private sub?: Subscription;
  readonly vm = computed(() => ({ scores: this.scores() }));
  connect(messages$: Observable<DefaultMessage>, surface: ClientSurface): void {
    this.sub?.unsubscribe();
    this.sub = messages$.subscribe((m) => this.handleMessage(m, surface));
  }
  disconnect(): void {
    this.sub?.unsubscribe();
    this.sub = undefined;
  }
  private handleMessage(message: DefaultMessage, surface: ClientSurface): void {
    if (message.type !== 'welcome') return;
    const welcome = message as S3WelcomeMessage;
    if (welcome.stage === 'winner') this.scores.set(welcome.scores);
    else {
      this.disconnect();
      this.router.navigate(surface === 'admin' ? ['admin', welcome.stage] : [welcome.stage]);
    }
  }
}
