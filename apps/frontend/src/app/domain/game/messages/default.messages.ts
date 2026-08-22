import { GameStage } from '../models/game-stage.model';

export type GameMessageType =
  | 'welcome'
  | 'new_team'
  | 'kick_team'
  | 'button_clicked'
  | 'album_picked'
  | 'song_next'
  | 'song_reveal'
  | 'song_repeat'
  | 'answer'
  | 'error_solved'
  | 'pause';

export interface DefaultMessage<TType extends GameMessageType = GameMessageType> {
  type: TType;
}

export interface WelcomeMessage<
  TType extends GameMessageType = 'welcome',
> extends DefaultMessage<TType> {
  stage: GameStage;
}
