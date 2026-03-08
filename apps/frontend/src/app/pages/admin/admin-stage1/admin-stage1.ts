import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { firstValueFrom } from 'rxjs';
import { CategorySimple } from '../../../entities/albums';
import { LastCategory } from '../../../entities/selected.album';
import { Team } from '../../../entities/teams';
import { CategoryService } from '../../../services/category.service';
import { S1WelcomeMessage } from '../../../entities/messages/stage1.messages';

@Component({
  selector: 'app-admin-stage1',
  imports: [],
  templateUrl: './admin-stage1.html',
  styleUrl: './admin-stage1.scss',
})
export class AdminStage1 implements OnInit {
  albums: CategorySimple[] = [];
  pickedByTeam?: Team;
  selectedAlbum?: LastCategory;
  loaded: boolean = false;
  showButtons: boolean = false;
  inTransit: boolean = false;

  constructor(
    private storage: Storage,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private categoryService: CategoryService,
  ) {
    if (this.storage.code === '') {
      this.router.navigate(['admin']);
    }
  }

  async ngOnInit(): Promise<void> {
    if (this.storage.messageObservable) {
      try {
        const mes = (await firstValueFrom(this.storage.messageObservable)) as S1WelcomeMessage;
        if (mes.type == 'welcome' && mes.stage == 'albums') {
          if (mes.albums != null) {
            this.albums = mes.albums;
            this.pickedByTeam = mes.team;
          } else {
            this.selectedAlbum = mes.selected;
            this.pickedByTeam = this.selectedAlbum?.pickedByTeam;
            this.showButtons = true;
          }

          this.loaded = true;
          this.cdr.markForCheck();
        }
      } catch (_ignored) {
        //Do nothing
      }
    }
  }

  async pickAlbum(categoryId: string) {
    if (this.inTransit) return;
    let teamId = null;
    if (this.pickedByTeam) teamId = this.pickedByTeam.id;
    this.inTransit = true;
    try {
      this.selectedAlbum = await firstValueFrom(this.categoryService.pickAlbum(categoryId, teamId));
      this.pickedByTeam = this.selectedAlbum.pickedByTeam;
      this.showButtons = true;

      this.cdr.markForCheck();
    } catch (_ignored) {
      /*TODO: Toast*/
    }
    this.inTransit = false;
  }

  async start() {
    if (this.inTransit || !this.selectedAlbum) return;
    this.inTransit = true;
    try {
      await firstValueFrom(this.categoryService.start(this.selectedAlbum.categoryId));
      // Transition to stage 2
      this.router.navigate(['admin', 'songs']);
    } catch (_ignored) {
      /*TODO: Toast*/
    }
    this.inTransit = false;
  }
}
