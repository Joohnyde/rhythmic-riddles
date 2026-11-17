import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { teamIconColor } from '../../../domain/game/models/team-icon.utils';
import { SongRoundStore } from '../../../domain/game/state/song-round.store';
import { SongScenario } from '../../../domain/game/state/song-scenario';
import { SeekTimerComponent } from '../../../shared/ui/seek-timer/seek-timer.component';
import { SongLoadingPanelComponent } from '../../../shared/ui/song-round-panels/song-loading-panel/song-loading-panel.component';
import { createAnswerFeedback } from '../../../shared/ui/song-round-presentation/song-round-presentation';
import { TeamScoreboardComponent } from '../../../shared/ui/team-scoreboard/team-scoreboard.component';

@Component({
  selector: 'rr-admin-song-round-page',
  imports: [SeekTimerComponent, SongLoadingPanelComponent, TeamScoreboardComponent],
  templateUrl: './admin-song-round.page.html',
  styleUrl: './admin-song-round.page.scss',
})
export class AdminSongRoundPage implements OnInit, OnDestroy {
  readonly session = inject(GameSession);
  readonly store = inject(SongRoundStore);
  readonly SongScenario = SongScenario;
  readonly verdict = createAnswerFeedback(this.session.messages$, this.store.vm);

  private readonly router = inject(Router);

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

  answeringTeamColor(image: string | undefined): string {
    return teamIconColor(image ?? '') ?? 'var(--primary)';
  }

  formatRemaining(seconds: number): string {
    return (Math.ceil(seconds * 1000) / 1000).toFixed(3);
  }

  timerColor(fraction: number): string {
    const upper = fraction >= 0.5;
    const from = upper ? [255, 206, 78] : [240, 0, 154];
    const to = upper ? [69, 189, 142] : [255, 206, 78];
    const blend = upper ? (fraction - 0.5) * 2 : fraction * 2;
    return `rgb(${from.map((channel, index) => Math.round(channel + (to[index]! - channel) * blend)).join(' ')})`;
  }
}
