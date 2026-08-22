import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GameRealtimeService } from '../../../core/realtime/game-realtime.service';
import { GameSession } from '../../../core/session/game-session.service';
import { GameServerMessage } from '../../../domain/game/messages/game-server-message.types';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let session: GameSession;
  const connect = vi.fn();
  const disconnect = vi.fn();
  const navigate = vi.fn();

  beforeEach(async () => {
    connect.mockReset();
    disconnect.mockReset();
    navigate.mockReset().mockResolvedValue(true);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        GameSession,
        { provide: GameRealtimeService, useValue: { connect, disconnect } },
        { provide: Router, useValue: { navigate } },
      ],
    }).compileComponents();

    session = TestBed.inject(GameSession);
    fixture = TestBed.createComponent(LoginComponent);
    fixture.componentRef.setInput('surface', 'admin');
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('rejects malformed room codes without opening a socket', async () => {
    component.roomCode.set('ABC');
    await component.login();
    expect(connect).not.toHaveBeenCalled();
    expect(component.inTransit()).toBe(false);
  });

  it('normalizes the room code and commits the session only after welcome', async () => {
    const messages$ = of({ type: 'welcome', stage: 'lobby', teams: [] } as GameServerMessage);
    connect.mockReturnValue(messages$);
    component.roomCode.set(' akku ');

    await component.login();

    expect(connect).toHaveBeenCalledWith({ roomCode: 'AKKU', surface: 'admin' });
    expect(component.roomCode()).toBe('AKKU');
    expect(session.code).toBe('AKKU');
    expect(session.messages$).toBe(messages$);
    expect(navigate).toHaveBeenCalled();
    expect(component.error()).toBeNull();
  });

  it('keeps duplicate submissions out while the handshake is in transit', async () => {
    const messages$ = new Subject<GameServerMessage>();
    connect.mockReturnValue(messages$.asObservable());
    component.roomCode.set('AKKU');

    const firstLogin = component.login();
    await component.login();

    expect(connect).toHaveBeenCalledOnce();
    expect(component.inTransit()).toBe(true);

    messages$.next({ type: 'welcome', stage: 'lobby', teams: [] } as GameServerMessage);
    await firstLogin;
  });

  it('cleans up when opening the websocket fails synchronously', async () => {
    connect.mockImplementation(() => {
      throw new Error('socket construction failed');
    });
    component.roomCode.set('AKKU');

    await component.login();

    expect(disconnect).toHaveBeenCalledOnce();
    expect(session.code).toBe('');
    expect(session.messages$).toBeUndefined();
    expect(component.error()).toBe('Could not connect to that room.');
    expect(component.inTransit()).toBe(false);
  });

  it('disconnects and clears partial session state when the socket errors', async () => {
    session.code = 'OLD';
    session.messages$ = of({ type: 'welcome', stage: 'lobby', teams: [] } as GameServerMessage);
    connect.mockReturnValue(throwError(() => new Error('socket failed')));
    component.roomCode.set('AKKU');

    await component.login();

    expect(disconnect).toHaveBeenCalledOnce();
    expect(session.code).toBe('');
    expect(session.messages$).toBeUndefined();
    expect(component.error()).toBe('Could not connect to that room.');
    expect(component.inTransit()).toBe(false);
  });

  it('treats a non-welcome first frame as a failed handshake', async () => {
    connect.mockReturnValue(
      of({
        type: 'new_team',
        team: { id: 'one', name: 'One', image: '/team-icons/one_A94DFB.png' },
      } as GameServerMessage),
    );
    component.roomCode.set('AKKU');

    await component.login();

    expect(disconnect).toHaveBeenCalledOnce();
    expect(session.code).toBe('');
    expect(navigate).not.toHaveBeenCalled();
    expect(component.error()).toBe('Could not connect to that room.');
  });

  it('cleans up when navigation is cancelled after a valid welcome', async () => {
    connect.mockReturnValue(
      of({ type: 'welcome', stage: 'lobby', teams: [] } as GameServerMessage),
    );
    navigate.mockResolvedValue(false);
    component.roomCode.set('AKKU');

    await component.login();

    expect(disconnect).toHaveBeenCalledOnce();
    expect(session.code).toBe('');
    expect(session.messages$).toBeUndefined();
    expect(component.error()).toBe('Could not connect to that room.');
  });

  it('cleans up when navigation after a valid welcome fails', async () => {
    const messages$ = new Subject<GameServerMessage>();
    connect.mockReturnValue(messages$.asObservable());
    navigate.mockRejectedValue(new Error('navigation failed'));
    component.roomCode.set('AKKU');

    const login = component.login();
    messages$.next({ type: 'welcome', stage: 'lobby', teams: [] } as GameServerMessage);
    await login;

    expect(disconnect).toHaveBeenCalledOnce();
    expect(session.code).toBe('');
    expect(component.error()).toBe('Could not connect to that room.');
  });
});
