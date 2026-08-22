import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, Subscription } from 'rxjs';
import { GameSession } from '../../../core/session/game-session.service';
import { TEAM_ICONS } from '../generated/team-icons.generated';
import { GameApiService } from '../data-access/game-api.service';
import { CreateTeamRequest, TeamApiService } from '../data-access/team-api.service';
import { DefaultMessage } from '../messages/default.messages';
import { GameServerMessage } from '../messages/game-server-message.types';
import {
  S0ButtonClickedMessage,
  S0KickTeamMessage,
  S0NewTeamMessage,
  S0WelcomeMessage,
} from '../messages/stage0.messages';
import { ClientSurface } from '../models/client-surface.model';
import { GameStageId } from '../models/game-stage-id.model';
import { routeForStage } from '../models/game-stage.model';
import { Team } from '../models/team.model';

const BUZZER_DOUBLE_CLICK_MS = 250;

export type LobbyTeamDraft = CreateTeamRequest;

interface BuzzerCandidate {
  readonly code: string;
  readonly pressedAt: number;
}

interface LobbyState {
  readonly teams: readonly Team[];
  readonly inTransit: boolean;
  readonly draft: LobbyTeamDraft;
  readonly buzzerCandidate?: BuzzerCandidate;
  readonly buzzerPulseSequence: number;
}

interface LobbyVm {
  readonly teams: readonly Team[];
  readonly inTransit: boolean;
  readonly draft: LobbyTeamDraft;
  readonly buzzerPulseSequence: number;
  readonly roomCode: string;
  readonly canAddTeam: boolean;
  readonly canStartGame: boolean;
  readonly availableIcons: readonly string[];
}

function availableIcons(teams: readonly Team[]): readonly string[] {
  const usedIcons = new Set(teams.map((team) => team.image));
  return TEAM_ICONS.filter((icon) => !usedIcons.has(icon));
}

function emptyDraft(teams: readonly Team[]): LobbyTeamDraft {
  return {
    name: '',
    image: availableIcons(teams)[0] ?? '',
    buttonCode: '',
  };
}

function createInitialLobbyState(): LobbyState {
  return {
    teams: [],
    inTransit: false,
    draft: emptyDraft([]),
    buzzerPulseSequence: 0,
  };
}

@Injectable({ providedIn: 'root' })
export class LobbyStore {
  private readonly router = inject(Router);
  private readonly teamsApi = inject(TeamApiService);
  private readonly gameApi = inject(GameApiService);
  private readonly session = inject(GameSession);
  private readonly state = signal<LobbyState>(createInitialLobbyState());
  private subscription?: Subscription;

  readonly vm = computed<LobbyVm>(() => {
    const state = this.state();
    const icons = availableIcons(state.teams);
    const draftName = state.draft.name.trim();
    return {
      teams: state.teams,
      inTransit: state.inTransit,
      draft: state.draft,
      buzzerPulseSequence: state.buzzerPulseSequence,
      roomCode: this.session.code,
      availableIcons: icons,
      canAddTeam:
        !state.inTransit &&
        !!draftName &&
        !!state.draft.buttonCode &&
        !!state.draft.image &&
        icons.includes(state.draft.image),
      canStartGame: state.teams.length > 0 && !state.inTransit,
    };
  });

  connect(messages$: Observable<GameServerMessage>, surface: ClientSurface): void {
    this.subscription?.unsubscribe();
    this.state.set(createInitialLobbyState());
    this.subscription = messages$.subscribe((message) => this.handleMessage(message, surface));
  }

  disconnect(): void {
    this.subscription?.unsubscribe();
    this.subscription = undefined;
  }

  setDraftName(name: string): void {
    this.state.update((state) => ({
      ...state,
      draft: { ...state.draft, name },
      buzzerCandidate: name.trim() ? state.buzzerCandidate : undefined,
    }));
  }

  selectNextIcon(): void {
    this.state.update((state) => {
      const icons = availableIcons(state.teams);
      if (icons.length === 0) {
        return { ...state, draft: { ...state.draft, image: '' } };
      }

      const currentIndex = icons.indexOf(state.draft.image);
      const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % icons.length;
      return { ...state, draft: { ...state.draft, image: icons[nextIndex] } };
    });
  }

