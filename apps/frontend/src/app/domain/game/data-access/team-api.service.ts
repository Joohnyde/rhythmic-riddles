import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { GameSession } from '../../../core/session/game-session.service';
import { Team } from '../models/team.model';

export interface CreateTeamRequest {
  name: string;
  image: string;
  buttonCode: string;
}

@Injectable({ providedIn: 'root' })
export class TeamApiService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(GameSession);

  private apiUrl(): string {
    if (!this.session.code) {
      throw new Error('Unknown room_code');
    }

    return `${environment.apiUrl}/api/v1/games/${this.session.code}/teams`;
  }

  createTeam(req: CreateTeamRequest): Observable<Team> {
    return this.http.post<Team>(this.apiUrl(), req);
  }

  kickTeam(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl()}/${id}`);
  }
}
