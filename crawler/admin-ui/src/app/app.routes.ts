import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { CrawlerProgressComponent } from './crawler-progress/crawler-progress.component';
import { sessionGuard } from './guard/session.guard';

/**
 * The page paths must not start with /crawler, /login or /logout: the dev server forwards those
 * to the crawler's API.
 */
export const routes: Routes = [
    { path: '', component: CrawlerProgressComponent, canActivate: [sessionGuard] },
    { path: 'sign-in', component: LoginComponent },
    { path: '**', redirectTo: '' },
];
