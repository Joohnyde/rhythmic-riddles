import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subscription } from 'rxjs';
import { DefaultMessage } from '../messages/default.messages';
import { GameServerMessage } from '../messages/game-server-message.types';
import { S3WelcomeMessage } from '../messages/stage3.messages';
import { ClientSurface } from '../models/client-surface.model';
import { routeForStage } from '../models/game-stage.model';
import { TeamScore } from '../models/team-score.model';

interface WinnerState {
  readonly scores: readonly TeamScore[];
}

interface WinnerVm extends WinnerState {
  readonly winner: TeamScore | null;
}

function createInitialWinnerState(): WinnerState {
  return {
    scores: [],
  };
}

@Injectable({ providedIn: 'root' })
export class WinnerStore {
  private readonly router = inject(Router);
  private readonly state = signal<WinnerState>(createInitialWinnerState());
  private sub?: Subscription;

  readonly vm = computed<WinnerVm>(() => {
    const scores = this.state().scores;
    return {
      scores,
      winner: scores.length > 0 ? [...scores].sort((a, b) => b.score - a.score)[0] : null,
    };
  });

  connect(messages$: Observable<GameServerMessage>, surface: ClientSurface): void {
    this.sub?.unsubscribe();
    this.sub = messages$.subscribe((message) => this.handleMessage(message, surface));
  }

  disconnect(): void {
    this.sub?.unsubscribe();
    this.sub = undefined;
  }

  private handleMessage(message: DefaultMessage, surface: ClientSurface): void {
    if (message.type !== 'welcome') {
      return;
    }

    const welcome = message as S3WelcomeMessage;
    if (welcome.stage === 'winner') {
      this.state.set({ scores: welcome.scores });
      return;
    }

    this.disconnect();
    void this.router.navigate(routeForStage(surface, welcome.stage));
  }
}
