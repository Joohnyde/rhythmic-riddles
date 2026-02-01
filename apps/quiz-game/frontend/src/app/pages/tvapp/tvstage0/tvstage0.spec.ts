import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TVStage0 } from './tvstage0';

describe('TVStage0', () => {
  let component: TVStage0;
  let fixture: ComponentFixture<TVStage0>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TVStage0]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TVStage0);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
