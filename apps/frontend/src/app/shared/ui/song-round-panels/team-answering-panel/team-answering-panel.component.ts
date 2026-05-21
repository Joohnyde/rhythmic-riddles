import { Component, Input } from '@angular/core';
import { Team } from '../../../../domain/game/models/team.model';
@Component({
  selector: 'rr-team-answering-panel',
  templateUrl: './team-answering-panel.component.html',
  styleUrl: './team-answering-panel.component.scss',
})
export class TeamAnsweringPanelComponent {
  @Input() team: Team | null = null;
}
