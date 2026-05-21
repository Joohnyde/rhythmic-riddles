import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DefaultMessage } from '../../domain/game/messages/default.messages';
@Injectable({ providedIn: 'root' })
export class GameSession {
  code = '';
  messages$?: Observable<DefaultMessage>;
  clear(): void {
    this.code = '';
    this.messages$ = undefined;
  }
}
