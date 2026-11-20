import { Component, ChangeDetectorRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import {
  DatasetPreview,
  DatasetService
} from '../services/dataset.service';

@Component({
  selector: 'app-dataset-preview',
  imports: [
    CommonModule,
    RouterLink
  ],
  templateUrl: './dataset-preview.html',
  styleUrl: './dataset-preview.css'
})
export class DatasetPreviewComponent implements OnInit {

  preview: DatasetPreview | null = null;

  loading = false;

  errorMessage = '';

  constructor(
    private route: ActivatedRoute,
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

    this.loadPreview(id);
  }

  loadPreview(id: number): void {

    this.loading = true;

    this.errorMessage = '';

    console.log(
      'Loading dataset preview:',
      id
    );

    this.datasetService
      .previewDataset(id)
      .subscribe({

        next: (response: DatasetPreview) => {

          console.log(
            'Dataset preview loaded:',
            response
          );

          this.preview = response;

          this.loading = false;

          this.changeDetectorRef.detectChanges();
        },

        error: (error) => {

          console.error(
            'Failed to load dataset preview:',
            error
          );

          this.loading = false;

          this.errorMessage =
            'Failed to load dataset preview.';
        }

      });
  }

}