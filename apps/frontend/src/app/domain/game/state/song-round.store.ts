import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, Subscription } from 'rxjs';
import { DelayApiService } from '../data-access/delay-api.service';
import { InterruptApiService } from '../data-access/interrupt-api.service';
import { ScheduleApiService } from '../data-access/schedule-api.service';
import { DefaultMessage } from '../messages/default.messages';
import { GameServerMessage } from '../messages/game-server-message.types';
import {
  S2AnswerMessage,
  S2ErrorSolvedMessage,
  S2PauseMessage,
  S2PlaybackEvent,
  S2SongRepeatMessage,
  S2WelcomeMessage,
} from '../messages/stage2.messages';
import { ClientSurface } from '../models/client-surface.model';
import { routeForStage } from '../models/game-stage.model';
import { TeamScore } from '../models/team-score.model';
import { Team, teamFromScore } from '../models/team.model';
import { coerceSongScenario, SongScenario } from './song-scenario';

export interface SongRoundState {
  readonly songId: string | null;
  readonly question: string | null;
  readonly answer: string | null;
  readonly answeringTeam: Team | null;
  readonly interruptId: string | null;
  readonly seek: number | null;
  readonly remaining: number | null;
  readonly answerDuration: number | null;
  readonly lastPlayedSong: string | null;
  readonly scenario: SongScenario;
  readonly teams: readonly TeamScore[];
  readonly bravo: TeamScore | null;
  readonly inTransit: boolean;
  readonly playbackDelayId: string | null;
  readonly playbackWaiting: boolean;
  readonly playbackError: string | null;
}

export interface SongRoundVm extends SongRoundState {
  readonly isPlaying: boolean;
  readonly isRevealed: boolean;
  readonly isFinishedUnrevealed: boolean;
  readonly isTeamAnswering: boolean;
  readonly isSystemError: boolean;
  readonly canShowScoreboard: boolean;
  readonly canRevealAnswer: boolean;
  readonly canResolveError: boolean;
}

function createInitialSongRoundState(): SongRoundState {
  return {
    songId: null,
    question: null,
    answer: null,
    answeringTeam: null,
    interruptId: null,
    seek: null,
    remaining: null,
    answerDuration: null,
    lastPlayedSong: null,
    scenario: SongScenario.Loading,
    teams: [],
    bravo: null,
    inTransit: false,
    playbackDelayId: null,
    playbackWaiting: true,
    playbackError: null,
  };
}

@Injectable({ providedIn: 'root' })
export class SongRoundStore {
  private readonly router = inject(Router);
  private readonly interruptApi = inject(InterruptApiService);
  private readonly delayApi = inject(DelayApiService);
  private readonly scheduleApi = inject(ScheduleApiService);
  private readonly state = signal<SongRoundState>(createInitialSongRoundState());
  private sub?: Subscription;
  private surface: ClientSurface | null = null;
  private playbackEpoch = 0;
  private registration?: Promise<void>;
  private acknowledgement: Promise<void> = Promise.resolve();

  readonly vm = computed<SongRoundVm>(() => {
    const state = this.state();
    return {
      ...state,
      isPlaying: state.scenario === SongScenario.Playing,
      isRevealed: state.scenario === SongScenario.Revealed,
      isFinishedUnrevealed: state.scenario === SongScenario.FinishedUnrevealed,
      isTeamAnswering: state.scenario === SongScenario.TeamAnswering,
      isSystemError: state.scenario === SongScenario.SystemError,
      canShowScoreboard: state.teams.length > 0 && state.lastPlayedSong !== null,
      canRevealAnswer: state.scenario === SongScenario.FinishedUnrevealed && !state.inTransit,
      canResolveError: state.scenario === SongScenario.SystemError && !state.inTransit,
    };
  });

  connect(messages$: Observable<GameServerMessage>, surface: ClientSurface): void {
    this.sub?.unsubscribe();
    this.playbackEpoch++;
    this.registration = undefined;
    this.acknowledgement = Promise.resolve();
    this.surface = surface;
    this.sub = messages$.subscribe((message) => void this.handleMessage(message, surface));
  }

  disconnect(): void {
    this.sub?.unsubscribe();
    this.playbackEpoch++;
    this.registration = undefined;
    this.acknowledgement = Promise.resolve();
    this.sub = undefined;
    this.surface = null;
  }

  private patchState(patch: Partial<SongRoundState>): void {
    this.state.update((state) => ({ ...state, ...patch }));
  }

  private async handleMessage(message: DefaultMessage, surface: ClientSurface): Promise<void> {
    switch (message.type) {
      case 'song_next':
        this.handleSongNext(message as S2WelcomeMessage);
        break;
      case 'song_reveal':
        this.patchState({ scenario: SongScenario.Revealed });
        break;
      case 'song_repeat':
        this.handleSongRepeat(message as S2SongRepeatMessage);
        break;
      case 'answer':
        this.handleAnswer(message as S2AnswerMessage);
        break;
      case 'error_solved':
        this.handleErrorSolved(message as S2ErrorSolvedMessage);
        break;
      case 'pause':
        await this.handlePause(message as S2PauseMessage);
        break;
      case 'welcome':
        this.handleWelcome(message as S2WelcomeMessage, surface);
        break;
      case 'play':
        this.handlePlaybackEvent(message as S2PlaybackEvent);
        break;
    }
  }

