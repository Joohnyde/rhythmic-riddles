import { Inject, Injectable } from '@angular/core';
import { WebSocketSubject, webSocket } from 'rxjs/webSocket';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class WebSocketService {
  private socket$: WebSocketSubject<any>;

 constructor(
  @Inject('room_code') private code: string
 ) {
    this.socket$ = webSocket('ws://localhost:8080/ws/'+code);
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
    alert("Ula")
    this.socket$.complete();
  }
}