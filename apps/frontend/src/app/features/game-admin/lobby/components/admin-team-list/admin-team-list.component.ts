import { Component, inject } from '@angular/core';
import { LobbyStore } from '../../../../../domain/game/state/lobby.store';
import { BrandLetteringComponent } from '../../../../../shared/ui/brand-lettering/brand-lettering.component';
import { ConfirmDialogComponent } from '../../../../../shared/ui/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'rr-admin-team-list',
  imports: [BrandLetteringComponent, ConfirmDialogComponent],
  templateUrl: './admin-team-list.component.html',
  styleUrl: './admin-team-list.component.scss',
})
export class AdminTeamListComponent {
  readonly store = inject(LobbyStore);
}