  private handleSongNext(message: S2WelcomeMessage): void {
    this.playbackEpoch++;
    this.registration = undefined;
    this.patchState({
      songId: message.songId,
      question: message.question,
      answer: message.answer,
      seek: 0,
      remaining: message.remaining ?? null,
      answerDuration: message.answerDuration,
      lastPlayedSong: message.scheduleId,
      bravo: null,
      scenario: SongScenario.Playing,
      playbackDelayId: null,
      playbackWaiting: true,
      playbackError: null,
      answeringTeam: null,
      interruptId: null,
    });
  }

  private handleSongRepeat(message: S2SongRepeatMessage): void {
    this.playbackEpoch++;
    this.registration = undefined;
    this.patchState({
      seek: 0,
      remaining: message.remaining,
      playbackDelayId: null,
      playbackWaiting: true,
      playbackError: null,
      scenario: SongScenario.Playing,
    });
  }

  private handleAnswer(message: S2AnswerMessage): void {
    const teams = this.state().teams.map((team) =>
      team.teamId === message.teamId
        ? {
            ...team,
            scheduleId: message.scheduleId,
            score: team.score + (message.correct ? 30 : -10),
          }
        : team,
    );
    const answeredTeam = teams.find((team) => team.teamId === message.teamId) ?? null;

    this.patchState({
      teams,
      bravo: message.correct ? answeredTeam : null,
      scenario: message.correct ? SongScenario.Revealed : SongScenario.Playing,
      playbackDelayId: null,
      playbackWaiting: !message.correct,
    });
  }

  private handleErrorSolved(message: S2ErrorSolvedMessage): void {
    if (this.state().scenario === SongScenario.SystemError) {
      const scenario = coerceSongScenario(message.previousScenario);
      this.patchState({
        scenario,
        playbackDelayId: null,
        playbackWaiting: scenario === SongScenario.Playing,
      });
    }
  }

  private async handlePause(message: S2PauseMessage): Promise<void> {
    if (this.state().scenario === SongScenario.TeamAnswering) {
      return;
    }

    if (message.answeringTeamId === 'null') {
      await this.handleSystemPause();
      return;
    }

    const item = this.state().teams.find((team) => team.teamId === message.answeringTeamId);
    this.patchState({
      answeringTeam: item ? teamFromScore(item) : null,
      interruptId: message.interruptId,
      scenario: SongScenario.TeamAnswering,
    });
  }

  private async handleSystemPause(): Promise<void> {
    const previous = this.state().scenario;
    if (previous !== SongScenario.SystemError) {
      try {
        await firstValueFrom(this.interruptApi.savePrevScenario(previous));
      } catch {
        // TODO: replace with user-facing toast/error state when backend error handling is standardized.
      }
    }

    this.patchState({ scenario: SongScenario.SystemError });
  }

  private handleWelcome(message: S2WelcomeMessage, surface: ClientSurface): void {
    this.playbackEpoch++;
    this.registration = undefined;
    if (message.stage !== 'songs') {
      this.disconnect();
      void this.router.navigate(routeForStage(surface, message.stage));
      return;
    }

    const base: Partial<SongRoundState> = {
      songId: message.songId,
      question: message.question,
      answer: message.answer,
      lastPlayedSong: message.scheduleId,
      teams: message.scores,
      answerDuration: message.answerDuration,
      playbackDelayId: null,
      playbackWaiting: true,
      playbackError: null,
    };

    if (message.revealed != null) {
      this.patchState({
        ...base,
        bravo: message.revealed
          ? (message.scores.find((team) => team.teamId === message.bravo) ?? null)
          : null,
        scenario: message.revealed ? SongScenario.Revealed : SongScenario.FinishedUnrevealed,
      });
      return;
    }

    if (message.answeringTeam != null) {
      this.patchState({
        ...base,
        seek: message.seek ?? null,
        remaining: message.remaining ?? null,
        answeringTeam: message.answeringTeam,
        interruptId: message.interruptId ?? null,
        scenario: SongScenario.TeamAnswering,
      });
      return;
    }

    if (message.error != null) {
      this.patchState({
        ...base,
        seek: message.seek ?? null,
        remaining: message.remaining ?? null,
        scenario: SongScenario.SystemError,
      });
      return;
    }

    this.patchState({
      ...base,
      seek: message.seek ?? null,
      remaining: message.remaining ?? null,
      scenario: SongScenario.Playing,
    });
  }

  private handlePlaybackEvent(message: S2PlaybackEvent): void {
    if (
      this.surface === 'admin' &&
      message.scheduleId === this.state().lastPlayedSong &&
      this.state().scenario === SongScenario.Playing &&
      this.state().playbackWaiting
    ) {
      this.patchState({ playbackWaiting: false });
    }
  }

