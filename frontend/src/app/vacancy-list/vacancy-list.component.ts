import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { Vacancy } from '../type/vacancy.type';
import { describePostedAt, formatCountries, formatSalary } from '../vacancy-display';

/**
 * One page of vacancy cards with a paginator, a skeleton while loading, and the projected
 * content in place of the list when the page is empty.
 */
@Component({
  selector: 'app-vacancy-list',
  standalone: true,
  imports: [CommonModule, RouterModule, MatPaginatorModule],
  templateUrl: './vacancy-list.component.html'
})
export class VacancyListComponent {
  @Input({ required: true }) vacancies: Vacancy[] = []
  @Input({ required: true }) isLoading: boolean = true
  @Input({ required: true }) totalVacancies: number = 0
  @Input({ required: true }) currentPage: number = 0
  @Input({ required: true }) pageSize: number = 10
  @Output() pageChange = new EventEmitter<PageEvent>()

  readonly formatSalary = formatSalary;
  readonly describePostedAt = describePostedAt;
  readonly formatCountries = formatCountries;
}
