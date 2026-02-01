import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminStage0 } from './admin-stage0';

describe('AdminStage0', () => {
  let component: AdminStage0;
  let fixture: ComponentFixture<AdminStage0>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminStage0]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminStage0);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
