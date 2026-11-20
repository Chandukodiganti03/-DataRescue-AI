import { Component, ChangeDetectorRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import {
  Dataset,
  DatasetService
} from '../services/dataset.service';

@Component({
  selector: 'app-dataset-detail',
  imports: [
    CommonModule,
    RouterLink
  ],
  templateUrl: './dataset-detail.html',
  styleUrl: './dataset-detail.css'
})
export class DatasetDetail implements OnInit {

  dataset: Dataset | null = null;

  loading = false;

  errorMessage = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private datasetService: DatasetService,
    private changeDetectorRef: ChangeDetectorRef
  ) {}

  ngOnInit(): void {

    const id =
      Number(this.route.snapshot.paramMap.get('id'));

    if (!id) {

      this.errorMessage =
        'Invalid dataset ID.';

      return;
    }

    this.loadDataset(id);
  }

  loadDataset(id: number): void {

    this.loading = true;

    console.log(
      'Loading dataset:',
      id
    );

    this.datasetService
      .getDatasetById(id)
      .subscribe({

        next: (response: Dataset) => {

          console.log(
            'Dataset loaded:',
            response
          );

          this.dataset = response;

          this.loading = false;

          this.changeDetectorRef.detectChanges();
        },

        error: (error) => {

          console.error(
            'Failed to load dataset:',
            error
          );

          this.loading = false;

          this.errorMessage =
            'Failed to load dataset details.';
        }

      });
  }

    previewDataset(): void {

    const id =
      Number(this.route.snapshot.paramMap.get('id'));

    if (!id) {

      this.errorMessage =
        'Invalid dataset ID.';

      return;
    }

    console.log(
      'Opening dataset preview:',
      id
    );

    this.router.navigate([
      '/dataset',
      id,
      'preview'
    ]);
  }

cleanDataset(): void {

  const id =
    Number(this.route.snapshot.paramMap.get('id'));

  if (!id) {

    this.errorMessage =
      'Invalid dataset ID.';

    return;
  }

  this.loading = true;

  this.errorMessage = '';

  console.log(
    'Cleaning dataset:',
    id
  );

  this.datasetService
    .cleanDataset(id)
    .subscribe({

      next: (file: Blob) => {

        console.log(
          'Cleaned dataset received.'
        );

        const downloadUrl =
          window.URL.createObjectURL(file);

        const link =
          document.createElement('a');

        link.href = downloadUrl;

        link.download =
          'cleaned_dataset.xlsx';

        document.body.appendChild(link);

        link.click();

        document.body.removeChild(link);

        window.URL.revokeObjectURL(
          downloadUrl
        );

        this.loading = false;

        this.changeDetectorRef.detectChanges();

        this.router.navigate([
          '/dataset',
          id,
          'report'
        ]);
      },

      error: (error) => {

        console.error(
          'Dataset cleaning failed:',
          error
        );

        this.loading = false;

        this.errorMessage =
          'Failed to clean the dataset.';

        this.changeDetectorRef.detectChanges();
      }

    });
}

}