import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { AlbumSelectionStore } from '../../../domain/game/state/album-selection.store';
import { PickerIdentityComponent } from '../../../shared/ui/picker-identity/picker-identity.component';
@Component({
  imports: [PickerIdentityComponent],
  selector: 'rr-admin-album-selection-page',
  templateUrl: './admin-album-selection.page.html',
  styleUrl: './admin-album-selection.page.scss',
})
export class AdminAlbumSelectionPage implements OnInit, OnDestroy {
  private readonly session = inject(GameSession);
  private readonly router = inject(Router);
  readonly store = inject(AlbumSelectionStore);
  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router.navigate(['admin']);
      return;
    }
    this.store.connect(this.session.messages$, 'admin');
  }
  ngOnDestroy(): void {
    this.store.disconnect();
  }
}
