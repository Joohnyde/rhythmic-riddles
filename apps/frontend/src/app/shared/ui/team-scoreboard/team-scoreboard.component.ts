import { Component, Input } from '@angular/core';
import { TeamScore } from '../../../domain/game/models/team-score.model';
@Component({
  selector: 'rr-team-scoreboard',
  templateUrl: './team-scoreboard.component.html',
  styleUrl: './team-scoreboard.component.scss',
})
export class TeamScoreboardComponent {
  @Input() bravo: TeamScore | null = null;
  @Input() teams: TeamScore[] = [];
  @Input() currentScheduleId: string | null = null;
  isAnsweredWrong(team: TeamScore): boolean {
    return this.bravo !== team && team.scheduleId === this.currentScheduleId;
  }
  isAnsweredCorrect(team: TeamScore): boolean {
    return this.bravo === team && team.scheduleId === this.currentScheduleId;
  }
}
