import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { Observable, Subscription, filter, fromEvent, shareReplay } from 'rxjs';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { environment } from '../../../environments/environment';
import { GameServerMessage } from '../../domain/game/messages/game-server-message.types';
import { CLIENT_POSITION, ClientSurface } from '../../domain/game/models/client-surface.model';
import { GameSession } from '../session/game-session.service';
import { GAME_CONNECTION_SURFACE_ROUTE_DATA } from './game-connection-route.data';

export interface GameSocketContext {
  roomCode: string;
  surface: ClientSurface;
}

const DOCUMENT_UNLOAD_CLOSE = 4001;

@Injectable({ providedIn: 'root' })
export class GameRealtimeService {
  private readonly router = inject(Router);
  private readonly session = inject(GameSession);
  private readonly destroyRef = inject(DestroyRef);

  private socket?: WebSocketSubject<GameServerMessage>;
  private keepAliveSub?: Subscription;
  private messages?: Observable<GameServerMessage>;
  private context?: GameSocketContext;

  constructor() {
    /*
     * The WebSocket belongs to the active game session, not to one routed page.
     * It therefore survives normal in-game navigation such as
     * lobby -> albums -> songs -> winner, and is closed when Angular successfully
     * navigates out of that connected route tree.
     *
     * There is one important second path: typing a different URL in the browser,
     * refreshing, or closing the tab unloads the whole document, so Angular's
     * Router never gets a NavigationEnd in the old application. We explicitly
     * close from pagehide as well instead of waiting for the browser/TCP timeout.
     *
     * The close codes preserve the backend contract:
     *   1000 = deliberate Angular SPA route change; do not create an interrupt.
     *   4001 = document unload (refresh, tab close, hard URL navigation); treat it
     *          like the old browser-level disconnect and allow the backend to
     *          create an interrupt when the game is in the playing stage.
     *
     * 4001 is in the RFC 6455 application-defined range (4000-4999). If the
     * browser/process disappears before pagehide can run, the server still falls
     * back to its abnormal-disconnect handling as before.
     */
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.disconnectIfGameRouteWasLeft());

    fromEvent(window, 'pagehide')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.disconnectForDocumentUnload());
  }

  connect(context: GameSocketContext): Observable<GameServerMessage> {
    this.disconnect();

    // Room codes are case-insensitive at the UI boundary, but the realtime
    // protocol uses one canonical representation. Normalize here as the final
    // guard before constructing the WebSocket handshake URL.
    const normalizedContext: GameSocketContext = {
      ...context,
      roomCode: context.roomCode.trim().toUpperCase(),
    };
    const handshakeCode = `${CLIENT_POSITION[normalizedContext.surface]}${normalizedContext.roomCode}`;

    this.context = normalizedContext;
    this.socket = webSocket<GameServerMessage>(`${environment.wsUrl}/ws/${handshakeCode}`);
    this.messages = this.socket
      .asObservable()
      .pipe(shareReplay({ bufferSize: 1, refCount: false }));

    // Keep the shared socket subscribed even while routed feature stores swap over.
    this.keepAliveSub = this.messages.subscribe({
      error: () => undefined,
      complete: () => undefined,
    });

    return this.messages;
  }

  disconnect(): void {
    // Preserve the established SPA-navigation contract: completing the subject
    // performs the normal frontend close that the backend receives as status 1000.
    this.socket?.complete();
    this.resetSocketState();
  }

  private disconnectForDocumentUnload(): void {
    if (!this.socket) {
      return;
    }

    // WebSocketSubject accepts an error object with code/reason and forwards them
    // to WebSocket.close(). 4001 keeps hard navigation/refresh distinct from an
    // Angular SPA route change without waiting for an implicit browser disconnect.
    this.socket.error({
      code: DOCUMENT_UNLOAD_CLOSE,
      reason: 'Document unload',
    });
    this.resetSocketState();
  }

  private resetSocketState(): void {
    this.keepAliveSub?.unsubscribe();
    this.keepAliveSub = undefined;
    this.socket = undefined;
    this.messages = undefined;
    this.context = undefined;
  }

  private disconnectIfGameRouteWasLeft(): void {
    if (!this.socket || !this.context) {
      return;
    }

    const routedSurface = this.activeGameSurface();
    if (routedSurface === this.context.surface) {
      return;
    }

    this.disconnect();
    this.session.clear();
  }

  private activeGameSurface(): ClientSurface | undefined {
    let route: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    let surface: ClientSurface | undefined;

    while (route) {
      const routeSurface = route.data[GAME_CONNECTION_SURFACE_ROUTE_DATA] as
        ClientSurface | undefined;
      surface = routeSurface ?? surface;
      route = route.firstChild;
    }

    return surface;
  }
}
