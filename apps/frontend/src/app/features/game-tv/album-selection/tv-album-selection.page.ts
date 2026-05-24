import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { AlbumSelectionStore } from '../../../domain/game/state/album-selection.store';
import { PickerIdentityComponent } from '../../../shared/ui/picker-identity/picker-identity.component';
@Component({
  imports: [PickerIdentityComponent],
  selector: 'rr-tv-album-selection-page',
  templateUrl: './tv-album-selection.page.html',
  styleUrl: './tv-album-selection.page.scss',
})
export class TvAlbumSelectionPage implements OnInit, OnDestroy {
  private readonly session = inject(GameSession);
  private readonly router = inject(Router);
  readonly store = inject(AlbumSelectionStore);
  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router.navigate(['']);
      return;
    }
    this.store.connect(this.session.messages$, 'tv');
  }
  ngOnDestroy(): void {
    this.store.disconnect();
  }
}
