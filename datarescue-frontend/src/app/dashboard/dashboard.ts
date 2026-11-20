import { Component, ChangeDetectorRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import {
  Dataset,
  DatasetService
} from '../services/dataset.service';

@Component({
  selector: 'app-dashboard',
  imports: [
    CommonModule,
    RouterLink
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {

  datasets: Dataset[] = [];

  loading = false;

  errorMessage = '';

  constructor(
    private datasetService: DatasetService,
    private router: Router,
    private changeDetectorRef: ChangeDetectorRef
  ) {}

  ngOnInit(): void {

    this.loadDatasets();

  }

  loadDatasets(): void {

    this.loading = true;

    console.log('Loading datasets from backend...');

    this.datasetService
      .getDatasets()
      .subscribe({

        next: (response: Dataset[]) => {

          console.log(
            'Datasets loaded:',
            response
          );

          this.datasets = response
          .slice()
          .sort((a, b) => b.id - a.id)
          .slice(0, 5);

          this.loading = false;

          this.changeDetectorRef.detectChanges();

        },

        error: (error) => {

          console.error(
            'Failed to load datasets:',
            error
          );

          this.loading = false;

          this.errorMessage =
            'Failed to load datasets. Please make sure the backend is running.';

        }

      });
  }

  viewDataset(id: number): void {

    this.router.navigate([
      '/dataset',
      id
    ]);

  }

  deleteDataset(id: number): void {

    const confirmed =
      window.confirm(
        'Are you sure you want to delete this dataset?'
      );

    if (!confirmed) {
      return;
    }

    this.datasetService
      .deleteDataset(id)
      .subscribe({

        next: () => {

          console.log(
            'Dataset deleted:',
            id
          );

          this.loadDatasets();

        },

        error: (error) => {

          console.error(
            'Failed to delete dataset:',
            error
          );

          this.errorMessage =
            'Failed to delete the dataset.';

        }

      });
  }

}