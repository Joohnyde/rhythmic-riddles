import { TeamScore } from '../models/team-score.model';
import { WelcomeMessage } from './default.messages';
export interface S3WelcomeMessage extends WelcomeMessage {
  scores: TeamScore[];
}
