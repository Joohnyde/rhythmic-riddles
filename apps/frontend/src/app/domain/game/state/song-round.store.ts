import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, Subscription } from 'rxjs';
import { ClientSurface } from '../../../core/realtime/game-realtime.service';
import { InterruptApiService } from '../data-access/interrupt-api.service';
import { ScheduleApiService } from '../data-access/schedule-api.service';
import { DefaultMessage } from '../messages/default.messages';
import {
  S2AnswerMessage,
  S2ErrorSolvedMessage,
  S2PauseMessage,
  S2SongRepeatMessage,
  S2WelcomeMessage,
} from '../messages/stage2.messages';
import { TeamScore } from '../models/team-score.model';
import { Team } from '../models/team.model';
import { coerceSongScenario, SongScenario } from './song-scenario';

export interface SongRoundState {
  songId: string | null;
  question: string | null;
  answer: string | null;
  answeringTeam: Team | null;
  interruptId: string | null;
  seek: number | null;
  remaining: number | null;
  answerDuration: number | null;
  lastPlayedSong: string | null;
  scenario: SongScenario;
  teams: TeamScore[];
  bravo: TeamScore | null;
  inTransit: boolean;
}
const initialState: SongRoundState = {
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
};
@Injectable({ providedIn: 'root' })
export class SongRoundStore {
  private readonly router = inject(Router);
  private readonly interruptApi = inject(InterruptApiService);
  private readonly scheduleApi = inject(ScheduleApiService);
  private sub?: Subscription;
  private readonly state = signal<SongRoundState>(initialState);
  readonly vm = computed(() => this.state());
  connect(messages$: Observable<DefaultMessage>, surface: ClientSurface): void {
    this.sub?.unsubscribe();
    this.sub = messages$.subscribe((m) => void this.handleMessage(m, surface));
  }
  disconnect(): void {
    this.sub?.unsubscribe();
    this.sub = undefined;
  }
  reset(): void {
    this.state.set(initialState);
  }
  private patch(p: Partial<SongRoundState>) {
    this.state.update((s) => ({ ...s, ...p }));
  }
  private async handleMessage(message: DefaultMessage, surface: ClientSurface): Promise<void> {
    switch (message.type) {
      case 'song_next':
        this.handleSongNext(message as S2WelcomeMessage);
        break;
      case 'song_reveal':
        this.patch({ scenario: SongScenario.Revealed });
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
    }
  }
  private handleSongNext(message: S2WelcomeMessage): void {
    this.patch({
      songId: message.songId,
      question: message.question,
      answer: message.answer,
      seek: 0,
      remaining: message.remaining ?? null,
      answerDuration: message.answerDuration,
      lastPlayedSong: message.scheduleId,
      bravo: null,
      scenario: SongScenario.Playing,
      answeringTeam: null,
      interruptId: null,
    });
  }
  private handleSongRepeat(message: S2SongRepeatMessage): void {
    this.patch({ seek: 0, remaining: message.remaining, scenario: SongScenario.Playing });
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
    this.patch({
      teams,
      bravo: message.correct ? answeredTeam : null,
      scenario: message.correct ? SongScenario.Revealed : SongScenario.Playing,
    });
  }
  private handleErrorSolved(message: S2ErrorSolvedMessage): void {
    if (this.state().scenario === SongScenario.SystemError)
      this.patch({ scenario: coerceSongScenario(message.previousScenario) });
  }
  private async handlePause(message: S2PauseMessage): Promise<void> {
    if (this.state().scenario === SongScenario.TeamAnswering) return;
    if (message.answeringTeamId === 'null') {
      await this.handleSystemPause();
      return;
    }
    const item = this.state().teams.find((x) => x.teamId === message.answeringTeamId);
    this.patch({
      answeringTeam: item ? new Team(item) : null,
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
        /* TODO: toast */
      }
    }
    this.patch({ scenario: SongScenario.SystemError });
  }
  private handleWelcome(message: S2WelcomeMessage, surface: ClientSurface): void {
    if (message.stage !== 'songs') {
      this.disconnect();
      this.router.navigate(surface === 'admin' ? ['admin', message.stage] : [message.stage]);
      return;
    }
    const base: Partial<SongRoundState> = {
      songId: message.songId,
      question: message.question,
      answer: message.answer,
      lastPlayedSong: message.scheduleId,
      teams: message.scores,
      answerDuration: message.answerDuration,
    };
    if (message.revealed != null) {
      this.patch({
        ...base,
        bravo: message.revealed
          ? (message.scores.find((t) => t.teamId === message.bravo) ?? null)
          : null,
        scenario: message.revealed ? SongScenario.Revealed : SongScenario.FinishedUnrevealed,
      });
      return;
    }
    if (message.answeringTeam != null) {
      this.patch({
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
      this.patch({
        ...base,
        seek: message.seek ?? null,
        remaining: message.remaining ?? null,
        scenario: SongScenario.SystemError,
      });
      return;
    }
    this.patch({
      ...base,
      seek: message.seek ?? null,
      remaining: message.remaining ?? null,
      scenario: SongScenario.Playing,
    });
  }
  onTrackFinished(): void {
    if (this.state().scenario === SongScenario.Playing)
      this.patch({ scenario: SongScenario.FinishedUnrevealed });
  }
  onAnswerAudioFinished(): void {
    /* original TV behavior does not change stage after answer audio ends */
  }
  savePlaybackState(state: { seek: number; remaining: number }): void {
    this.patch({ seek: state.seek, remaining: state.remaining });
  }
  async resolveError(): Promise<void> {
    const scheduleId = this.state().lastPlayedSong;
    if (!scheduleId || this.state().inTransit) return;
    this.patch({ inTransit: true });
    try {
      await firstValueFrom(this.interruptApi.resolveErrors(scheduleId));
    } finally {
      this.patch({ inTransit: false });
    }
  }
  async teamAnswered(correct: boolean): Promise<void> {
    const interruptId = this.state().interruptId;
    if (!interruptId || this.state().inTransit) return;
    this.patch({ inTransit: true });
    try {
      await firstValueFrom(this.interruptApi.answer(interruptId, correct));
    } finally {
      this.patch({ inTransit: false });
    }
  }
  async advanceGame(repeatSong: boolean): Promise<void> {
    const scheduleId = this.state().lastPlayedSong;
    if (
      this.state().scenario !== SongScenario.FinishedUnrevealed ||
      this.state().inTransit ||
      !scheduleId
    )
      return;
    this.patch({ inTransit: true });
    try {
      if (repeatSong) await firstValueFrom(this.scheduleApi.replaySong(scheduleId));
      else await firstValueFrom(this.scheduleApi.revealAnswer(scheduleId));
    } finally {
      this.patch({ inTransit: false });
    }
  }
  async nextSong(): Promise<void> {
    if (this.state().inTransit) return;
    this.patch({ inTransit: true });
    try {
      await firstValueFrom(this.scheduleApi.next());
    } finally {
      this.patch({ inTransit: false });
    }
  }
}
