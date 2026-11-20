import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CleaningReport } from './cleaning-report';

describe('CleaningReport', () => {
  let component: CleaningReport;
  let fixture: ComponentFixture<CleaningReport>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CleaningReport],
    }).compileComponents();

    fixture = TestBed.createComponent(CleaningReport);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
