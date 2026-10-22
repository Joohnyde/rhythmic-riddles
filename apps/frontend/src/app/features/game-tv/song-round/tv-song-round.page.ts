import {
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { interval, Subscription } from 'rxjs';

import { GameSession } from '../../../core/session/game-session.service';
import { AudioAssetUrlService } from '../../../domain/game/data-access/audio-asset-url.service';
import { TeamScore } from '../../../domain/game/models/team-score.model';
import { teamIconColor } from '../../../domain/game/models/team-icon.utils';
import { SongScenario } from '../../../domain/game/state/song-scenario';
import { SongRoundStore, SongRoundVm } from '../../../domain/game/state/song-round.store';
import { AudioPlayerComponent } from '../../../shared/ui/audio-player/audio-player.component';
import { SelectedAlbumArtworkService } from '../../../shared/ui/song-round-presentation/selected-album-artwork.service';
import { SongRevealedPanelComponent } from '../../../shared/ui/song-round-panels/song-revealed-panel/song-revealed-panel.component';

interface ScoreChange {
  readonly teamId: string;
  readonly amount: number;
}

interface ScoreAnimation {
  readonly teamId: string;
  readonly from: number;
  readonly to: number;
  readonly startedAt: number;
}

const CELEBRATION_MS = 3200;
const SCORE_CHANGE_MS = 2300;
const SCORE_COUNT_MS = 1800;

@Component({
  selector: 'rr-tv-song-round-page',
  imports: [AudioPlayerComponent, SongRevealedPanelComponent],
  templateUrl: './tv-song-round.page.html',
  styleUrl: './tv-song-round.page.scss',
})
export class TvSongRoundPage implements OnInit, OnDestroy {
  @ViewChild(AudioPlayerComponent) private player?: AudioPlayerComponent;

  readonly session = inject(GameSession);
  readonly store = inject(SongRoundStore);
  readonly audioUrls = inject(AudioAssetUrlService);
  readonly artwork = inject(SelectedAlbumArtworkService);
  readonly SongScenario = SongScenario;

  readonly secondsRemaining = signal(0);
  readonly countdownFraction = signal(1);
  readonly celebratedTeamId = signal<string | null>(null);
  readonly scoreChange = signal<ScoreChange | null>(null);
  readonly displayedScores = signal<ReadonlyMap<string, number>>(new Map());
  readonly albumImageFailed = signal(false);

  private readonly router = inject(Router);
  private readonly tickSubscription = new Subscription();
  private celebrationTimeout?: ReturnType<typeof setTimeout>;
  private scoreTimeout?: ReturnType<typeof setTimeout>;
  private scoreAnimation?: ScoreAnimation;
  private scheduleId: string | null = null;
  private celebratedKey: string | null = null;
  private knownScores = new Map<string, number>();
  private scoreSnapshotReady = false;

  constructor() {
    effect(() => this.synchronizePresentation(this.store.vm()));
    effect(() => {
      this.artwork.album();
      this.albumImageFailed.set(false);
    });
  }

  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router.navigate(['']);
      return;
    }

    this.store.connect(this.session.messages$, 'tv');
    this.tickSubscription.add(
      interval(50).subscribe(() => {
        this.refreshClock();
        this.refreshScoreAnimation();
      }),
    );
  }

  ngOnDestroy(): void {
    this.tickSubscription.unsubscribe();
    this.cancelCelebration();
    this.cancelScoreChange();
    this.store.disconnect();
  }

  /** Preserve the ordering supplied by the game server. */
  leftTeams(teams: readonly TeamScore[]): readonly TeamScore[] {
    return teams.slice(0, Math.ceil(teams.length / 2));
  }

  rightTeams(teams: readonly TeamScore[]): readonly TeamScore[] {
    return teams.slice(Math.ceil(teams.length / 2));
  }

  displayedScore(team: TeamScore): number {
    return this.displayedScores().get(team.teamId) ?? team.score;
  }

  /** Density is determined by the fuller column, not total participant count. */
  teamDensity(teams: readonly TeamScore[]): 'sparse' | 'normal' | 'medium' | 'compact' {
    // Keep small games visually balanced: four or fewer teams need smaller cards.
    if (teams.length <= 4) return 'sparse';
    const perSide = Math.ceil(teams.length / 2);
    if (perSide >= 8) return 'compact';
    return perSide >= 6 ? 'medium' : 'normal';
  }

  hasFourDigitScore(team: TeamScore): boolean {
    return Math.abs(this.displayedScore(team)) >= 1000;
  }

  teamColor(team: TeamScore): string {
    return teamIconColor(team.image) ?? '#a770ed';
  }

  isAnswering(team: TeamScore, vm: SongRoundVm): boolean {
    return vm.scenario === SongScenario.TeamAnswering && vm.answeringTeam?.id === team.teamId;
  }

  isCorrect(team: TeamScore, vm: SongRoundVm): boolean {
    return vm.bravo?.teamId === team.teamId && team.scheduleId === vm.lastPlayedSong;
  }

  isWrong(team: TeamScore, vm: SongRoundVm): boolean {
    // A previous incorrect attempt must not gray the team when it buzzes again.
    // The active answering state takes priority over the historical wrong state.
    return (
      !this.isAnswering(team, vm) &&
      team.scheduleId !== null &&
      team.scheduleId === vm.lastPlayedSong &&
      vm.bravo?.teamId !== team.teamId
    );
  }

  private synchronizePresentation(vm: SongRoundVm): void {
    if (vm.lastPlayedSong !== this.scheduleId) {
      this.scheduleId = vm.lastPlayedSong;
      this.celebratedKey = null;
      this.cancelCelebration();
      this.cancelScoreChange();
      this.knownScores = new Map(vm.teams.map((team) => [team.teamId, team.score]));
      this.scoreSnapshotReady = vm.teams.length > 0;
      this.secondsRemaining.set(Math.ceil(Math.max(0, vm.remaining ?? 0)));
      this.countdownFraction.set(1);
      return;
    }

    if (vm.teams.length > 0) {
      if (this.scoreSnapshotReady) {
        const changed = vm.teams.find(
          (team) =>
            this.knownScores.has(team.teamId) && this.knownScores.get(team.teamId) !== team.score,
        );
        if (changed) {
          this.showScoreChange(
            changed.teamId,
            this.knownScores.get(changed.teamId)!,
            changed.score,
          );
        }
      }
      this.knownScores = new Map(vm.teams.map((team) => [team.teamId, team.score]));
      this.scoreSnapshotReady = true;
    }

    if (vm.bravo && vm.lastPlayedSong) {
      const key = `${vm.lastPlayedSong}:${vm.bravo.teamId}`;
      if (key !== this.celebratedKey) {
        this.celebratedKey = key;
        this.cancelCelebration();
        this.celebratedTeamId.set(vm.bravo.teamId);
        this.celebrationTimeout = setTimeout(() => this.celebratedTeamId.set(null), CELEBRATION_MS);
      }
    }

    if (vm.scenario !== SongScenario.Playing && vm.remaining !== null) {
      this.secondsRemaining.set(Math.ceil(Math.max(0, vm.remaining)));
    }
  }

  private refreshClock(): void {
    const vm = this.store.vm();
    if (vm.scenario !== SongScenario.Playing || !this.player) return;

    // The existing audio player is the source of truth; no independent timer
    // can drift while a team answers or after restoring the saved seek offset.
    const duration = this.player.totalDuration;
    const elapsed = this.player.audio.getCurrentTime();
    const remaining = Math.max(0, duration - elapsed);
    this.secondsRemaining.set(Math.ceil(remaining));
    this.countdownFraction.set(duration > 0 ? remaining / duration : 0);
  }

  private showScoreChange(teamId: string, from: number, to: number): void {
    this.cancelScoreChange();
    this.scoreChange.set({ teamId, amount: to - from });
    // Animate the cumulative total, never the award by itself: 20 + 30 -> 50.
    this.scoreAnimation = { teamId, from, to, startedAt: Date.now() };
    this.displayedScores.set(new Map([[teamId, from]]));
    this.scoreTimeout = setTimeout(() => this.scoreChange.set(null), SCORE_CHANGE_MS);
  }

  private refreshScoreAnimation(): void {
    const animation = this.scoreAnimation;
    if (!animation) return;

    const progress = Math.min(1, (Date.now() - animation.startedAt) / SCORE_COUNT_MS);
    const eased = 1 - Math.pow(1 - progress, 3);
    const value = Math.round(animation.from + (animation.to - animation.from) * eased);
    if (progress === 1) {
      this.scoreAnimation = undefined;
      this.displayedScores.set(new Map()); // Read the store's authoritative final total.
    } else if (this.displayedScores().get(animation.teamId) !== value) {
      this.displayedScores.set(new Map([[animation.teamId, value]]));
    }
  }

  private cancelCelebration(): void {
    if (this.celebrationTimeout) clearTimeout(this.celebrationTimeout);
    this.celebrationTimeout = undefined;
    this.celebratedTeamId.set(null);
  }

  private cancelScoreChange(): void {
    if (this.scoreTimeout) clearTimeout(this.scoreTimeout);
    this.scoreTimeout = undefined;
    this.scoreAnimation = undefined;
    this.displayedScores.set(new Map());
    this.scoreChange.set(null);
  }
}
