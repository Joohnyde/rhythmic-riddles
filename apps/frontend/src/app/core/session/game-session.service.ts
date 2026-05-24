import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GameServerMessage } from '../../domain/game/messages/game-server-message.types';

@Injectable({ providedIn: 'root' })
export class GameSession {
  code = '';
  messages$?: Observable<GameServerMessage>;

  clear(): void {
    this.code = '';
    this.messages$ = undefined;
  }
}
