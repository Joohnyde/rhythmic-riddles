import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GameSession } from '../../../core/session/game-session.service';
import { environment } from '../../../../environments/environment';

export interface PlaybackRegistration {
  delayId: string;
  clientAttemptAt: number;
}

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

  register(scheduleId: string, clientAttemptAt: number): Observable<PlaybackRegistration> {
    return this.http.post<PlaybackRegistration>(this.apiUrl(), { scheduleId, clientAttemptAt });
  }

  acknowledge(delayId: string, clientAttemptAt: number): Observable<void> {
    return this.http.put<void>(`${this.apiUrl()}/${delayId}/acknowledge`, { clientAttemptAt });
  }
}