  /** Register loading once per TV attempt; parallel requests share the same registration. */
  requestPlayback(scheduleId: string): Promise<void> {
    if (
      this.surface !== 'tv' ||
      scheduleId !== this.state().lastPlayedSong ||
      this.state().scenario !== SongScenario.Playing ||
      this.state().playbackDelayId !== null
    ) {
      return Promise.resolve();
    }
    if (this.registration) return this.registration;
    const epoch = this.playbackEpoch;
    const priorAcknowledgement = this.acknowledgement;
    const registration = (async () => {
      // Keep registration serialized with any outstanding acknowledgement.
      await priorAcknowledgement.catch(() => {});
      if (
        this.playbackEpoch !== epoch ||
        this.surface !== 'tv' ||
        this.state().lastPlayedSong !== scheduleId ||
        this.state().scenario !== SongScenario.Playing
      )
        return;
      try {
        const delayId = await firstValueFrom(this.delayApi.register(scheduleId));
        if (
          this.surface === 'tv' &&
          this.playbackEpoch === epoch &&
          this.state().lastPlayedSong === scheduleId &&
          this.state().scenario === SongScenario.Playing
        ) {
          this.patchState({ playbackDelayId: delayId, playbackError: null });
        }
      } catch {
        if (this.playbackEpoch === epoch) {
          this.patchState({ playbackError: 'Could not register TV playback. Reconnect to retry.' });
        }
      }
    })();
    this.registration = registration;
    void registration.finally(() => {
      if (this.registration === registration) this.registration = undefined;
    });
    return registration;
  }

  /** Acknowledge only after registration and real audio-clock progress. */
  async acknowledgePlayback(): Promise<void> {
    if (this.surface !== 'tv' || this.state().scenario !== SongScenario.Playing) return;
    const epoch = this.playbackEpoch;
    const previous = this.acknowledgement;
    const task = (async () => {
      await previous.catch(() => {});
      if (this.registration) await this.registration;
      const delayId = this.state().playbackDelayId;
      if (
        this.surface !== 'tv' ||
        this.playbackEpoch !== epoch ||
        this.state().scenario !== SongScenario.Playing ||
        !delayId
      )
        return;
      try {
        await firstValueFrom(this.delayApi.acknowledge(delayId));
        if (this.playbackEpoch === epoch && this.state().playbackDelayId === delayId) {
          this.patchState({ playbackError: null });
        }
      } catch {
        if (this.playbackEpoch === epoch) {
          this.patchState({ playbackError: 'Could not acknowledge TV playback.' });
        }
      }
    })();
    this.acknowledgement = task;
    await task;
  }

  playbackFailed(): void {
    this.patchState({
      playbackError: 'Audio playback could not start. Check the TV audio device.',
    });
  }

  /** Each client finishes its own snippet; only the TV acknowledges playback start. */
  onTrackFinished(): void {
    if (
      this.state().scenario !== SongScenario.Playing ||
      (this.surface === 'admin' && this.state().playbackWaiting)
    )
      return;
    this.patchState({ scenario: SongScenario.FinishedUnrevealed });
  }

  onAnswerAudioFinished(): void {
    // Original TV behavior does not change stage after answer audio ends.
  }

  savePlaybackState(state: { seek: number; remaining: number; scheduleId?: string | null }): void {
    if (state.scheduleId && state.scheduleId !== this.state().lastPlayedSong) return;
    this.patchState({
      seek: state.seek,
      remaining: state.remaining,
    });
  }

  async resolveError(): Promise<void> {
    const scheduleId = this.state().lastPlayedSong;
    if (!scheduleId || this.state().inTransit) {
      return;
    }

    this.patchState({ inTransit: true });
    try {
      await firstValueFrom(this.interruptApi.resolveErrors(scheduleId));
    } finally {
      this.patchState({ inTransit: false });
    }
  }

  async teamAnswered(correct: boolean): Promise<void> {
    const interruptId = this.state().interruptId;
    if (!interruptId || this.state().inTransit) {
      return;
    }

    this.patchState({ inTransit: true });
    try {
      await firstValueFrom(this.interruptApi.answer(interruptId, correct));
    } finally {
      this.patchState({ inTransit: false });
    }
  }

  async advanceGame(repeatSong: boolean): Promise<void> {
    const scheduleId = this.state().lastPlayedSong;
    if (
      this.state().scenario !== SongScenario.FinishedUnrevealed ||
      this.state().inTransit ||
      !scheduleId
    ) {
      return;
    }

    this.patchState({ inTransit: true });
    try {
      if (repeatSong) {
        await firstValueFrom(this.scheduleApi.replaySong(scheduleId));
      } else {
        await firstValueFrom(this.scheduleApi.revealAnswer(scheduleId));
      }
    } finally {
      this.patchState({ inTransit: false });
    }
  }

  async nextSong(): Promise<void> {
    if (this.state().inTransit) {
      return;
    }

    this.patchState({ inTransit: true });
    try {
      await firstValueFrom(this.scheduleApi.next());
    } finally {
      this.patchState({ inTransit: false });
    }
  }
}
