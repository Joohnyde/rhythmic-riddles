import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GameSession } from '../../../core/session/game-session.service';
import { GameApiService } from '../data-access/game-api.service';
import { TeamApiService } from '../data-access/team-api.service';
import { TEAM_ICONS } from '../generated/team-icons.generated';
import { GameServerMessage } from '../messages/game-server-message.types';
import { GameStageId } from '../models/game-stage-id.model';
import { Team } from '../models/team.model';
import { LobbyStore } from './lobby.store';

function team(id: string, image: string, name = id): Team {
  return { id, image, name };
}

describe('LobbyStore Stage 0 behavior', () => {
  let store: LobbyStore;
  let session: GameSession;
  let messages$: Subject<GameServerMessage>;
  const createTeam = vi.fn();
  const kickTeam = vi.fn();
  const changeState = vi.fn();
  const navigate = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-21T12:00:00Z'));
    createTeam.mockReset();
    kickTeam.mockReset();
    changeState.mockReset();
    navigate.mockReset().mockResolvedValue(true);

    TestBed.configureTestingModule({
      providers: [
        LobbyStore,
        GameSession,
        { provide: TeamApiService, useValue: { createTeam, kickTeam } },
        { provide: GameApiService, useValue: { changeState } },
        { provide: Router, useValue: { navigate } },
      ],
    });

    store = TestBed.inject(LobbyStore);
    session = TestBed.inject(GameSession);
    session.code = 'AKKU';
    messages$ = new Subject<GameServerMessage>();
    store.connect(messages$, 'admin');
  });

  afterEach(() => {
    store.disconnect();
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  it('hydrates lobby teams from welcome and prevents duplicate websocket additions', () => {
    const existing = team('one', TEAM_ICONS[0]);
    messages$.next({ type: 'welcome', stage: 'lobby', teams: [existing] } as GameServerMessage);
    messages$.next({ type: 'new_team', team: existing } as GameServerMessage);

    expect(store.vm().teams).toEqual([existing]);
    expect(store.vm().canStartGame).toBe(true);
  });

  it('keeps icon choices unique while teams are added', () => {
    const firstIcon = store.vm().draft.image;
    messages$.next({
      type: 'new_team',
      team: team('one', firstIcon),
    } as GameServerMessage);

    expect(store.vm().availableIcons).not.toContain(firstIcon);
    expect(store.vm().draft.image).not.toBe(firstIcon);
  });

  it('frees an icon when its team is removed', () => {
    const firstIcon = TEAM_ICONS[0];
    messages$.next({
      type: 'welcome',
      stage: 'lobby',
      teams: [team('one', firstIcon)],
    } as GameServerMessage);
    messages$.next({ type: 'kick_team', uuid: 'one' } as GameServerMessage);

    expect(store.vm().availableIcons).toContain(firstIcon);
  });

  it('keeps icon uniqueness correct across add, remove and icon-change operations', () => {
    const [firstIcon, secondIcon] = TEAM_ICONS;
    messages$.next({
      type: 'welcome',
      stage: 'lobby',
      teams: [team('one', firstIcon), team('two', secondIcon)],
    } as GameServerMessage);

    expect(store.vm().availableIcons).not.toContain(firstIcon);
    expect(store.vm().availableIcons).not.toContain(secondIcon);

    messages$.next({ type: 'kick_team', uuid: 'one' } as GameServerMessage);
    expect(store.vm().availableIcons).toContain(firstIcon);

    const visited = new Set<string>();
    for (let index = 0; index < TEAM_ICONS.length; index += 1) {
      visited.add(store.vm().draft.image);
      store.selectNextIcon();
    }
    expect(visited).toContain(firstIcon);
    expect(visited).not.toContain(secondIcon);
  });

  it('stays stable when all generated icons are already assigned and recovers when one is freed', () => {
    const exhaustedTeams = TEAM_ICONS.map((icon, index) =>
      team(`team-${index}`, icon, `Team ${index + 1}`),
    );
    messages$.next({
      type: 'welcome',
      stage: 'lobby',
      teams: exhaustedTeams,
    } as GameServerMessage);

    expect(store.vm().availableIcons).toEqual([]);
    expect(store.vm().draft.image).toBe('');
    expect(store.vm().canAddTeam).toBe(false);

    store.selectNextIcon();
    expect(store.vm().draft.image).toBe('');

    messages$.next({ type: 'kick_team', uuid: 'team-0' } as GameServerMessage);
    expect(store.vm().availableIcons).toContain(TEAM_ICONS[0]);
    expect(store.vm().draft.image).toBe(TEAM_ICONS[0]);
  });

  it('cycles only through currently unused icons when changing a draft icon', () => {
    const firstIcon = TEAM_ICONS[0];
    messages$.next({
      type: 'welcome',
      stage: 'lobby',
      teams: [team('one', firstIcon)],
    } as GameServerMessage);

    for (let index = 0; index < TEAM_ICONS.length * 2; index += 1) {
      store.selectNextIcon();
      expect(store.vm().draft.image).not.toBe(firstIcon);
    }
  });

  it('links a buzzer only after a same-code double click within the threshold', () => {
    store.setDraftName('Team One');
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    expect(store.vm().draft.buttonCode).toBe('');

    vi.advanceTimersByTime(249);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    expect(store.vm().draft.buttonCode).toBe('101');
    expect(store.vm().buzzerPulseSequence).toBe(1);
  });

  it('does not link different codes or clicks outside the double-click window', () => {
    store.setDraftName('Team One');
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    messages$.next({ type: 'button_clicked', buttonCode: '202' } as GameServerMessage);
    expect(store.vm().draft.buttonCode).toBe('');

    vi.advanceTimersByTime(251);
    messages$.next({ type: 'button_clicked', buttonCode: '202' } as GameServerMessage);
    expect(store.vm().draft.buttonCode).toBe('');
  });

  it('ignores unlinked buzzer presses until the draft has a team name', () => {
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    expect(store.vm().draft.buttonCode).toBe('');
  });

  it('increments the animation sequence for every press of an already linked buzzer', () => {
    store.setDraftName('Team One');
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    expect(store.vm().buzzerPulseSequence).toBe(3);
  });

  it('does not apply the 250 ms linking window after the buzzer is linked', () => {
    store.setDraftName('Team One');
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    vi.advanceTimersByTime(100);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    expect(store.vm().buzzerPulseSequence).toBe(1);

    vi.advanceTimersByTime(50);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    expect(store.vm().buzzerPulseSequence).toBe(2);

    vi.advanceTimersByTime(50);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    expect(store.vm().buzzerPulseSequence).toBe(3);
  });

  it('ignores button-clicked frames on the TV surface', () => {
    store.disconnect();
    store.setDraftName('Team One');
    store.connect(messages$, 'tv');
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    expect(store.vm().draft.buttonCode).toBe('');
  });

  it('creates a valid team and resets the draft only after success', async () => {
    const icon = store.vm().draft.image;
    store.setDraftName('  Team One  ');
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    createTeam.mockReturnValue(of(team('created', icon, 'Team One')));

    await expect(store.createTeam()).resolves.toBe(true);
    expect(createTeam).toHaveBeenCalledWith({ name: 'Team One', image: icon, buttonCode: '101' });
    expect(store.vm().teams.map((value) => value.id)).toContain('created');
    expect(store.vm().draft.name).toBe('');
    expect(store.vm().draft.buttonCode).toBe('');
  });

  it('preserves the draft when creation fails so the operator can retry', async () => {
    store.setDraftName('Team One');
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    messages$.next({ type: 'button_clicked', buttonCode: '101' } as GameServerMessage);
    createTeam.mockReturnValue(throwError(() => new Error('network')));

    await expect(store.createTeam()).rejects.toThrow('network');
    expect(store.vm().draft.name).toBe('Team One');
    expect(store.vm().draft.buttonCode).toBe('101');
    expect(store.vm().inTransit).toBe(false);
  });

  it('refuses incomplete draft creation without calling the API', async () => {
    store.setDraftName('Team One');
    await expect(store.createTeam()).resolves.toBe(false);
    expect(createTeam).not.toHaveBeenCalled();
  });

  it('removes a team after a successful admin kick request', async () => {
    const existing = team('one', TEAM_ICONS[0]);
    messages$.next({ type: 'welcome', stage: 'lobby', teams: [existing] } as GameServerMessage);
    kickTeam.mockReturnValue(of(undefined));

    await store.kickTeam(existing.id);

    expect(kickTeam).toHaveBeenCalledWith(existing.id);
    expect(store.vm().teams).toEqual([]);
    expect(store.vm().availableIcons).toContain(existing.image);
    expect(store.vm().inTransit).toBe(false);
  });

  it('retains the team and clears transit state when a kick request fails', async () => {
    const existing = team('one', TEAM_ICONS[0]);
    messages$.next({ type: 'welcome', stage: 'lobby', teams: [existing] } as GameServerMessage);
    kickTeam.mockReturnValue(throwError(() => new Error('network')));

    await expect(store.kickTeam(existing.id)).rejects.toThrow('network');

    expect(store.vm().teams).toEqual([existing]);
    expect(store.vm().inTransit).toBe(false);
  });

  it('does not start an empty lobby even when the action is called directly', async () => {
    await store.startAdminGame();

    expect(changeState).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    expect(store.vm().inTransit).toBe(false);
  });

  it('starts the game through the Stage 0 API and routes the admin to album selection', async () => {
    messages$.next({
      type: 'welcome',
      stage: 'lobby',
      teams: [team('one', TEAM_ICONS[0])],
    } as GameServerMessage);
    changeState.mockReturnValue(of(undefined));

    await store.startAdminGame();

    expect(changeState).toHaveBeenCalledWith(GameStageId.Albums);
    expect(navigate).toHaveBeenCalledWith(['admin', 'albums']);
    expect(store.vm().inTransit).toBe(false);
  });

  it('returns canStartGame to false after the final team is removed', () => {
    const existing = team('one', TEAM_ICONS[0]);
    messages$.next({ type: 'welcome', stage: 'lobby', teams: [existing] } as GameServerMessage);
    expect(store.vm().canStartGame).toBe(true);

    messages$.next({ type: 'kick_team', uuid: 'one' } as GameServerMessage);
    expect(store.vm().canStartGame).toBe(false);
  });

  it('clears stale lobby state when a new websocket connection is attached', () => {
    messages$.next({
      type: 'welcome',
      stage: 'lobby',
      teams: [team('one', TEAM_ICONS[0])],
    } as GameServerMessage);
    store.setDraftName('Stale draft');

    const nextRoomMessages = new Subject<GameServerMessage>();
    store.connect(nextRoomMessages, 'admin');

    expect(store.vm().teams).toEqual([]);
    expect(store.vm().draft.name).toBe('');
    nextRoomMessages.complete();
  });

  it('moves away from Stage 0 when welcome reports another stage', () => {
    messages$.next({ type: 'welcome', stage: 'albums', teams: [] } as GameServerMessage);
    expect(navigate).toHaveBeenCalled();
  });
});
