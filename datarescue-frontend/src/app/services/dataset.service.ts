
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Dataset {
  id: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  totalRows: number;
  totalColumns: number;
  missingValues: number;
  duplicateRows: number;
  qualityScore: number;
  uploadedAt: string;
}

export interface DatasetPreview {
  fileName: string;
  columns: string[];
  rows: string[][];
}

export interface ColumnIssue {
  columnName: string;
  missingCount: number;
  missingPercentage: number;
}

export interface CleaningReport {
  fileName: string;
  originalRows: number;
  cleanedRows: number;
  originalColumns: number;
  cleanedColumns: number;
  removedColumns: number;
  repeatedHeaderRows: number;
  duplicateRows: number;
  originalQualityScore: number;
  cleanedQualityScore: number;
  qualityImprovement: number;
  qualityStatus: string;
  qualityExplanation: string;
  missingValues: number;
  duplicateRecordsDetected: number;
  emptyColumnsDetected: number;
  columnsWithMissingValues: ColumnIssue[];
  recommendations: string[];
}

@Injectable({
  providedIn: 'root'
})
export class DatasetService {

  private apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  analyzeDataset(file: File): Observable<Dataset> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<Dataset>(
      `${this.apiUrl}/excel/analyze`,
      formData
    );
  }

  getDatasets(): Observable<Dataset[]> {
    return this.http.get<Dataset[]>(
      `${this.apiUrl}/datasets`
    );
  }

  getDatasetById(id: number): Observable<Dataset> {
    return this.http.get<Dataset>(
      `${this.apiUrl}/datasets/${id}`
    );
  }

  previewDataset(id: number): Observable<DatasetPreview> {
    return this.http.get<DatasetPreview>(
      `${this.apiUrl}/excel/preview/${id}`
    );
  }

  deleteDataset(id: number): Observable<string> {
    return this.http.delete(
      `${this.apiUrl}/datasets/${id}`,
      { responseType: 'text' }
    );
  }

  cleanDataset(id: number): Observable<Blob> {
    return this.http.get(
      `${this.apiUrl}/clean/${id}`,
      { responseType: 'blob' }
    );
  }

  downloadCleanedDataset(id: number): Observable<Blob> {
    return this.http.get(
      `${this.apiUrl}/clean/${id}`,
      { responseType: 'blob' }
    );
  }

  getCleaningReport(id: number): Observable<CleaningReport> {
    return this.http.get<CleaningReport>(
      `${this.apiUrl}/clean/${id}/report`
    );
  }
}
