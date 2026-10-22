import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SeekTimerComponent } from './seek-timer.component';

/** The Admin owns completion; timer progression must not depend on a TV finished event. */
describe('SeekTimerComponent countdown lifecycle', () => {
  let fixture: ComponentFixture<SeekTimerComponent>;
  let timer: SeekTimerComponent;
  let elapsedMs: number;

  beforeEach(async () => {
    vi.useFakeTimers();
    elapsedMs = 0;
    vi.spyOn(performance, 'now').mockImplementation(() => elapsedMs);
    await TestBed.configureTestingModule({ imports: [SeekTimerComponent] }).compileComponents();
    fixture = TestBed.createComponent(SeekTimerComponent);
    timer = fixture.componentInstance;
  });

  afterEach(() => {
    fixture.destroy();
    vi.restoreAllMocks();
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  function advance(milliseconds: number): void {
    elapsedMs += milliseconds;
    vi.advanceTimersByTime(milliseconds);
  }

  it('finishes at zero exactly once even when additional ticks would occur', () => {
    const completed = vi.fn();
    timer.completed.subscribe(completed);
    fixture.componentRef.setInput('duration', 2);
    fixture.componentRef.setInput('seek', 0);
    fixture.detectChanges();
    advance(900);
    expect(timer.remainingTime()).toBeCloseTo(1.1, 1);
    expect(completed).not.toHaveBeenCalled();
    advance(1200);
    expect(timer.remainingTime()).toBe(0);
    expect(completed).toHaveBeenCalledTimes(1);
    advance(5000);
    expect(completed).toHaveBeenCalledTimes(1);
  });

  it('resumes from backend seek instead of counting from the beginning', () => {
    const completed = vi.fn();
    timer.completed.subscribe(completed);
    fixture.componentRef.setInput('duration', 10);
    fixture.componentRef.setInput('seek', 7);
    fixture.detectChanges();
    expect(timer.currentTime()).toBe(7);
    advance(1900);
    expect(timer.currentTime()).toBeCloseTo(8.9, 1);
    expect(completed).not.toHaveBeenCalled();
    advance(1200);
    expect(completed).toHaveBeenCalledOnce();
    expect(timer.currentTime()).toBe(10);
  });

  it('restored seek beyond total duration finishes without waiting for a tick', () => {
    const completed = vi.fn();
    timer.completed.subscribe(completed);
    fixture.componentRef.setInput('duration', 3);
    fixture.componentRef.setInput('seek', 6);
    fixture.detectChanges();
    expect(timer.currentTime()).toBe(3);
    expect(completed).toHaveBeenCalledOnce();
  });

  it('stopping for a pause emits the saved position and never finishes afterwards', () => {
    const completed = vi.fn();
    const snapshot = vi.fn();
    timer.completed.subscribe(completed);
    timer.stateOut.subscribe(snapshot);
    fixture.componentRef.setInput('duration', 10);
    fixture.componentRef.setInput('seek', 2);
    fixture.detectChanges();
    advance(1500);
    fixture.destroy();
    expect(snapshot).toHaveBeenCalledTimes(1);
    expect(snapshot.mock.lastCall?.[0].seek).toBeCloseTo(3.5, 1);
    expect(snapshot.mock.lastCall?.[0].remaining).toBeCloseTo(6.5, 1);
    advance(10000);
    expect(completed).not.toHaveBeenCalled();
  });

  it('does not schedule a countdown for zero or nonfinite duration', () => {
    const completed = vi.fn();
    timer.completed.subscribe(completed);
    fixture.componentRef.setInput('duration', Number.NaN);
    fixture.detectChanges();
    advance(3000);
    expect(timer.currentTime()).toBe(0);
    expect(completed).not.toHaveBeenCalled();
  });
});
