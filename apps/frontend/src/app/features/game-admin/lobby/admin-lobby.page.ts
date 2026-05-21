import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { LobbyStore } from '../../../domain/game/state/lobby.store';
@Component({
  selector: 'rr-admin-lobby-page',
  imports: [FormsModule],
  templateUrl: './admin-lobby.page.html',
  styleUrl: './admin-lobby.page.scss',
})
export class AdminLobbyPage implements OnInit, OnDestroy {
  private readonly session = inject(GameSession);
  private readonly router = inject(Router);
  readonly store = inject(LobbyStore);
  readonly form = signal({ name: '', image: '', buttonCode: '1671' });
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
  async createTeam(): Promise<void> {
    await this.store.createTeam(this.form());
    this.form.set({ name: '', image: '', buttonCode: '1671' });
  }
}
