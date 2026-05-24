import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { LobbyStore } from '../../../domain/game/state/lobby.store';
@Component({
  selector: 'rr-tv-lobby-page',
  templateUrl: './tv-lobby.page.html',
  styleUrl: './tv-lobby.page.scss',
})
export class TvLobbyPage implements OnInit, OnDestroy {
  private readonly session = inject(GameSession);
  private readonly router = inject(Router);
  readonly store = inject(LobbyStore);
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
