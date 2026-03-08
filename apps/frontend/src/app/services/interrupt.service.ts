import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Storage } from '../utils/storage';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class InterruptService {
  constructor(
    private http: HttpClient,
    private storage: Storage,
  ) {}

  apiUrl() {
    if (this.storage.code == '') throw new Error('Unknown room_code');
    return `${environment.apiUrl}/api/v1/games/${this.storage.code}`;
  }

  resolveErrors(scheduleId: string): Observable<void> {
    return this.http.post<void>(this.apiUrl() + '/interrupts/system/resolve', {
      scheduleId: scheduleId,
    });
  }

  answer(answerId: string, correct: boolean): Observable<void> {
    return this.http.post<void>(this.apiUrl() + '/interrupts/' + answerId + '/answer', {
      correct: correct,
    });
  }

  savePrevScenario(previousScenario: number): Observable<void> {
    return this.http.put<void>(this.apiUrl() + '/ui/scenario', { scenario: previousScenario });
  }
}
