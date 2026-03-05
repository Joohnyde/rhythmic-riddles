import { Inject, Injectable } from '@angular/core';
import { WebSocketSubject, webSocket } from 'rxjs/webSocket';
import { Observable, shareReplay, Subscription } from 'rxjs';
import { DefaultMessage } from '../entities/messages/default.messages';

@Injectable({
  providedIn: 'root',
})
export class WebSocketService {
  private socket$: WebSocketSubject<DefaultMessage>;
  private keepAliveSub?: Subscription;

  // Expose a shared stream
  private messages$: Observable<DefaultMessage>;

  constructor(@Inject('room_code') private code: string) {
    // 'code' is actually "{socketPosition}{roomCode}" (e.g., "0AKKU") to match backend handshake parsing.
    this.socket$ = webSocket('ws://localhost:8080/ws/' + code);

       // Share the same socket among all subscribers and cache last message
    this.messages$ = this.socket$.asObservable().pipe(
      // IMPORTANT: refCount false means it won't close just because nobody is subscribed
      shareReplay({ bufferSize: 1, refCount: false })
    );

    // Keep the connection open
    this.keepAliveSub = this.messages$.subscribe({
      error: () => {}, // optional
      complete: () => {} // optional
    });
  }

  // Receive messages from the server
  getMessages(): Observable<DefaultMessage> {
    return this.messages$;
  }

  // Close the WebSocket connection
  closeConnection() {
    this.socket$.complete();
  }
}
