import { Team } from '../teams';
import { DefaultMessage, WelcomeMessage } from './default.messages';

export interface S0WelcomeMessage extends WelcomeMessage {
  teams: Team[];
}

export interface S0NewTeamMessage extends DefaultMessage {
  team: Team;
}

export interface S0KickTeamMessage extends DefaultMessage {
  uuid: string;
}
