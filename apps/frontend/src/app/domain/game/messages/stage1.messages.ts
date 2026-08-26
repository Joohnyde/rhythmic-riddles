import { CategorySimple } from '../models/album.model';
import { LastCategory } from '../models/selected-album.model';
import { Team } from '../models/team.model';
import { WelcomeMessage } from './default.messages';

export interface S1WelcomeMessage extends WelcomeMessage<'welcome'> {
  albums?: CategorySimple[];
  team?: Team;
  selected?: LastCategory;
}

export interface S1AlbumPickedMessage extends WelcomeMessage<'album_picked'> {
  team?: Team;
  selected?: LastCategory;
}

export type Stage1Message = S1WelcomeMessage | S1AlbumPickedMessage;