  clearDraft(): void {
    this.state.update((state) => ({
      ...state,
      draft: emptyDraft(state.teams),
      buzzerCandidate: undefined,
    }));
  }

  async createTeam(): Promise<boolean> {
    const state = this.state();
    const request: CreateTeamRequest = {
      ...state.draft,
      name: state.draft.name.trim(),
      buttonCode: state.draft.buttonCode,
    };
    if (
      state.inTransit ||
      !request.name ||
      !request.image ||
      !request.buttonCode ||
      !availableIcons(state.teams).includes(request.image)
    ) {
      return false;
    }

    this.patchState({ inTransit: true });
    try {
      const team = await firstValueFrom(this.teamsApi.createTeam(request));
      const teams = this.appendUniqueTeam(this.state().teams, team);
      this.patchState({
        teams,
        draft: emptyDraft(teams),
        buzzerCandidate: undefined,
      });
      return true;
    } finally {
      this.patchState({ inTransit: false });
    }
  }

  async kickTeam(teamId: string): Promise<void> {
    if (this.state().inTransit) {
      return;
    }

    this.patchState({ inTransit: true });
    try {
      await firstValueFrom(this.teamsApi.kickTeam(teamId));
      this.setTeams(this.state().teams.filter((team) => team.id !== teamId));
    } finally {
      this.patchState({ inTransit: false });
    }
  }

  async startAdminGame(): Promise<void> {
    if (this.state().inTransit || this.state().teams.length === 0) {
      return;
    }

    this.patchState({ inTransit: true });
    try {
      await firstValueFrom(this.gameApi.changeState(GameStageId.Albums));
      void this.router.navigate(['admin', 'albums']);
    } finally {
      this.patchState({ inTransit: false });
    }
  }

  private patchState(patch: Partial<LobbyState>): void {
    this.state.update((state) => ({ ...state, ...patch }));
  }

  private handleMessage(message: DefaultMessage, surface: ClientSurface): void {
    switch (message.type) {
      case 'welcome':
        this.handleWelcome(message as S0WelcomeMessage, surface);
        break;
      case 'new_team':
        this.setTeams(
          this.appendUniqueTeam(this.state().teams, (message as S0NewTeamMessage).team),
        );
        break;
      case 'kick_team':
        this.setTeams(
          this.state().teams.filter((team) => team.id !== (message as S0KickTeamMessage).uuid),
        );
        break;
      case 'button_clicked':
        if (surface === 'admin') {
          this.handleButtonClicked(message as S0ButtonClickedMessage);
        }
        break;
    }
  }

  private handleWelcome(message: S0WelcomeMessage, surface: ClientSurface): void {
    if (message.stage === 'lobby') {
      this.setTeams(message.teams);
      return;
    }

    this.disconnect();
    void this.router.navigate(routeForStage(surface, message.stage));
  }

  private handleButtonClicked(message: S0ButtonClickedMessage): void {
    const buttonCode = message.buttonCode;
    if (!buttonCode) {
      return;
    }

    const state = this.state();
    const now = Date.now();
    if (state.draft.buttonCode) {
      if (state.draft.buttonCode === buttonCode) {
        this.patchState({ buzzerPulseSequence: state.buzzerPulseSequence + 1 });
      }
      return;
    }

    if (!state.draft.name.trim()) {
      return;
    }

    const candidate = state.buzzerCandidate;
    if (
      !candidate ||
      candidate.code !== buttonCode ||
      now - candidate.pressedAt > BUZZER_DOUBLE_CLICK_MS
    ) {
      this.patchState({ buzzerCandidate: { code: buttonCode, pressedAt: now } });
      return;
    }

    this.patchState({
      draft: { ...state.draft, buttonCode },
      buzzerCandidate: undefined,
      buzzerPulseSequence: state.buzzerPulseSequence + 1,
    });
  }

  private setTeams(teams: readonly Team[]): void {
    this.state.update((state) => {
      const icons = availableIcons(teams);
      const draftImage = icons.includes(state.draft.image) ? state.draft.image : (icons[0] ?? '');
      return {
        ...state,
        teams,
        draft: { ...state.draft, image: draftImage },
      };
    });
  }

  private appendUniqueTeam(teams: readonly Team[], team: Team): readonly Team[] {
    return teams.some((existing) => existing.id === team.id) ? teams : [...teams, team];
  }
}
