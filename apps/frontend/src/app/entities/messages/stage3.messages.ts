import { TeamScore } from '../team.scores';
import { WelcomeMessage } from './default.messages';

export interface S3WelcomeMessage extends WelcomeMessage {
  scores: TeamScore[];
}
