import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { GameSession } from '../../../core/session/game-session.service';
import { LastCategory } from '../models/selected-album.model';

@Injectable({ providedIn: 'root' })
export class CategoryApiService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(GameSession);

  private apiUrl(categoryId: string): string {
    if (!this.session.code) {
      throw new Error('Unknown room_code');
    }

    return `${environment.apiUrl}/api/v1/games/${this.session.code}/categories/${categoryId}`;
  }

  pickAlbum(categoryId: string, teamId: string | null): Observable<LastCategory> {
    return this.http.put<LastCategory>(`${this.apiUrl(categoryId)}/pick`, { teamId });
  }

  start(categoryId: string): Observable<void> {
    return this.http.post<unknown>(`${this.apiUrl(categoryId)}/start`, null).pipe(
      map((response) => {
        // Some servers expose business errors in a JSON response body rather
        // than a failing HTTP status. Treat either form as a rejected start.
        let body: unknown = response;
        if (typeof response === 'string') {
          try { body = JSON.parse(response) as unknown; }
          catch { body = response; }
        }
        if (typeof body === 'string' && /E004|app not reachable|TV app has to be connected/i.test(body)) {
          throw new Error(body);
        }
        if (body && typeof body === 'object') {
          const result = body as { error?: unknown; message?: unknown };
          const errorCode = typeof result.error === 'string' ? result.error : '';
          const message = typeof result.message === 'string' ? result.message : '';
          if (/E004|app not reachable/i.test(errorCode) ||
              /TV app has to be connected to proceed/i.test(message) ||
              (errorCode && message)) {
            throw new Error([errorCode, message].filter(Boolean).join(': '));
          }
        }
      }),
    );
  }
}
