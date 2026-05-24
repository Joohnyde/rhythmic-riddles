import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Team } from '../../../domain/game/models/team.model';

@Component({
  selector: 'rr-picker-identity',
  standalone: true,
  templateUrl: './picker-identity.component.html',
  styleUrl: './picker-identity.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PickerIdentityComponent {
  readonly team = input<Team | null>(null);
  readonly showLabel = input<boolean>(true);
}
