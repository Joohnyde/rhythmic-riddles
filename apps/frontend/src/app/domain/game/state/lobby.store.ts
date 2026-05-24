import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, Subscription } from 'rxjs';
import { GameApiService } from '../data-access/game-api.service';
import { TeamApiService } from '../data-access/team-api.service';
import { DefaultMessage } from '../messages/default.messages';
import { GameServerMessage } from '../messages/game-server-message.types';
import { S0KickTeamMessage, S0NewTeamMessage, S0WelcomeMessage } from '../messages/stage0.messages';
import { ClientSurface } from '../models/client-surface.model';
import { GameStageId } from '../models/game-stage-id.model';
import { routeForStage } from '../models/game-stage.model';
import { Team } from '../models/team.model';

interface LobbyState {
  readonly teams: readonly Team[];
  readonly inTransit: boolean;
}

interface LobbyVm extends LobbyState {
  readonly canStartGame: boolean;
}

function createInitialLobbyState(): LobbyState {
  return {
    teams: [],
    inTransit: false,
  };
}

@Injectable({ providedIn: 'root' })
export class LobbyStore {
  private readonly router = inject(Router);
  private readonly teamsApi = inject(TeamApiService);
  private readonly gameApi = inject(GameApiService);
  private readonly state = signal<LobbyState>(createInitialLobbyState());
  private sub?: Subscription;

  readonly vm = computed<LobbyVm>(() => {
    const state = this.state();
    return {
      ...state,
      canStartGame: state.teams.length > 0 && !state.inTransit,
    };
  });

  connect(messages$: Observable<GameServerMessage>, surface: ClientSurface): void {
    this.sub?.unsubscribe();
    this.sub = messages$.subscribe((message) => this.handleMessage(message, surface));
  }

  disconnect(): void {
    this.sub?.unsubscribe();
    this.sub = undefined;
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
        this.patchState({ teams: [...this.state().teams, (message as S0NewTeamMessage).team] });
        break;
      case 'kick_team':
        this.patchState({
          teams: this.state().teams.filter(
            (team) => team.id !== (message as S0KickTeamMessage).uuid,
          ),
        });
        break;
    }
  }

  private handleWelcome(message: S0WelcomeMessage, surface: ClientSurface): void {
    if (message.stage === 'lobby') {
      this.patchState({ teams: message.teams });
      return;
    }

    this.disconnect();
    void this.router.navigate(routeForStage(surface, message.stage));
  }

  async createTeam(form: { name: string; image: string; buttonCode: string }): Promise<void> {
    if (this.state().inTransit || !form.name || !form.image) {
      return;
    }

    this.patchState({ inTransit: true });
    try {
      const team = await firstValueFrom(this.teamsApi.createTeam(form));
      this.patchState({ teams: [...this.state().teams, team] });
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
      this.patchState({ teams: this.state().teams.filter((team) => team.id !== teamId) });
    } finally {
      this.patchState({ inTransit: false });
    }
  }

  async startAdminGame(): Promise<void> {
    if (this.state().inTransit) {
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
}
