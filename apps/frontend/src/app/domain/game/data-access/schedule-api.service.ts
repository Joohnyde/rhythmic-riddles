import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { GameSession } from '../../../core/session/game-session.service';

@Injectable({ providedIn: 'root' })
export class ScheduleApiService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(GameSession);

  private apiUrl(): string {
    if (!this.session.code) {
      throw new Error('Unknown room_code');
    }

    return `${environment.apiUrl}/api/v1/games/${this.session.code}/schedules`;
  }

  revealAnswer(scheduleId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl()}/${scheduleId}/reveal`, null);
  }

  replaySong(scheduleId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl()}/${scheduleId}/replay`, null);
  }

  next(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl()}/next`, null);
  }
}
