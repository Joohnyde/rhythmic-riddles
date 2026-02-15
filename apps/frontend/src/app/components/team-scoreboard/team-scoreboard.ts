import { Component, Input } from '@angular/core';
import { TeamScore } from '../../entities/team.scores';

@Component({
  selector: 'app-team-scoreboard',
  imports: [],
  templateUrl: './team-scoreboard.html',
  styleUrl: './team-scoreboard.scss',
})
export class TeamScoreboard {
  @Input() bravo: TeamScore | undefined;
  @Input() teams: TeamScore[] = [];
  @Input() currentScheduleId: string | null = null;

  isAnsweredWrong(team: TeamScore): boolean {
    return this.bravo !== team && team.scheduleId === this.currentScheduleId;
  }

  isAnsweredCorrect(team: TeamScore): boolean {
    return this.bravo === team && team.scheduleId === this.currentScheduleId;
  }
}