import { Component, Input } from '@angular/core';
import { TeamScore } from '../../entities/team.scores';

@Component({
  selector: 'app-team-scoreboard',
  imports: [],
  templateUrl: './team-scoreboard.html',
  styleUrl: './team-scoreboard.scss',
})
export class TeamScoreboard {
  @Input() teams: TeamScore[] = [];
  @Input() current_redoslijed_id: string | null = null;

  isAnsweredWrong(team: TeamScore): boolean {
    return !!team.odgovarao && team.odgovarao === this.current_redoslijed_id;
  }
}