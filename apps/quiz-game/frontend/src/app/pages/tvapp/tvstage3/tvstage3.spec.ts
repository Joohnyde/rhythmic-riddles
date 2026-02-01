import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Tvstage3 } from './tvstage3';

describe('Tvstage3', () => {
  let component: Tvstage3;
  let fixture: ComponentFixture<Tvstage3>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Tvstage3]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Tvstage3);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
