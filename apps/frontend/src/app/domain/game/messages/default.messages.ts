import type { GameStage } from '../models/game-stage.model';

/** Runtime and compile-time source of truth for backend WebSocket frame types understood by the frontend. */
export const GAME_MESSAGE_TYPES = [
  'welcome',
  'new_team',
  'kick_team',
  'button_clicked',
  'album_picked',
  'song_next',
  'song_reveal',
  'song_repeat',
  'answer',
  'error_solved',
  'pause',
] as const;

export type GameMessageType = (typeof GAME_MESSAGE_TYPES)[number];

export interface DefaultMessage<TType extends GameMessageType = GameMessageType> {
  type: TType;
}

export interface WelcomeMessage<
  TType extends GameMessageType = 'welcome',
> extends DefaultMessage<TType> {
  stage: GameStage;
}
