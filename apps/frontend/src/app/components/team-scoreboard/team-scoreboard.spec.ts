import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TeamScoreboard } from './team-scoreboard';

describe('TeamScoreboard', () => {
  let component: TeamScoreboard;
  let fixture: ComponentFixture<TeamScoreboard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TeamScoreboard]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TeamScoreboard);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
