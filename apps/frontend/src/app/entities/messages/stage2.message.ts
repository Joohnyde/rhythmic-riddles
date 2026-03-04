import { TeamScore } from '../team.scores';
import { Team } from '../teams';
import { DefaultMessage, WelcomeMessage } from './default.messages';

export interface S2WelcomeMessage extends WelcomeMessage {
  // Mandatory fields
  songId: string;
  question: string;
  answer: string;
  scheduleId: string;
  answerDuration: number;
  scores: TeamScore[];

  // Scenario 0
  revealed?: boolean;
  bravo?: string;

  seek?: number;
  remaining?: number;

  // Scenario 3
  answeringTeam?: Team;
  interruptId?: string;

  // Scenario 4
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
