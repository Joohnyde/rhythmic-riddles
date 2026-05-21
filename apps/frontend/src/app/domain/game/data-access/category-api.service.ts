import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { GameSession } from '../../../core/session/game-session.service';
import { LastCategory } from '../models/selected-album.model';
@Injectable({ providedIn: 'root' })
export class CategoryApiService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(GameSession);
  private apiUrl(categoryId: string): string {
    if (!this.session.code) throw new Error('Unknown room_code');
    return `${environment.apiUrl}/api/v1/games/${this.session.code}/categories/${categoryId}`;
  }
  pickAlbum(categoryId: string, teamId: string | null): Observable<LastCategory> {
    return this.http.put<LastCategory>(`${this.apiUrl(categoryId)}/pick`, { teamId });
  }
  start(categoryId: string): Observable<unknown> {
    return this.http.post(`${this.apiUrl(categoryId)}/start`, null);
  }
}
