import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminStage1 } from './admin-stage1';

describe('AdminStage1', () => {
  let component: AdminStage1;
  let fixture: ComponentFixture<AdminStage1>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminStage1]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminStage1);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
