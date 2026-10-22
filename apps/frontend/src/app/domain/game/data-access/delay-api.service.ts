import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GameSession } from '../../../core/session/game-session.service';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class DelayApiService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(GameSession);

  private apiUrl(): string {
    if (!this.session.code) {
      throw new Error('Unknown room code');
    }
    return `${environment.apiUrl}/api/v1/games/${this.session.code}/delays`;
  }

  register(scheduleId: string): Observable<string> {
    return this.http.post<string>(this.apiUrl(), { scheduleId });
  }

  acknowledge(delayId: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl()}/${delayId}/acknowledge`, null);
  }

}
