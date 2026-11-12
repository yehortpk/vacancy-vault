import { VacancyService } from '../service/vacancy.service';
import { VacanciesListDTO, Vacancy } from "../type/vacancy.type"

import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute, ParamMap, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';

import { MatSortModule, Sort } from '@angular/material/sort';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatRadioModule } from '@angular/material/radio';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { FormsModule } from '@angular/forms';
import { TruncatePipe } from '../pipe/truncate.pipe';
import { MatSelectChange, MatSelectModule } from '@angular/material/select';
import { Filter } from '../type/filter.type';
import { FilterService } from '../service/filter.service';
import { AuthService } from '../service/auth.service';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
  standalone: true,
  imports: [
    CommonModule, 
    MatTableModule, 
    MatSortModule, 
    MatPaginatorModule, 
    MatRadioModule,
    FormsModule,
    MatMenuModule,
    MatButtonModule,
    TruncatePipe,
    MatSelectModule,
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

  displayedColumns: string[] = ['title', 'minSalary', 'maxSalary', 'company', 'parsedAt'];
  dataSource: MatTableDataSource<Vacancy> = new MatTableDataSource();

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
    let sortBy = params.get('sortBy') || '';
    let sortDir = params.get('sortDir') || '';
    let filterParam = params.get('filter');
    let queryParam = params.get('query') || '';
    this.selectedFilterId = filterParam ? Number.parseInt(filterParam) : null;

    if (filterParam) {
      this.requestVacanciesByFilter(Number.parseInt(filterParam), this.currentPage, this.pageSize, sortBy, sortDir);
    } else {
      this.requestVacanciesOnPage(this.currentPage, this.pageSize, sortBy, sortDir, queryParam);
    }
  }

  requestVacanciesByFilter(filterId: number, pageId: number, pageSize: number, sortBy: string, sortDir: string) {
    this.filterService.getVacanciesByFilter(filterId, pageId, pageSize, sortBy, sortDir).subscribe({
      next: (response) => {
        this.updatePage(response)
      },
      error: (error) => {
        console.error('An error occurred:', error);
      }
    });
  }

  requestVacanciesOnPage(pageId: number, pageSize: number, sortBy: string, sortDir: string, queryParam: string) {
    this.vacancyService.getVacancies(pageId, pageSize, sortBy, sortDir, queryParam).subscribe({
      next: (response) => {
        this.updatePage(response)
      },
      error: (error) => {
        console.error('An error occurred:', error);
      }
    });
  }

  updatePage(response: VacanciesListDTO) {
    response.vacancies.forEach((vacancy, _, __) => {
      if(!vacancy.company.imageUrl) {
        const dummyPictures = [
          '/img/logo/dummyA.png',
          '/img/logo/dummyB.png', 
          '/img/logo/dummyC.png', 
          '/img/logo/dummyD.png', 
          '/img/logo/dummyE.png', 
          '/img/logo/dummyF.png', 
          '/img/logo/dummyG.png'
        ];
        vacancy.company.imageUrl = dummyPictures[vacancy.company.company_id % dummyPictures.length];
      }

      if (vacancy.minSalary == 0) {
        vacancy.minSalary = "Not specified"
      }

      if (vacancy.maxSalary == 0) {
        vacancy.maxSalary = "Not specified"
      }

    });
    this.vacancies = response.vacancies;

    this.dataSource = new MatTableDataSource(this.vacancies)

    this.totalPages = response.totalPages;
    this.totalVacancies = response.totalVacancies;
  }

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

  onSortChange(event: Sort) {
    this.router.navigate(["/dashboard"], {
      relativeTo: this.route,
      queryParams: { 
        page: 0,
        sortBy: event.active,
        sortDir: event.direction.toUpperCase()
      },
      queryParamsHandling: 'merge'
    });
  }

  onFilterChange(event: MatSelectChange) {
    this.router.navigate(["/dashboard"], {
      relativeTo: this.route,
      queryParams: {
        filter: event.value
      }
    });
  }
}
