import { Inject, Injectable } from '@angular/core';
import { WebSocketSubject, webSocket } from 'rxjs/webSocket';
import { Observable } from 'rxjs';
import { DefaultMessage } from '../entities/messages/default.messages';

@Injectable({
  providedIn: 'root',
})
export class WebSocketService {
  private socket$: WebSocketSubject<DefaultMessage>;

  constructor(@Inject('room_code') private code: string) {
    // 'code' is actually "{socketPosition}{roomCode}" (e.g., "0AKKU") to match backend handshake parsing.
    this.socket$ = webSocket('ws://localhost:8080/ws/' + code);
  }

  // Receive messages from the server
  getMessages(): Observable<DefaultMessage> {
    return this.socket$.asObservable();
  }

  // Close the WebSocket connection
  closeConnection() {
    this.socket$.complete();
  }
}
