import { Injectable } from '@angular/core';
import { Observable, shareReplay, Subscription } from 'rxjs';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { environment } from '../../../environments/environment';
import { GameServerMessage } from '../../domain/game/messages/game-server-message.types';
import { CLIENT_POSITION, ClientSurface } from '../../domain/game/models/client-surface.model';

export interface GameSocketContext {
  roomCode: string;
  surface: ClientSurface;
}

@Injectable({ providedIn: 'root' })
export class GameRealtimeService {
  private socket?: WebSocketSubject<GameServerMessage>;
  private keepAliveSub?: Subscription;
  private messages?: Observable<GameServerMessage>;

  connect(context: GameSocketContext): Observable<GameServerMessage> {
    this.disconnect();

    const handshakeCode = `${CLIENT_POSITION[context.surface]}${context.roomCode}`;
    this.socket = webSocket<GameServerMessage>(`${environment.wsUrl}/ws/${handshakeCode}`);
    this.messages = this.socket
      .asObservable()
      .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    this.keepAliveSub = this.messages.subscribe({
      error: () => undefined,
      complete: () => undefined,
    });

    return this.messages;
  }

  disconnect(): void {
    this.keepAliveSub?.unsubscribe();
    this.keepAliveSub = undefined;
    this.socket?.complete();
    this.socket = undefined;
    this.messages = undefined;
  }
}
