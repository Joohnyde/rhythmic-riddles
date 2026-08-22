import { Team } from '../models/team.model';
import { DefaultMessage, WelcomeMessage } from './default.messages';

export interface S0WelcomeMessage extends WelcomeMessage<'welcome'> {
  teams: Team[];
}

export interface S0NewTeamMessage extends DefaultMessage<'new_team'> {
  team: Team;
}

export interface S0KickTeamMessage extends DefaultMessage<'kick_team'> {
  uuid: string;
}

export interface S0ButtonClickedMessage extends DefaultMessage<'button_clicked'> {
  buttonCode: string;
}

export type Stage0Message =
  S0WelcomeMessage | S0NewTeamMessage | S0KickTeamMessage | S0ButtonClickedMessage;
