import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { PageEvent } from '@angular/material/paginator';
import { combineLatest } from 'rxjs';
import { CompanyService } from '../service/company.service';
import { Company } from '../type/company.type';
import { VacanciesListDTO, Vacancy } from '../type/vacancy.type';
import { VacancyListComponent } from '../vacancy-list/vacancy-list.component';

/** One company with its vacancies, listed like the dashboard's. */
@Component({
  selector: 'app-company',
  standalone: true,
  imports: [CommonModule, RouterModule, VacancyListComponent],
  templateUrl: './company.component.html'
})
export class CompanyComponent implements OnInit {
  company: Company | null = null
  isNotFound: boolean = false
  hasFailed: boolean = false
  vacancies: Vacancy[] = []
  /** Set from the `page` and `pageSize` query params, so they survive a reload or a shared link. */
  currentPage: number = 0
  pageSize: number = 10
  totalVacancies: number = 0
  isLoading: boolean = true

  constructor(
    private companyService: CompanyService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const companyId = Number(params.get('id'));
      if (!Number.isInteger(companyId)) {
        this.isNotFound = true;
        return;
      }
      this.requestCompany(companyId);
    });
    combineLatest([this.route.paramMap, this.route.queryParamMap]).subscribe(([params, queryParams]) => {
      const companyId = Number(params.get('id'));
      if (!Number.isInteger(companyId)) {
        return;
      }
      this.currentPage = parseInt(queryParams.get('page') || '0');
      this.pageSize = parseInt(queryParams.get('pageSize') || '10');
      this.requestVacancies(companyId);
    });
  }

  requestCompany(companyId: number) {
    this.company = null;
    this.isNotFound = false;
    this.hasFailed = false;
    this.companyService.getCompany(companyId).subscribe({
      next: (company) => this.company = company,
      error: (error: HttpErrorResponse) => {
        if (error.status === 404) {
          this.isNotFound = true;
        } else {
          this.hasFailed = true;
          console.error('An error occurred:', error);
        }
      }
    });
  }

  requestVacancies(companyId: number) {
    this.isLoading = true;
    this.companyService.getCompanyVacancies(companyId, this.currentPage, this.pageSize).subscribe({
      next: (response) => this.updatePage(response),
      error: (error) => {
        this.isLoading = false;
        console.error('An error occurred:', error);
      }
    });
  }

  updatePage(response: VacanciesListDTO) {
    this.vacancies = response.vacancies;
    this.totalVacancies = response.totalVacancies;
    this.isLoading = false;
  }

  onPageChange(event: PageEvent) {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: event.pageIndex,
        pageSize: event.pageSize
      },
      queryParamsHandling: 'merge'
    });
  }
}
