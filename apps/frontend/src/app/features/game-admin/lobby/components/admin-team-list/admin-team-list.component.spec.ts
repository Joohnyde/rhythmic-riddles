import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LobbyStore } from '../../../../../domain/game/state/lobby.store';
import { AdminTeamListComponent } from './admin-team-list.component';

describe('AdminTeamListComponent', () => {
  let fixture: ComponentFixture<AdminTeamListComponent>;
  const initialVm = () => ({
    teams: [
      { id: 'one', name: 'Riddlers', image: '/team-icons/test_A94DFB.png' },
      { id: 'two', name: 'Tempo', image: '/team-icons/test_3EB8F0.png' },
    ],
    inTransit: false,
    draft: { name: '', image: '', buttonCode: '' },
    buzzerPulseSequence: 0,
    roomCode: 'AKKU',
    canAddTeam: false,
    canStartGame: true,
    availableIcons: [],
  });
  const vm = signal(initialVm());
  const kickTeam = vi.fn().mockResolvedValue(undefined);

  beforeEach(async () => {
    vm.set(initialVm());
    kickTeam.mockClear();
    await TestBed.configureTestingModule({
      imports: [AdminTeamListComponent],
      providers: [{ provide: LobbyStore, useValue: { vm, kickTeam } }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminTeamListComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('renders every lobby team with a stable id-based row hook', () => {
    expect(
      fixture.nativeElement.querySelector('[data-testid="admin-team-row-one"]'),
    ).not.toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="admin-team-row-two"]'),
    ).not.toBeNull();
  });

  it('keeps the displayed team count synchronized with the actual team list size', () => {
    const teamCount = () =>
      fixture.nativeElement.querySelector('[data-testid="admin-team-count"]').textContent.trim();

    expect(teamCount()).toBe('(2)');

    vm.update((value) => ({ ...value, teams: value.teams.slice(0, 1) }));
    fixture.detectChanges();
    expect(teamCount()).toBe('(1)');

    vm.update((value) => ({ ...value, teams: [] }));
    fixture.detectChanges();
    expect(teamCount()).toBe('(0)');
  });

  it('uses the original trash-can icon for team removal', () => {
    const remove = fixture.nativeElement.querySelector(
      '[data-testid="admin-kick-team-button-one"]',
    );
    const icon = remove.querySelector('svg');
    const paths = [...icon.querySelectorAll('path')].map((path: SVGPathElement) =>
      path.getAttribute('d'),
    );

    expect(icon.getAttribute('viewBox')).toBe('0 0 24 24');
    expect(paths).toEqual(['M9 4h6', 'M5 7h14', 'M8 7l1 13h6l1-13', 'M10 10v6M14 10v6']);
  });

  it('opens the matching confirmation dialog and removes only that team when confirmed', () => {
    const remove = fixture.nativeElement.querySelector(
      '[data-testid="admin-kick-team-button-one"]',
    );
    const dialog = remove.parentElement.querySelector('dialog') as HTMLDialogElement;
    dialog.showModal = vi.fn(() => dialog.setAttribute('open', ''));
    dialog.close = vi.fn(() => dialog.removeAttribute('open'));

    remove.click();
    fixture.detectChanges();

    expect(dialog.open).toBe(true);
    const confirm = [...dialog.querySelectorAll('button')].find((button: HTMLButtonElement) =>
      button.textContent?.includes('REMOVE'),
    ) as HTMLButtonElement;
    confirm.click();

    expect(kickTeam).toHaveBeenCalledOnce();
    expect(kickTeam).toHaveBeenCalledWith('one');
  });

  it('shows the empty-state guidance when all teams are removed', () => {
    vm.update((value) => ({ ...value, teams: [], canStartGame: false }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No teams yet');
  });
});
