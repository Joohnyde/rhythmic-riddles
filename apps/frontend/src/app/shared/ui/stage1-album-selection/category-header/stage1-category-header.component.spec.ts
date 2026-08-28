import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { Stage1CategoryHeaderComponent } from './stage1-category-header.component';

describe('Stage1CategoryHeaderComponent', () => {
  let fixture: ComponentFixture<Stage1CategoryHeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Stage1CategoryHeaderComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(Stage1CategoryHeaderComponent);
    fixture.componentRef.setInput('roomCode', 'AKKU');
    fixture.componentRef.setInput('loaded', true);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('shows the current team picker name and icon while albums are selectable', () => {
    fixture.componentRef.setInput('pickedByTeam', {
      id: 'team-a',
      name: 'Tempo',
      image: '/team-icons/team-a.png',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Now picking:');
    expect(fixture.nativeElement.textContent).toContain('Tempo');
    expect(fixture.nativeElement.querySelector('img')?.getAttribute('src')).toBe(
      '/team-icons/team-a.png',
    );
  });

  it('renders Admin as the picker when no team is choosing', () => {
    fixture.componentRef.setInput('pickedByTeam', null);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Now picking:');
    expect(fixture.nativeElement.textContent).toContain('Admin');
  });

  it('hides picker details after an album is selected', () => {
    fixture.componentRef.setInput('pickedByTeam', {
      id: 'team-a',
      name: 'Tempo',
      image: '/team-icons/team-a.png',
    });
    fixture.componentRef.setInput('selected', true);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Now picking:');
    expect(fixture.nativeElement.textContent).not.toContain('Tempo');
  });
});
