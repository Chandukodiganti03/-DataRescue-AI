import { Component, ChangeDetectorRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import {
  Dataset,
  DatasetService
} from '../services/dataset.service';

@Component({
  selector: 'app-upload',
  imports: [
    CommonModule,
    RouterLink
  ],
  templateUrl: './upload.html',
  styleUrl: './upload.css'
})
export class Upload implements OnInit {

  selectedFile: File | null = null;

  result: Dataset | null = null;

  loading = false;

  errorMessage = '';

  constructor(
    private datasetService: DatasetService,
    private changeDetectorRef: ChangeDetectorRef
  ) {}

  ngOnInit(): void {

    this.loadLatestDataset();

  }

  loadLatestDataset(): void {

    console.log('Loading saved datasets...');

    this.datasetService
      .getDatasets()
      .subscribe({

        next: (datasets: Dataset[]) => {

          console.log(
            'Saved datasets:',
            datasets
          );

          if (datasets.length > 0) {

            this.result =
              datasets[datasets.length - 1];

          }

          this.changeDetectorRef.detectChanges();

        },

        error: (error) => {

          console.error(
            'Failed to load saved datasets:',
            error
          );

          this.errorMessage =
            'Failed to load saved dataset history.';

        }

      });
  }

  onFileSelected(event: Event): void {

    const input =
      event.target as HTMLInputElement;

    if (
      !input.files ||
      input.files.length === 0
    ) {
      return;
    }

    const file = input.files[0];

    const fileName =
      file.name.toLowerCase();

    if (
      !fileName.endsWith('.xlsx') &&
      !fileName.endsWith('.xls')
    ) {

      this.errorMessage =
        'Please select a valid Excel file (.xlsx or .xls).';

      this.selectedFile = null;

      return;
    }

    this.selectedFile = file;

    this.result = null;

    this.errorMessage = '';
  }

  analyzeFile(): void {

    if (!this.selectedFile) {

      this.errorMessage =
        'Please select an Excel file first.';

      return;
    }

    this.loading = true;

    this.errorMessage = '';

    this.result = null;

    console.log(
      'Starting dataset analysis:',
      this.selectedFile.name
    );

    this.datasetService
      .analyzeDataset(this.selectedFile)
      .subscribe({

        next: (response: Dataset) => {

          console.log(
            'Dataset analysis response:',
            response
          );

          this.result = response;

          this.loading = false;

          this.changeDetectorRef.detectChanges();

        },

        error: (error) => {

          console.error(
            'Dataset analysis failed:',
            error
          );

          this.loading = false;

          this.errorMessage =
            'Failed to analyze the dataset. Please make sure the backend is running.';

        },

        complete: () => {

          console.log(
            'Dataset analysis request completed.'
          );

        }

      });
  }

  formatFileSize(bytes: number): string {

    if (bytes === 0) {
      return '0 Bytes';
    }

    const units = [
      'Bytes',
      'KB',
      'MB',
      'GB'
    ];

    const index = Math.floor(
      Math.log(bytes) / Math.log(1024)
    );

    return (
      parseFloat(
        (
          bytes /
          Math.pow(1024, index)
        ).toFixed(2)
      )
      + ' '
      + units[index]
    );
  }
}