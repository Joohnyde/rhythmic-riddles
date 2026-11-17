import { Component, OnDestroy, OnInit, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { SongJudgement, SongRoundStore } from '../../../domain/game/state/song-round.store';
import { SongScenario } from '../../../domain/game/state/song-scenario';
import { teamIconColor } from '../../../domain/game/models/team-icon.utils';
import { SeekTimerComponent } from '../../../shared/ui/seek-timer/seek-timer.component';
import { SongLoadingPanelComponent } from '../../../shared/ui/song-round-panels/song-loading-panel/song-loading-panel.component';
import { SelectedAlbumArtworkService } from '../../../shared/ui/song-round-presentation/selected-album-artwork.service';

const VERDICT_VISIBLE_MS = 2700;

@Component({
  selector: 'rr-admin-song-round-page',
  imports: [SeekTimerComponent, SongLoadingPanelComponent],
  templateUrl: './admin-song-round.page.html',
  styleUrl: './admin-song-round.page.scss',
})
export class AdminSongRoundPage implements OnInit, OnDestroy {
  readonly session = inject(GameSession);
  readonly store = inject(SongRoundStore);
  readonly artwork = inject(SelectedAlbumArtworkService);
  readonly SongScenario = SongScenario;
  readonly albumImageFailed = signal(false);
  readonly remainingMilliseconds = signal(0);
  readonly remainingSeconds = computed(() => Math.ceil(this.remainingMilliseconds() / 1000));
  readonly timerDisplay = computed(() => {
    const ms = this.remainingMilliseconds();
    return `${Math.floor(ms / 1000)}.${String(ms % 1000).padStart(3, '0')}`;
  });
  readonly timerFraction = signal(1);
  /** Countdown fill transitions green -> yellow -> pink as the remaining time falls. */
  readonly timerColor = computed(() => {
    const fraction = Math.min(1, Math.max(0, this.timerFraction()));
    const upper = fraction >= 0.5;
    const from = upper ? [255, 206, 78] : [240, 0, 154];
    const to = upper ? [69, 189, 142] : [255, 206, 78];
    const blend = upper ? (fraction - 0.5) * 2 : fraction * 2;
    return `rgb(${from.map((channel, index) => Math.round(channel + (to[index]! - channel) * blend)).join(' ')})`;
  });
  readonly verdict = signal<SongJudgement | null>(null);

  private readonly router = inject(Router);
  private lastVerdictSequence = 0;
  private verdictTimeout?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      this.artwork.album();
      this.albumImageFailed.set(false);
    });
    // Show feedback only after the server confirms the Admin's judgement,
    // so the Admin and TV celebrate (or reject) the same answer event.
    effect(() => {
      const judgement = this.store.vm().lastJudgement;
      if (!judgement || judgement.sequence === this.lastVerdictSequence) return;
      this.lastVerdictSequence = judgement.sequence;
      this.verdict.set(judgement);
      if (this.verdictTimeout) clearTimeout(this.verdictTimeout);
      this.verdictTimeout = setTimeout(() => this.verdict.set(null), VERDICT_VISIBLE_MS);
    });
  }

  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router.navigate(['admin']);
      return;
    }
    this.store.connect(this.session.messages$, 'admin');
  }

  ngOnDestroy(): void {
    if (this.verdictTimeout) clearTimeout(this.verdictTimeout);
    this.store.disconnect();
  }

  answeringTeamColor(image: string | null | undefined): string {
    return teamIconColor(image ?? '') ?? '#a770ed';
  }

  onTimerProgress(elapsed: number, duration: number): void {
    // SeekTimer emits its initial time synchronously from ngOnChanges. Defer
    // updates so a child input initialization cannot mutate an already-checked
    // parent binding during the same Angular change-detection pass.
    queueMicrotask(() => {
      this.remainingMilliseconds.set(Math.ceil(Math.max(0, duration - elapsed) * 1000));
      this.timerFraction.set(duration > 0 ? Math.max(0, (duration - elapsed) / duration) : 0);
    });
  }
}
