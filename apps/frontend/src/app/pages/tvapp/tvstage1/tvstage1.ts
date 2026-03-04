import { ChangeDetectorRef, Component } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { CategorySimple } from '../../../entities/albums';
import { LastCategory } from '../../../entities/selected.album';
import { Team } from '../../../entities/teams';
import { S1AlbumPickedMessage, S1WelcomeMessage } from '../../../entities/messages/stage1.messages';

@Component({
  selector: 'app-tvstage1',
  imports: [],
  templateUrl: './tvstage1.html',
  styleUrl: './tvstage1.scss',
})
export class TVStage1 {
  albums: CategorySimple[] = [];
  pickedByTeam?: Team;
  selectedAlbum?: LastCategory;
  loaded: boolean = false;
  showButtons: boolean = false;

  constructor(
    private storage: Storage,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {
    if (this.storage.code === '') {
      this.router.navigate(['']);
    } else if (this.storage.messageObservable) {
      const subs = this.storage.messageObservable.subscribe((mes) => {
        switch (mes.type) {
          case 'welcome': {
            const welcomeMessage = mes as S1WelcomeMessage;
            if (welcomeMessage.stage == 'albums') {
              if (welcomeMessage.albums != null) {
                this.albums = welcomeMessage.albums;
                this.pickedByTeam = welcomeMessage.team;
              } else {
                this.selectedAlbum = welcomeMessage.selected;
                this.pickedByTeam = this.selectedAlbum?.pickedByTeam;
                this.showButtons = true;
              }
              this.loaded = true;
            } else {
              subs.unsubscribe();
              this.router.navigate([welcomeMessage.stage]);
            }
            break;
          }
          case 'album_picked': {
            const albumPickedMessage = mes as S1AlbumPickedMessage;
            this.showButtons = true;
            this.selectedAlbum = albumPickedMessage.selected;
            this.pickedByTeam = this.selectedAlbum?.pickedByTeam;
            break;
          }
        }
        this.cdr.markForCheck();
      });
    }
  }
}
