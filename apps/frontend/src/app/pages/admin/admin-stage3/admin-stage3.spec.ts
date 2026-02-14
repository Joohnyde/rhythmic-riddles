import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminStage3 } from './admin-stage3';

describe('AdminStage3', () => {
  let component: AdminStage3;
  let fixture: ComponentFixture<AdminStage3>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminStage3]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminStage3);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
