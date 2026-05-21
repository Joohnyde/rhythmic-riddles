import { TeamScore } from './team-score.model';
export class Team {
  id!: string;
  name!: string;
  image!: string;
  constructor(teamScore?: TeamScore) {
    if (teamScore) {
      this.id = teamScore.teamId;
      this.name = teamScore.name;
      this.image = teamScore.image;
    }
  }
}
