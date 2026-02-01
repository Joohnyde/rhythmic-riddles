import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SeekTimer } from './seek-timer';

describe('SeekTimer', () => {
  let component: SeekTimer;
  let fixture: ComponentFixture<SeekTimer>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SeekTimer]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SeekTimer);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
