import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { WinnerStore } from '../../../domain/game/state/winner.store';
@Component({
  selector: 'rr-tv-winner-page',
  templateUrl: './tv-winner.page.html',
  styleUrl: './tv-winner.page.scss',
})
export class TvWinnerPage implements OnInit, OnDestroy {
  private readonly session = inject(GameSession);
  private readonly router = inject(Router);
  readonly store = inject(WinnerStore);
  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router.navigate(['']);
      return;
    }
    this.store.connect(this.session.messages$, 'tv');
  }
  ngOnDestroy(): void {
    this.store.disconnect();
  }
}
