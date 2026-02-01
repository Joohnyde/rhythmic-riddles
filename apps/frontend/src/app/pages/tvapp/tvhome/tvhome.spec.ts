import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TVHome } from './tvhome';

describe('TVHome', () => {
  let component: TVHome;
  let fixture: ComponentFixture<TVHome>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TVHome]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TVHome);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
