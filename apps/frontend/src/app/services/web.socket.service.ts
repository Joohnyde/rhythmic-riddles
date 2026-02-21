import { Inject, Injectable } from '@angular/core';
import { WebSocketSubject, webSocket } from 'rxjs/webSocket';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class WebSocketService {
  private socket$: WebSocketSubject<any>;

 constructor(
  @Inject('room_code') private code: string
 ) {
   // 'code' is actually "{socketPosition}{roomCode}" (e.g., "0AKKU") to match backend handshake parsing.
    
   this.socket$ = webSocket(`${environment.wsUrl}/ws/${code}`);
  }

  // Send a message to the server
  sendMessage(message: any) {
    this.socket$.next(message);
  }

  // Receive messages from the server
  getMessages(): Observable<any> {
    return this.socket$.asObservable();
  }

  // Close the WebSocket connection
  closeConnection() {
    this.socket$.complete();
  }
}