import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChartBuilder } from './chart-builder';

describe('ChartBuilder', () => {
  let component: ChartBuilder;
  let fixture: ComponentFixture<ChartBuilder>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChartBuilder],
    }).compileComponents();

    fixture = TestBed.createComponent(ChartBuilder);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
