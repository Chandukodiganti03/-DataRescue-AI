import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DatasetPreview } from './dataset-preview';

describe('DatasetPreview', () => {
  let component: DatasetPreview;
  let fixture: ComponentFixture<DatasetPreview>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DatasetPreview],
    }).compileComponents();

    fixture = TestBed.createComponent(DatasetPreview);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
