import { Component, inject } from '@angular/core';
import { LobbyStore } from '../../../../../domain/game/state/lobby.store';
import { BrandLetteringComponent } from '../../../../../shared/ui/brand-lettering/brand-lettering.component';

@Component({
  selector: 'rr-admin-team-list',
  imports: [BrandLetteringComponent],
  templateUrl: './admin-team-list.component.html',
  styleUrl: './admin-team-list.component.scss',
})
export class AdminTeamListComponent {
  readonly store = inject(LobbyStore);
}
