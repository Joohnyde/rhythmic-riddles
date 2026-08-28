import { CategorySimple } from '../models/album.model';
import { LastCategory } from '../models/selected-album.model';
import { Team } from '../models/team.model';
import { DefaultMessage, WelcomeMessage } from './default.messages';

interface S1WelcomeBase extends WelcomeMessage<'welcome'> {
  readonly stage: 'albums';
  /**
   * Authoritative Stage 1 album membership/metadata snapshot from the backend.
   * Backend array order is not a visual contract: the frontend canonicalizes this collection before
   * rendering so logical album positions remain stable across refresh, reconnect, and recovery.
   */
  readonly albums: CategorySimple[];
}

export type S1WelcomeMessage =
  | (S1WelcomeBase & {
      /** Team currently choosing; null means the Admin is choosing. */
      readonly team: Team | null;
      readonly selected?: never;
    })
  | (S1WelcomeBase & {
      /** Recovery snapshot for the category already selected and waiting to start. */
      readonly selected: LastCategory;
      readonly team?: never;
    })
  | (S1WelcomeBase & {
      /** Defensive final-album Stage 1 snapshot: nobody is choosing and nothing awaits start. */
      readonly team?: never;
      readonly selected?: never;
    });

export interface S1AlbumPickedMessage extends DefaultMessage<'album_picked'> {
  /** Authoritative selected-category snapshot emitted to TV after a Stage 1 pick. */
  readonly selected: LastCategory;
}

export type Stage1Message = S1WelcomeMessage | S1AlbumPickedMessage;
