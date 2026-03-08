import { CategorySimple } from '../albums';
import { LastCategory } from '../selected.album';
import { Team } from '../teams';
import { WelcomeMessage } from './default.messages';

export interface S1WelcomeMessage extends WelcomeMessage {
  albums?: CategorySimple[];
  team?: Team;
  selected?: LastCategory;
}

export interface S1AlbumPickedMessage extends WelcomeMessage {
  albums?: CategorySimple[];
  team?: Team;
  selected?: LastCategory;
}
