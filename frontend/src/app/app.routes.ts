import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ParserProgressComponent } from './parser-progress/parser-progress.component';
import { AuthFormComponent } from './auth-form/auth-form.component';
import { AccountComponent } from './account/account.component';
import { adminGuard, authGuard } from './guard/auth.guard';

export const routes: Routes = [
    { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
    // Public: guests browse vacancies without an account
    { path: 'dashboard', component: DashboardComponent },
    { path: 'login', component: AuthFormComponent, data: { mode: 'login' } },
    { path: 'register', component: AuthFormComponent, data: { mode: 'register' } },
    // Any signed-in account
    { path: 'account', component: AccountComponent, canActivate: [authGuard] },
    // Admins only
    { path: 'parser', component: ParserProgressComponent, canActivate: [adminGuard] },
];
