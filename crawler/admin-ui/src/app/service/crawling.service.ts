import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CrawlingProgress, CrawlingRunSummary } from '../type/crawling-progress.type';

@Injectable({
  providedIn: 'root'
})
export class CrawlingService {
  private baseUrl = '/crawler';

  constructor(private http: HttpClient) { }

  startCrawling(): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/start`, {});
  }

  stopCrawling(): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/stop`, {});
  }

  getProgress(): Observable<CrawlingProgress> {
    return this.http.get<CrawlingProgress>(`${this.baseUrl}/progress`);
  }

  getCrawlingRuns(): Observable<CrawlingRunSummary[]> {
    return this.http.get<CrawlingRunSummary[]>(`${this.baseUrl}/history`);
  }

  getCrawlingRun(crawlingHash: string): Observable<CrawlingProgress> {
    return this.http.get<CrawlingProgress>(`${this.baseUrl}/progress/${encodeURIComponent(crawlingHash)}`);
  }
}
