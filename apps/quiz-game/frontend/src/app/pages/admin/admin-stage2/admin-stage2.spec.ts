import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminStage2 } from './admin-stage2';

describe('AdminStage2', () => {
  let component: AdminStage2;
  let fixture: ComponentFixture<AdminStage2>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminStage2]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminStage2);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
