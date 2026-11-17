import { Component, OnInit } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { VacancyService } from '../service/vacancy.service';
import { VacancyDetails } from '../type/vacancy.type';
import { describePostedAt, formatSalary } from '../vacancy-display';

/** One vacancy on a page of its own, with the description the dashboard's cards leave out. */
@Component({
  selector: 'app-vacancy',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './vacancy.component.html'
})
export class VacancyComponent implements OnInit {
  vacancy: VacancyDetails | null = null
  isLoading: boolean = true
  isNotFound: boolean = false
  hasFailed: boolean = false

  readonly formatSalary = formatSalary;
  readonly describePostedAt = describePostedAt;

  constructor(
    private vacancyService: VacancyService,
    private route: ActivatedRoute,
    private router: Router,
    private location: Location
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const vacancyId = Number(params.get('id'));
      if (!Number.isInteger(vacancyId)) {
        this.isLoading = false;
        this.isNotFound = true;
        return;
      }
      this.requestVacancy(vacancyId);
    });
  }

  requestVacancy(vacancyId: number) {
    this.isLoading = true;
    this.isNotFound = false;
    this.hasFailed = false;
    this.vacancyService.getVacancy(vacancyId).subscribe({
      next: (vacancy) => {
        this.vacancy = vacancy;
        this.isLoading = false;
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        if (error.status === 404) {
          this.isNotFound = true;
        } else {
          this.hasFailed = true;
          console.error('An error occurred:', error);
        }
      }
    });
  }

  /** Back to the listing as it was left (page, filter), or to the dashboard when opened directly. */
  navigateBack() {
    if (this.router.lastSuccessfulNavigation?.previousNavigation) {
      this.location.back();
    } else {
      this.router.navigate(['/dashboard']);
    }
  }
}
