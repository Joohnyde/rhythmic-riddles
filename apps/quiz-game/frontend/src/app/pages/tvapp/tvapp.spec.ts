import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Tvapp } from './tvapp';

describe('Tvapp', () => {
  let component: Tvapp;
  let fixture: ComponentFixture<Tvapp>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Tvapp]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Tvapp);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
