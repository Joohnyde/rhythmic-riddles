import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Tvstage1 } from './tvstage1';

describe('Tvstage1', () => {
  let component: Tvstage1;
  let fixture: ComponentFixture<Tvstage1>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Tvstage1]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Tvstage1);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
