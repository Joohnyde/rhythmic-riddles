import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { teamIconColor } from '../../../../domain/game/models/team-icon.utils';
import { Team } from '../../../../domain/game/models/team.model';
import { BrandLetteringComponent } from '../../brand-lettering/brand-lettering.component';

@Component({
  selector: 'rr-stage1-category-header',
  standalone: true,
  imports: [BrandLetteringComponent],
  templateUrl: './stage1-category-header.component.html',
  styleUrl: './stage1-category-header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Stage1CategoryHeaderComponent {
  readonly roomCode = input('');
  readonly pickedByTeam = input<Team | null>(null);
  readonly loaded = input(false);
  readonly selected = input(false);

  teamColor(image: string): string {
    return teamIconColor(image) ?? 'var(--primary)';
  }
}
