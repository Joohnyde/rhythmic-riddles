import { TeamScore } from '../models/team-score.model';
import { Team } from '../models/team.model';
import { DefaultMessage, WelcomeMessage } from './default.messages';

export interface S2RoundSnapshotMessage extends WelcomeMessage<'welcome' | 'song_next'> {
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

export type S2WelcomeMessage = S2RoundSnapshotMessage;
export type S2SongNextMessage = S2RoundSnapshotMessage;

export interface S2PauseMessage extends DefaultMessage<'pause'> {
  /** Team id, or the literal string 'null' for system pauses. */
  answeringTeamId: string | 'null';
  interruptId: string;
}

export interface S2ErrorSolvedMessage extends DefaultMessage<'error_solved'> {
  previousScenario: number;
}

export interface S2AnswerMessage extends DefaultMessage<'answer'> {
  teamId: string;
  scheduleId: string;
  correct: boolean;
}

export interface S2SongRepeatMessage extends DefaultMessage<'song_repeat'> {
  remaining: number;
}

export type S2SongRevealMessage = DefaultMessage<'song_reveal'>;

export type Stage2Message =
  | S2WelcomeMessage
  | S2SongNextMessage
  | S2PauseMessage
  | S2ErrorSolvedMessage
  | S2AnswerMessage
  | S2SongRepeatMessage
  | S2SongRevealMessage;
