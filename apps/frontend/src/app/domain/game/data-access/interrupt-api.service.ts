import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { GameSession } from '../../../core/session/game-session.service';
import { SongScenario } from '../state/song-scenario';

@Injectable({ providedIn: 'root' })
export class InterruptApiService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(GameSession);

  private apiUrl(): string {
    if (!this.session.code) {
      throw new Error('Unknown room_code');
    }

    return `${environment.apiUrl}/api/v1/games/${this.session.code}`;
  }

  resolveErrors(scheduleId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl()}/interrupts/system/resolve`, { scheduleId });
  }

  answer(answerId: string, correct: boolean): Observable<void> {
    return this.http.post<void>(`${this.apiUrl()}/interrupts/${answerId}/answer`, { correct });
  }

  savePrevScenario(previousScenario: SongScenario): Observable<void> {
    return this.http.put<void>(`${this.apiUrl()}/ui/scenario`, { scenario: previousScenario });
  }
}
