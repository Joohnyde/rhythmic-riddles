import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Storage } from '../utils/storage';
import { environment } from '../../environments/environment';
import { Team } from '../entities/teams';

export interface CreateTeamRequest {
  name: string;
  image: string;
  buttonCode: string; // UUID
}

@Injectable({
  providedIn: 'root',
})
export class TeamService {
  constructor(
    private http: HttpClient,
    private storage: Storage,
  ) {}

  apiUrl() {
    if (this.storage.code == '') throw new Error('Unknown room_code');
    return `${environment.apiUrl}/api/v1/games/${this.storage.code}/teams`;
  }

  createTeam(req: CreateTeamRequest): Observable<Team> {
    return this.http.post<Team>(this.apiUrl(), req);
  }
  kickTeam(id: string): Observable<void> {
    return this.http.delete<void>(this.apiUrl() + '/' + id);
  }
}
