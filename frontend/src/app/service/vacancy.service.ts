import { Injectable } from '@angular/core';
import { VacanciesListDTO, VacancyConditions, createConditionParams } from '../type/vacancy.type';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environment';

@Injectable({
  providedIn: 'root'
})
export class VacancyService {
  constructor(private http: HttpClient) { }
  
  getVacancies(pageId: number, pageSize: number, sortBy: string, sortDir: string, queryParam: string,
               conditions: VacancyConditions): Observable<VacanciesListDTO> {
    const ROUTER_URL = environment.router_service_url + "/vacancy";
    return this.http.get<VacanciesListDTO>(ROUTER_URL, {params: {"page": pageId, "pageSize": pageSize, "sortBy": sortBy, "sortDir": sortDir, "query": queryParam,
      ...createConditionParams(conditions)}});
  }
}