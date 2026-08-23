import { CategorySimple } from '../models/album.model';
import { LastCategory } from '../models/selected-album.model';
import { Team } from '../models/team.model';
import { WelcomeMessage } from './default.messages';

export interface S1WelcomeMessage extends WelcomeMessage<'welcome'> {
  /** Complete Stage 1 album list; present in every albums-stage recovery snapshot. */
  albums: CategorySimple[];
  /** Next picker when choosing; null means Admin chooses. Absent while displaying a selection. */
  team?: Team | null;
  /** Album picked but not started yet. Absent while choosing the next album. */
  selected?: LastCategory;
}

export interface S1AlbumPickedMessage extends WelcomeMessage<'album_picked'> {
  team?: Team;
  selected?: LastCategory;
}

export type Stage1Message = S1WelcomeMessage | S1AlbumPickedMessage;
