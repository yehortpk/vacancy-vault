import { VacancyService } from '../service/vacancy.service';
import { VacanciesListDTO, Vacancy, VacancyConditions } from "../type/vacancy.type"

import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute, ParamMap, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';

import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { TruncatePipe } from '../pipe/truncate.pipe';
import { describeCountry, describePostedAt, formatCountries, formatSalary } from '../vacancy-display';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
  standalone: true,
  imports: [
    CommonModule,
    MatPaginatorModule,
    TruncatePipe,
    RouterModule
  ]
})
export class DashboardComponent implements OnInit {
  vacancies: Vacancy[] = []
  currentPage!: number
  pageSize!: number
  totalVacancies: number = 0
  totalPages: number = 0
  searchQuery: string = ''
  isLoading: boolean = true
  /** Set from the `remote`, `withSalary` and `country` query params, so they survive a reload or a shared link. */
  conditions: VacancyConditions = { remoteRequired: false, salaryRequired: false, country: null }
  /** ISO codes the country picker offers: those of the listed vacancies. */
  countries: string[] = []


  constructor(
    private vacancyService: VacancyService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.vacancyService.getCountries().subscribe({
      next: (countries) => this.countries = countries,
      error: (error) => console.error('An error occurred:', error)
    });
    this.route.queryParamMap.subscribe(params => {
      this.requestVacancies(params);
    });
  }

  requestVacancies(params: ParamMap) {
    this.currentPage = parseInt(params.get('page') || '1');
    this.pageSize = parseInt(params.get('pageSize') || '10');
    let queryParam = params.get('query') || '';
    this.searchQuery = queryParam;
    this.conditions = {
      remoteRequired: params.get('remote') === 'true',
      salaryRequired: params.get('withSalary') === 'true',
      country: params.get('country'),
    };
    this.isLoading = true;

    this.requestVacanciesOnPage(this.currentPage, this.pageSize, queryParam);
  }

  requestVacanciesOnPage(pageId: number, pageSize: number, queryParam: string) {
    this.vacancyService.getVacancies(pageId, pageSize, queryParam, this.conditions).subscribe({
      next: (response) => {
        this.updatePage(response)
      },
      error: (error) => {
        this.isLoading = false;
        console.error('An error occurred:', error);
      }
    });
  }

  updatePage(response: VacanciesListDTO) {
    this.vacancies = response.vacancies;
    this.isLoading = false;

    this.totalPages = response.totalPages;
    this.totalVacancies = response.totalVacancies;
  }

  readonly formatSalary = formatSalary;
  readonly describePostedAt = describePostedAt;
  readonly describeCountry = describeCountry;
  readonly formatCountries = formatCountries;

  onPageChange(event: PageEvent) {
    this.router.navigate(["/dashboard"], {
      relativeTo: this.route,
      queryParams: { 
        page: event.pageIndex, 
        pageSize: event.pageSize
      },
      queryParamsHandling: 'merge'
    });
  }

  toggleRemoteRequired() {
    this.navigateWithConditions({ ...this.conditions, remoteRequired: !this.conditions.remoteRequired });
  }

  toggleSalaryRequired() {
    this.navigateWithConditions({ ...this.conditions, salaryRequired: !this.conditions.salaryRequired });
  }

  selectCountry(country: string) {
    this.navigateWithConditions({ ...this.conditions, country: country || null });
  }

  hasActiveConditions(): boolean {
    return this.conditions.remoteRequired || this.conditions.salaryRequired || this.conditions.country !== null;
  }

  private navigateWithConditions(conditions: VacancyConditions) {
    this.router.navigate(["/dashboard"], {
      relativeTo: this.route,
      queryParams: {
        page: 0,
        remote: conditions.remoteRequired || null,
        withSalary: conditions.salaryRequired || null,
        country: conditions.country
      },
      queryParamsHandling: 'merge'
    });
  }
}
