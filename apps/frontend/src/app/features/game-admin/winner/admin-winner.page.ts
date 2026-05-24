import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { WinnerStore } from '../../../domain/game/state/winner.store';
@Component({
  selector: 'rr-admin-winner-page',
  templateUrl: './admin-winner.page.html',
  styleUrl: './admin-winner.page.scss',
})
export class AdminWinnerPage implements OnInit, OnDestroy {
  private readonly session = inject(GameSession);
  private readonly router = inject(Router);
  readonly store = inject(WinnerStore);
  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router.navigate(['admin']);
      return;
    }
    this.store.connect(this.session.messages$, 'admin');
  }
  ngOnDestroy(): void {
    this.store.disconnect();
  }
}
