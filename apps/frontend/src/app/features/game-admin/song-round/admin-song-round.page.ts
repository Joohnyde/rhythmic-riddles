import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { SongRoundStore } from '../../../domain/game/state/song-round.store';
import { SongScenario } from '../../../domain/game/state/song-scenario';
import { SeekTimerComponent } from '../../../shared/ui/seek-timer/seek-timer.component';
import { TeamScoreboardComponent } from '../../../shared/ui/team-scoreboard/team-scoreboard.component';
import { SongRevealedPanelComponent } from '../../../shared/ui/song-round-panels/song-revealed-panel/song-revealed-panel.component';
import { SongFinishedPanelComponent } from '../../../shared/ui/song-round-panels/song-finished-panel/song-finished-panel.component';
import { TeamAnsweringPanelComponent } from '../../../shared/ui/song-round-panels/team-answering-panel/team-answering-panel.component';
import { SystemErrorPanelComponent } from '../../../shared/ui/song-round-panels/system-error-panel/system-error-panel.component';
import { SongLoadingPanelComponent } from '../../../shared/ui/song-round-panels/song-loading-panel/song-loading-panel.component';
@Component({
  selector: 'rr-admin-song-round-page',
  imports: [
    SeekTimerComponent,
    TeamScoreboardComponent,
    SongRevealedPanelComponent,
    SongFinishedPanelComponent,
    TeamAnsweringPanelComponent,
    SystemErrorPanelComponent,
    SongLoadingPanelComponent,
  ],
  templateUrl: './admin-song-round.page.html',
  styleUrl: './admin-song-round.page.scss',
})
export class AdminSongRoundPage implements OnInit, OnDestroy {
  private readonly session = inject(GameSession);
  private readonly router = inject(Router);
  readonly store = inject(SongRoundStore);
  readonly SongScenario = SongScenario;
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
