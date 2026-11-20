
import { Component, ChangeDetectorRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import {
  CleaningReport as CleaningReportData,
  DatasetService
} from '../services/dataset.service';

@Component({
  selector: 'app-cleaning-report',
  imports: [
    CommonModule,
    RouterLink
  ],
  templateUrl: './cleaning-report.html',
  styleUrl: './cleaning-report.css'
})
export class CleaningReport implements OnInit {

  report: CleaningReportData | null = null;

  loading = false;
  downloading = false;
  errorMessage = '';
  downloadMessage = '';

  private datasetId = 0;

  constructor(
    private route: ActivatedRoute,
    private datasetService: DatasetService,
    private changeDetectorRef: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.datasetId = Number(
      this.route.snapshot.paramMap.get('id')
    );

    if (!this.datasetId) {
      this.errorMessage = 'Invalid dataset ID.';
      return;
    }

    this.loadReport(this.datasetId);
  }

  loadReport(id: number): void {
    this.loading = true;
    this.errorMessage = '';

    this.datasetService.getCleaningReport(id).subscribe({
      next: (response: CleaningReportData) => {
        this.report = response;
        this.loading = false;
        this.changeDetectorRef.detectChanges();
      },
      error: (error) => {
        console.error('Failed to load cleaning report:', error);
        this.loading = false;
        this.errorMessage = 'Failed to generate cleaning report.';
        this.changeDetectorRef.detectChanges();
      }
    });
  }

  downloadCleanedDataset(): void {
    if (!this.datasetId || this.downloading) {
      return;
    }

    this.downloading = true;
    this.downloadMessage = '';
    this.errorMessage = '';

    this.datasetService
      .downloadCleanedDataset(this.datasetId)
      .subscribe({
        next: (file: Blob) => {
          if (!file || file.size === 0) {
            this.errorMessage = 'The cleaned file is empty.';
            this.downloading = false;
            this.changeDetectorRef.detectChanges();
            return;
          }

          const url = URL.createObjectURL(file);
          const link = document.createElement('a');

          const originalName =
            this.report?.fileName || 'dataset.xlsx';

          const baseName = originalName.replace(/\.[^.]+$/, '');

          link.href = url;
          link.download = `${baseName}_cleaned.xlsx`;

          document.body.appendChild(link);
          link.click();
          link.remove();

          URL.revokeObjectURL(url);

          this.downloadMessage = 'Cleaned dataset downloaded successfully.';
          this.downloading = false;
          this.changeDetectorRef.detectChanges();
        },

        error: (error) => {
          console.error('Failed to download cleaned dataset:', error);

          this.errorMessage =
            'Failed to download the cleaned dataset. Check whether the backend is running.';

          this.downloading = false;
          this.changeDetectorRef.detectChanges();
        }
      });
  }
}
