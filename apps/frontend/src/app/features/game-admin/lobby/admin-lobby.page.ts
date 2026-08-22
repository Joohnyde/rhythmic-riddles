import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { LobbyStore } from '../../../domain/game/state/lobby.store';
import { BrandLetteringComponent } from '../../../shared/ui/brand-lettering/brand-lettering.component';
import { AdminTeamFormComponent } from './components/admin-team-form/admin-team-form.component';
import { AdminTeamListComponent } from './components/admin-team-list/admin-team-list.component';

@Component({
  selector: 'rr-admin-lobby-page',
  imports: [BrandLetteringComponent, AdminTeamFormComponent, AdminTeamListComponent],
  templateUrl: './admin-lobby.page.html',
  styleUrl: './admin-lobby.page.scss',
})
export class AdminLobbyPage implements OnInit, OnDestroy {
  readonly session = inject(GameSession);
  readonly store = inject(LobbyStore);
  private readonly router = inject(Router);

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
