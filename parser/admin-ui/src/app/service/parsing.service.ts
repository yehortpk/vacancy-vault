import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ParsingProgress } from '../type/parsing-progress.type';

@Injectable({
  providedIn: 'root'
})
export class ParsingService {
  private baseUrl = '/parser';

  constructor(private http: HttpClient) { }

  startParsing(): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/start`, {});
  }

  stopParsing(): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/stop`, {});
  }

  getProgress(): Observable<ParsingProgress> {
    return this.http.get<ParsingProgress>(`${this.baseUrl}/progress`);
  }
}
