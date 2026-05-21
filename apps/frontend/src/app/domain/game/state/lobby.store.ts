import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, Subscription } from 'rxjs';
import { DefaultMessage } from '../messages/default.messages';
import { S0KickTeamMessage, S0NewTeamMessage, S0WelcomeMessage } from '../messages/stage0.messages';
import { Team } from '../models/team.model';
import { GameApiService } from '../data-access/game-api.service';
import { TeamApiService } from '../data-access/team-api.service';
import { ClientSurface } from '../../../core/realtime/game-realtime.service';

interface LobbyState {
  teams: Team[];
  inTransit: boolean;
}
const initialState: LobbyState = { teams: [], inTransit: false };
@Injectable({ providedIn: 'root' })
export class LobbyStore {
  private readonly router = inject(Router);
  private readonly teamsApi = inject(TeamApiService);
  private readonly gameApi = inject(GameApiService);
  private readonly state = signal<LobbyState>(initialState);
  private sub?: Subscription;
  readonly vm = computed(() => this.state());
  connect(messages$: Observable<DefaultMessage>, surface: ClientSurface): void {
    this.sub?.unsubscribe();
    this.sub = messages$.subscribe((m) => this.handleMessage(m, surface));
  }
  disconnect(): void {
    this.sub?.unsubscribe();
    this.sub = undefined;
  }
  private patch(patch: Partial<LobbyState>) {
    this.state.update((s) => ({ ...s, ...patch }));
  }
  private handleMessage(message: DefaultMessage, surface: ClientSurface): void {
    switch (message.type) {
      case 'welcome':
        this.handleWelcome(message as S0WelcomeMessage, surface);
        break;
      case 'new_team':
        this.patch({ teams: [...this.state().teams, (message as S0NewTeamMessage).team] });
        break;
      case 'kick_team':
        this.patch({
          teams: this.state().teams.filter((t) => t.id !== (message as S0KickTeamMessage).uuid),
        });
        break;
    }
  }
  private handleWelcome(message: S0WelcomeMessage, surface: ClientSurface): void {
    if (message.stage === 'lobby') {
      this.patch({ teams: message.teams });
      return;
    }
    this.disconnect();
    this.router.navigate(surface === 'admin' ? ['admin', message.stage] : [message.stage]);
  }
  async createTeam(form: { name: string; image: string; buttonCode: string }): Promise<void> {
    if (this.state().inTransit || !form.name || !form.image) return;
    this.patch({ inTransit: true });
    try {
      const team = await firstValueFrom(this.teamsApi.createTeam(form));
      this.patch({ teams: [...this.state().teams, team] });
    } finally {
      this.patch({ inTransit: false });
    }
  }
  async kickTeam(teamId: string): Promise<void> {
    if (this.state().inTransit) return;
    this.patch({ inTransit: true });
    try {
      await firstValueFrom(this.teamsApi.kickTeam(teamId));
      this.patch({ teams: this.state().teams.filter((t) => t.id !== teamId) });
    } finally {
      this.patch({ inTransit: false });
    }
  }
  async startAdminGame(): Promise<void> {
    if (this.state().inTransit) return;
    this.patch({ inTransit: true });
    try {
      await firstValueFrom(this.gameApi.changeState(1));
      this.router.navigate(['admin', 'albums']);
    } finally {
      this.patch({ inTransit: false });
    }
  }
}
