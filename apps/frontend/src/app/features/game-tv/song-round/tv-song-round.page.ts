import { Component, OnDestroy, OnInit, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { interval, Subscription } from 'rxjs';
import { GameSession } from '../../../core/session/game-session.service';
import { AudioAssetUrlService } from '../../../domain/game/data-access/audio-asset-url.service';
import { TeamScore } from '../../../domain/game/models/team-score.model';
import { teamIconColor } from '../../../domain/game/models/team-icon.utils';
import { SongRoundStore, SongRoundVm } from '../../../domain/game/state/song-round.store';
import { SongRoundAlbumComponent } from '../../../shared/ui/song-round-album/song-round-album.component';
import { SongScenario } from '../../../domain/game/state/song-scenario';
import {
  AudioPlayerComponent,
  type AudioPlaybackProgress,
} from '../../../shared/ui/audio-player/audio-player.component';
import { SongRevealedPanelComponent } from '../../../shared/ui/song-round-panels/song-revealed-panel/song-revealed-panel.component';
import { createAnswerFeedback } from '../../../shared/ui/song-round-presentation/song-round-presentation';
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
  imports: [SongRoundAlbumComponent, AudioPlayerComponent, SongRevealedPanelComponent],
  templateUrl: './tv-song-round.page.html',
  styleUrls: ['./tv-song-round-teams.scss', './tv-song-round.page.scss'],
})
export class TvSongRoundPage implements OnInit, OnDestroy {
  readonly session = inject(GameSession);
  readonly store = inject(SongRoundStore);
  readonly audioUrls = inject(AudioAssetUrlService);
  readonly SongScenario = SongScenario;
  readonly verdict = createAnswerFeedback(this.session.messages$, this.store.vm);
  readonly secondsRemaining = signal(0);
  readonly countdownFraction = signal(1);

  private readonly router = inject(Router);
  private readonly teams = computed(() => this.store.vm().teams);
  private readonly frameTime = signal(Date.now());
  private readonly scoreAnimation = signal<ScoreAnimation | null>(null);
  private readonly celebration = signal<{ teamId: string; startedAt: number } | null>(null);
  private previousRound?: SongRoundVm;
  private ticks?: Subscription;

  readonly teamColumns = computed(() => {
    const teams = this.teams();
    const half = Math.ceil(teams.length / 2);
    return [teams.slice(0, half), teams.slice(half)] as const;
  });

  readonly teamDensity = computed(() => {
    const teamsPerSide = this.teamColumns()[0].length;
    if (teamsPerSide <= 2) return 'sparse';
    if (teamsPerSide >= 8) return 'compact';
    return teamsPerSide >= 6 ? 'medium' : 'normal';
  });

  readonly scoreChange = computed(() => {
    const animation = this.scoreAnimation();
    if (!animation) return null;

    const elapsed = Math.max(0, this.frameTime() - animation.startedAt);
    if (elapsed >= SCORE_CHANGE_MS) return null;

    const amount = animation.to - animation.from;
    const progress = Math.min(1, elapsed / SCORE_COUNT_MS);
    const eased = 1 - (1 - progress) ** 3;
    return { teamId: animation.teamId, amount, score: Math.round(animation.from + amount * eased) };
  });

  readonly celebratedTeamId = computed(() => {
    const celebration = this.celebration();
    return celebration && this.frameTime() - celebration.startedAt < CELEBRATION_MS
      ? celebration.teamId
      : null;
  });

  constructor() {
    effect(() => {
      const vm = this.store.vm();
      this.updateScoreAnimations(vm);
      this.resetClock(vm);
    });
  }

  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router.navigate(['']);
      return;
    }

    this.store.connect(this.session.messages$, 'tv');
    this.ticks = interval(50).subscribe(() => this.frameTime.set(Date.now()));
  }

  ngOnDestroy(): void {
    this.ticks?.unsubscribe();
    this.store.disconnect();
  }

  teamColor(image: string): string {
    return teamIconColor(image) ?? 'var(--primary)';
  }

  isAnswering(team: TeamScore, vm: SongRoundVm): boolean {
    return vm.scenario === SongScenario.TeamAnswering && vm.answeringTeam?.id === team.teamId;
  }

  isCorrect(team: TeamScore, vm: SongRoundVm): boolean {
    return vm.bravo?.teamId === team.teamId && team.scheduleId === vm.lastPlayedSong;
  }

  isWrong(team: TeamScore, vm: SongRoundVm): boolean {
    return (
      !this.isAnswering(team, vm) &&
      team.scheduleId !== null &&
      team.scheduleId === vm.lastPlayedSong &&
      !this.isCorrect(team, vm)
    );
  }

  private updateScoreAnimations(vm: SongRoundVm): void {
    const previous = this.previousRound;
    this.previousRound = vm;

    if (!previous || previous.lastPlayedSong !== vm.lastPlayedSong) {
      this.scoreAnimation.set(null);
      this.celebration.set(null);
      return;
    }

    if (vm.bravo && vm.bravo.teamId !== previous.bravo?.teamId) {
      this.celebration.set({ teamId: vm.bravo.teamId, startedAt: Date.now() });
    }

    if (previous.teams === vm.teams) return;

    for (const team of vm.teams) {
      const priorTeam = previous.teams.find(({ teamId }) => teamId === team.teamId);
      if (priorTeam && priorTeam.score !== team.score) {
        this.scoreAnimation.set({
          teamId: team.teamId,
          from: priorTeam.score,
          to: team.score,
          startedAt: Date.now(),
        });
        break;
      }
    }
  }

  updatePlaybackProgress(progress: AudioPlaybackProgress): void {
    const vm = this.store.vm();
    if (!vm.isPlaying || progress.scheduleId !== vm.lastPlayedSong) return;
    this.updateClock(progress.remaining, progress.duration);
  }

  private resetClock(vm: SongRoundVm): void {
    const remaining = vm.remaining ?? 0;
    const duration = (vm.seek ?? 0) + remaining;
    this.updateClock(remaining, duration, vm.remaining === null);
  }

  private updateClock(remaining: number, duration: number, pending = false): void {
    this.secondsRemaining.set(Math.ceil(remaining));
    this.countdownFraction.set(duration > 0 ? remaining / duration : pending ? 1 : 0);
  }
}
