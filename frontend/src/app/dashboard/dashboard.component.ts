import { VacancyService } from '../service/vacancy.service';
import { VacanciesListDTO, Vacancy, VacancyConditions } from "../type/vacancy.type"

import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute, ParamMap, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';

import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { TruncatePipe } from '../pipe/truncate.pipe';
import { Filter } from '../type/filter.type';
import { FilterService } from '../service/filter.service';
import { AuthService } from '../service/auth.service';
import { describePostedAt, formatSalary } from '../vacancy-display';

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
  /** The signed-in user's own filters; guests browse every vacancy and get no dropdown. */
  filters: Filter[] = []
  selectedFilterId: number | null = null
  searchQuery: string = ''
  isLoading: boolean = true
  /** Set from the `remote` and `withSalary` query params, so the toggles survive a reload or a shared link. */
  conditions: VacancyConditions = { remoteRequired: false, salaryRequired: false }


  constructor(
    
    private vacancyService: VacancyService,
    private filterService: FilterService,
    protected authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      this.requestVacancies(params);
    });

    this.requestFilters()
  }

  requestFilters() {
    this.authService.findAccount().subscribe((account) => {
      if (!account) {
        this.filters = [];
        return;
      }
      this.filterService.getFilters().subscribe({
        next: (response) => {
          this.filters = response;
        },
        error: (error) => {
          console.error('An error occurred:', error);
        }
      });
    });
  }

  requestVacancies(params: ParamMap) {
    this.currentPage = parseInt(params.get('page') || '1');
    this.pageSize = parseInt(params.get('pageSize') || '10');
    let filterParam = params.get('filter');
    let queryParam = params.get('query') || '';
    this.selectedFilterId = filterParam ? Number.parseInt(filterParam) : null;
    this.searchQuery = queryParam;
    this.conditions = {
      remoteRequired: params.get('remote') === 'true',
      salaryRequired: params.get('withSalary') === 'true',
    };
    this.isLoading = true;

    if (filterParam) {
      this.requestVacanciesByFilter(Number.parseInt(filterParam), this.currentPage, this.pageSize);
    } else {
      this.requestVacanciesOnPage(this.currentPage, this.pageSize, queryParam);
    }
  }

  requestVacanciesByFilter(filterId: number, pageId: number, pageSize: number) {
    this.filterService.getVacanciesByFilter(filterId, pageId, pageSize, this.conditions).subscribe({
      next: (response) => {
        this.updatePage(response)
      },
      error: (error) => {
        this.isLoading = false;
        console.error('An error occurred:', error);
      }
    });
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

  onFilterChange(filterId: string) {
    // A new filter starts over, except for the remote/salary toggles, which apply on top of any filter
    this.router.navigate(["/dashboard"], {
      relativeTo: this.route,
      queryParams: {
        filter: filterId || null,
        remote: this.conditions.remoteRequired || null,
        withSalary: this.conditions.salaryRequired || null
      }
    });
  }

  toggleRemoteRequired() {
    this.navigateWithConditions({ ...this.conditions, remoteRequired: !this.conditions.remoteRequired });
  }

  toggleSalaryRequired() {
    this.navigateWithConditions({ ...this.conditions, salaryRequired: !this.conditions.salaryRequired });
  }

  hasActiveConditions(): boolean {
    return this.conditions.remoteRequired || this.conditions.salaryRequired;
  }

  private navigateWithConditions(conditions: VacancyConditions) {
    this.router.navigate(["/dashboard"], {
      relativeTo: this.route,
      queryParams: {
        page: 0,
        remote: conditions.remoteRequired || null,
        withSalary: conditions.salaryRequired || null
      },
      queryParamsHandling: 'merge'
    });
  }
}
