
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ChatResponse {
  answer: string;
}

// export interface DatasetInfo {
//   id: number;
//   fileName?: string;
//   filename?: string;
//   name?: string;
// }

export interface DatasetInfo {
  id: number;
  fileName?: string;
  filename?: string;
  name?: string;
  totalRows?: number;
  totalColumns?: number;
  missingValues?: number;
  duplicateRows?: number;
  qualityScore?: number;
}

@Injectable({
  providedIn: 'root'
})
export class Chatbot {
  private readonly apiUrl = 'http://localhost:8080/api/chat';

  constructor(private http: HttpClient) {}

  askDatasetQuestion(
    datasetId: number,
    message: string
  ): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(
      `${this.apiUrl}/dataset/${datasetId}`,
      { message }
    );
  }

  getDatasets(): Observable<DatasetInfo[]> {
  return this.http.get<DatasetInfo[]>(
    'http://localhost:8080/api/datasets'
  );
}

}
