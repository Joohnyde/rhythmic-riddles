import { Injectable } from '@angular/core';
import { Observable, Subscription, shareReplay } from 'rxjs';
import { WebSocketSubject, webSocket } from 'rxjs/webSocket';
import { environment } from '../../../environments/environment';
import { DefaultMessage } from '../../domain/game/messages/default.messages';

export type ClientSurface = 'admin' | 'tv';
export interface GameSocketContext {
  roomCode: string;
  surface: ClientSurface;
}
const CLIENT_POSITION: Record<ClientSurface, number> = { admin: 0, tv: 1 };

@Injectable({ providedIn: 'root' })
export class GameRealtimeService {
  private socket?: WebSocketSubject<DefaultMessage>;
  private keepAliveSub?: Subscription;
  private messages?: Observable<DefaultMessage>;

  connect(context: GameSocketContext): Observable<DefaultMessage> {
    this.disconnect();
    const handshakeCode = `${CLIENT_POSITION[context.surface]}${context.roomCode}`;
    this.socket = webSocket<DefaultMessage>(`${environment.wsUrl}/ws/${handshakeCode}`);
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
