import { TeamScore } from './team-score.model';

export interface Team {
  id: string;
  name: string;
  image: string;
}

export function teamFromScore(score: TeamScore): Team {
  return {
    id: score.teamId,
    name: score.name,
    image: score.image,
  };
}
