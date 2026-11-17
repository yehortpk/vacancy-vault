import { Injectable } from '@angular/core';
import { VacanciesListDTO, VacancyConditions, VacancyDetails, createConditionParams } from '../type/vacancy.type';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environment';

@Injectable({
  providedIn: 'root'
})
export class VacancyService {
  constructor(private http: HttpClient) { }
  
  getVacancies(pageId: number, pageSize: number, queryParam: string,
               conditions: VacancyConditions): Observable<VacanciesListDTO> {
    const ROUTER_URL = environment.router_service_url + "/vacancy";
    return this.http.get<VacanciesListDTO>(ROUTER_URL, {params: {"page": pageId, "pageSize": pageSize, "query": queryParam,
      ...createConditionParams(conditions)}});
  }

  getVacancy(vacancyId: number): Observable<VacancyDetails> {
    return this.http.get<VacancyDetails>(`${environment.router_service_url}/vacancy/${vacancyId}`);
  }
}
