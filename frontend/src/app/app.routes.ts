import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard/dashboard.component';
import { VacancyComponent } from './vacancy/vacancy.component';
import { AuthFormComponent } from './auth-form/auth-form.component';
import { AccountComponent } from './account/account.component';
import { FiltersComponent } from './filters/filters.component';
import { ForbiddenComponent } from './forbidden/forbidden.component';
import { NotFoundComponent } from './not-found/not-found.component';
import { authGuard } from './guard/auth.guard';

export const routes: Routes = [
    { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
    // Public: guests browse vacancies without an account
    { path: 'dashboard', component: DashboardComponent },
    { path: 'vacancy/:id', component: VacancyComponent },
    { path: 'login', component: AuthFormComponent, data: { mode: 'login' } },
    { path: 'register', component: AuthFormComponent, data: { mode: 'register' } },
    // Any signed-in account
    { path: 'account', component: AccountComponent, canActivate: [authGuard] },
    { path: 'filters', component: FiltersComponent, canActivate: [authGuard] },
    { path: 'forbidden', component: ForbiddenComponent },
    // Must stay last: matches whatever no route above did
    { path: '**', component: NotFoundComponent },
];
