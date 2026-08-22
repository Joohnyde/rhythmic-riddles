import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LobbyStore } from '../../../../../domain/game/state/lobby.store';
import { AdminTeamFormComponent } from './admin-team-form.component';

describe('AdminTeamFormComponent', () => {
  let fixture: ComponentFixture<AdminTeamFormComponent>;
  let nextAnimationFrameId: number;
  let animationFrames: Map<number, FrameRequestCallback>;

  const vm = signal({
    teams: [],
    inTransit: false,
    draft: { name: 'Riddlers', image: '/team-icons/test_A94DFB.png', buttonCode: '101' },
    buzzerPulseSequence: 0,
    roomCode: 'AKKU',
    canAddTeam: true,
    canStartGame: false,
    availableIcons: ['/team-icons/test_A94DFB.png'],
  });
  const store = {
    vm,
    setDraftName: vi.fn(),
    selectNextIcon: vi.fn(),
    clearDraft: vi.fn(),
    createTeam: vi.fn().mockResolvedValue(true),
  };

  const flushAnimationFrame = (): void => {
    const pending = [...animationFrames.entries()];
    animationFrames.clear();
    for (const [, callback] of pending) {
      callback(performance.now());
    }
    fixture.detectChanges();
  };

  beforeEach(async () => {
    nextAnimationFrameId = 1;
    animationFrames = new Map<number, FrameRequestCallback>();
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      const id = nextAnimationFrameId++;
      animationFrames.set(id, callback);
      return id;
    });
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation((id) => {
      animationFrames.delete(id);
    });

    vm.set({
      teams: [],
      inTransit: false,
      draft: { name: 'Riddlers', image: '/team-icons/test_A94DFB.png', buttonCode: '101' },
      buzzerPulseSequence: 0,
      roomCode: 'AKKU',
      canAddTeam: true,
      canStartGame: false,
      availableIcons: ['/team-icons/test_A94DFB.png'],
    });
    store.setDraftName.mockClear();
    store.selectNextIcon.mockClear();
    store.clearDraft.mockClear();
    store.createTeam.mockClear();

    await TestBed.configureTestingModule({
      imports: [AdminTeamFormComponent],
      providers: [{ provide: LobbyStore, useValue: store }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminTeamFormComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('keeps form content scrollable without clipping the action-row ripple', () => {
    const form = fixture.nativeElement.querySelector('[data-testid="admin-create-team-form"]');
    const scrollRegion = fixture.nativeElement.querySelector(
      '[data-testid="admin-create-team-scroll-region"]',
    );

    expect(form).not.toBeNull();
    expect(form.classList.contains('overflow-y-auto')).toBe(false);
    expect(scrollRegion).not.toBeNull();
    expect(scrollRegion.classList.contains('min-h-0')).toBe(true);
    expect(scrollRegion.classList.contains('overflow-y-auto')).toBe(true);
    expect(scrollRegion.classList.contains('overscroll-contain')).toBe(true);
  });

  it('disables icon selection and team creation when no generated icon remains', () => {
    vm.set({
      teams: [],
      inTransit: false,
      draft: { name: 'Seventh team', image: '', buttonCode: '707' },
      buzzerPulseSequence: 0,
      roomCode: 'AKKU',
      canAddTeam: false,
      canStartGame: true,
      availableIcons: [],
    });
    fixture.detectChanges();

    const picker = fixture.nativeElement.querySelector('[data-testid="admin-team-icon-picker"]');
    const addButton = fixture.nativeElement.querySelector(
      '[data-testid="admin-create-team-button"]',
    );

    expect(picker.disabled).toBe(true);
    expect(picker.getAttribute('title')).toBe('No unused team icon is available.');
    expect(addButton.disabled).toBe(true);
    const tooltip = fixture.nativeElement.querySelector(
      '[data-testid="admin-add-team-tooltip"] .tooltip-text',
    );
    expect(tooltip.textContent.trim()).toBe('No unused team icon is available.');
  });

  it('cycles the icon through the store rather than keeping component-local icon state', () => {
    fixture.nativeElement.querySelector('[data-testid="admin-team-icon-picker"]').click();
    expect(store.selectNextIcon).toHaveBeenCalledOnce();
  });

  it('restores the disabled add-button tooltip text for each missing requirement', () => {
    const tooltipText = () =>
      fixture.nativeElement
        .querySelector('[data-testid="admin-add-team-tooltip"] .tooltip-text')
        .textContent.trim();

    vm.update((value) => ({
      ...value,
      draft: { ...value.draft, name: '', buttonCode: '' },
      canAddTeam: false,
    }));
    fixture.detectChanges();
    expect(tooltipText()).toBe('Enter a team name and link a buzzer before adding the team.');

    vm.update((value) => ({
      ...value,
      draft: { ...value.draft, name: 'Riddlers', buttonCode: '' },
    }));
    fixture.detectChanges();
    expect(tooltipText()).toBe('Link a buzzer before adding the team.');

    vm.update((value) => ({
      ...value,
      draft: { ...value.draft, name: '', buttonCode: '101' },
    }));
    fixture.detectChanges();
    expect(tooltipText()).toBe('Enter a team name before adding the team.');
  });

  it('restarts the original CSS ripple for every pulse sequence change', () => {
    const button = () =>
      fixture.nativeElement.querySelector('[data-testid="admin-create-team-button"]');

    vm.update((value) => ({ ...value, buzzerPulseSequence: 1 }));
    fixture.detectChanges();
    expect(button().classList.contains('buzzer-pulse')).toBe(false);
    expect(animationFrames.size).toBe(1);

    flushAnimationFrame();
    expect(button().classList.contains('buzzer-pulse')).toBe(true);

    vm.update((value) => ({ ...value, buzzerPulseSequence: 2 }));
    fixture.detectChanges();
    expect(button().classList.contains('buzzer-pulse')).toBe(false);
    expect(animationFrames.size).toBe(1);

    flushAnimationFrame();
    expect(button().classList.contains('buzzer-pulse')).toBe(true);
  });

  it('does not wait for the previous animation to finish before restarting it', () => {
    const button = () =>
      fixture.nativeElement.querySelector('[data-testid="admin-create-team-button"]');

    vm.update((value) => ({ ...value, buzzerPulseSequence: 1 }));
    fixture.detectChanges();
    flushAnimationFrame();
    expect(button().classList.contains('buzzer-pulse')).toBe(true);

    vm.update((value) => ({ ...value, buzzerPulseSequence: 2 }));
    fixture.detectChanges();
    expect(button().classList.contains('buzzer-pulse')).toBe(false);

    flushAnimationFrame();
    expect(button().classList.contains('buzzer-pulse')).toBe(true);
  });

  it('does not restart the buzzer animation when unrelated form state changes', () => {
    const button = () =>
      fixture.nativeElement.querySelector('[data-testid="admin-create-team-button"]');

    vm.update((value) => ({ ...value, buzzerPulseSequence: 1 }));
    fixture.detectChanges();
    flushAnimationFrame();
    expect(button().classList.contains('buzzer-pulse')).toBe(true);

    vm.update((value) => ({
      ...value,
      draft: { ...value.draft, image: '/team-icons/other_FF6A5F.png' },
    }));
    fixture.detectChanges();

    expect(button().classList.contains('buzzer-pulse')).toBe(true);
    expect(animationFrames.size).toBe(0);
  });

  it('stops the active ripple when the linked buzzer is cleared', () => {
    const button = () =>
      fixture.nativeElement.querySelector('[data-testid="admin-create-team-button"]');

    vm.update((value) => ({ ...value, buzzerPulseSequence: 1 }));
    fixture.detectChanges();
    flushAnimationFrame();
    expect(button().classList.contains('buzzer-pulse')).toBe(true);

    vm.update((value) => ({
      ...value,
      draft: { ...value.draft, buttonCode: '' },
      canAddTeam: false,
    }));
    fixture.detectChanges();

    expect(button().classList.contains('buzzer-pulse')).toBe(false);
    expect(animationFrames.size).toBe(0);
  });

  it('does not animate before a buzzer pulse has been observed', () => {
    const button = fixture.nativeElement.querySelector('[data-testid="admin-create-team-button"]');

    expect(fixture.componentInstance.buzzerPulsing()).toBe(false);
    expect(button.classList.contains('buzzer-pulse')).toBe(false);
  });
});
