import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TVStage2 } from './tvstage2';

describe('TVStage2', () => {
  let component: TVStage2;
  let fixture: ComponentFixture<TVStage2>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TVStage2]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TVStage2);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
