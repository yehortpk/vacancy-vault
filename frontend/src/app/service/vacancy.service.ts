import { Injectable } from '@angular/core';
import { VacanciesListDTO, VacancyConditions, VacancyDetails, createConditionParams } from '../type/vacancy.type';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environment';

/** The public vacancy listing, read from the dashboard service; no account is involved. */
@Injectable({
  providedIn: 'root'
})
export class VacancyService {
  private baseUrl = environment.dashboard_service_url + "/vacancy";

  constructor(private http: HttpClient) { }

  getVacancies(pageId: number, pageSize: number, queryParam: string,
               conditions: VacancyConditions): Observable<VacanciesListDTO> {
    return this.http.get<VacanciesListDTO>(this.baseUrl, {params: {"page": pageId, "pageSize": pageSize, "query": queryParam,
      ...createConditionParams(conditions)}});
  }

  getVacancy(vacancyId: number): Observable<VacancyDetails> {
    return this.http.get<VacancyDetails>(`${this.baseUrl}/${vacancyId}`);
  }
}
