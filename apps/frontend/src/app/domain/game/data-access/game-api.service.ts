import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { GameSession } from '../../../core/session/game-session.service';
import { GameStageId } from '../models/game-stage-id.model';

@Injectable({ providedIn: 'root' })
export class GameApiService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(GameSession);

  private apiUrl(): string {
    if (!this.session.code) {
      throw new Error('Unknown room_code');
    }

    return `${environment.apiUrl}/api/v1/games/${this.session.code}`;
  }

  changeState(stageId: GameStageId): Observable<void> {
    return this.http.put<void>(`${this.apiUrl()}/stage`, { stageId });
  }
}
