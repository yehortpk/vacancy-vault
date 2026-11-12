import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Filter, FilterRequest } from '../type/filter.type';
import { VacanciesListDTO } from '../type/vacancy.type';
import { environment } from '../../environment';

/** The signed-in user's own filters; the router answers 404 for anyone else's. */
@Injectable({
  providedIn: 'root'
})
export class FilterService {
  private baseUrl = environment.router_service_url + "/me/filters";

  constructor(private http: HttpClient) { }

  getFilters(): Observable<Filter[]> {
    return this.http.get<Filter[]>(this.baseUrl);
  }

  getFilter(filterId: number): Observable<Filter> {
    return this.http.get<Filter>(`${this.baseUrl}/${filterId}`);
  }

  createFilter(request: FilterRequest): Observable<Filter> {
    return this.http.post<Filter>(this.baseUrl, request);
  }

  /** Replaces the title, the expression and the bindings as a whole. */
  updateFilter(filterId: number, request: FilterRequest): Observable<Filter> {
    return this.http.put<Filter>(`${this.baseUrl}/${filterId}`, request);
  }

  deleteFilter(filterId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${filterId}`);
  }

  getVacanciesByFilter(filterId: number, pageId: number, pageSize: number, sortBy: string, sortDir: string): Observable<VacanciesListDTO> {
    return this.http.get<VacanciesListDTO>(`${this.baseUrl}/${filterId}/vacancies`,
      {params: {"page": pageId, "pageSize": pageSize, "sortBy": sortBy, "sortDir": sortDir}});
  }
}
