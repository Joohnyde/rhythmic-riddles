import { TeamScore } from '../models/team-score.model';
import { Team } from '../models/team.model';
import { DefaultMessage, WelcomeMessage } from './default.messages';
export interface S2WelcomeMessage extends WelcomeMessage {
  songId: string;
  question: string;
  answer: string;
  scheduleId: string;
  answerDuration: number;
  scores: TeamScore[];
  revealed?: boolean;
  bravo?: string;
  seek?: number;
  remaining?: number;
  answeringTeam?: Team;
  interruptId?: string;
  error?: boolean;
}
export interface S2PauseMessage extends DefaultMessage {
  answeringTeamId: string;
  interruptId: string;
}
export interface S2ErrorSolvedMessage extends DefaultMessage {
  previousScenario: number;
}
export interface S2AnswerMessage extends DefaultMessage {
  teamId: string;
  scheduleId: string;
  correct: boolean;
}
export interface S2SongRepeatMessage extends DefaultMessage {
  remaining: number;
}
