import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environment';
import { Company } from '../type/company.type';
import { VacanciesListDTO } from '../type/vacancy.type';

/** A company and its vacancies, read from the dashboard service; no account is involved. */
@Injectable({
  providedIn: 'root'
})
export class CompanyService {
  private baseUrl = environment.dashboard_service_url + "/company";

  constructor(private http: HttpClient) { }

  /** Answers 404 when no stored vacancy belongs to the company. */
  getCompany(companyId: number): Observable<Company> {
    return this.http.get<Company>(`${this.baseUrl}/${companyId}`);
  }

  getCompanyVacancies(companyId: number, pageId: number, pageSize: number): Observable<VacanciesListDTO> {
    return this.http.get<VacanciesListDTO>(`${this.baseUrl}/${companyId}/vacancies`,
      {params: {"page": pageId, "pageSize": pageSize}});
  }
}
