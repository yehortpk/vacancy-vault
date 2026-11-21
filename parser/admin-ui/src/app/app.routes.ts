import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { ParserProgressComponent } from './parser-progress/parser-progress.component';
import { sessionGuard } from './guard/session.guard';

/**
 * The page paths must not start with /parser, /login or /logout: the dev server forwards those
 * to the parser's API.
 */
export const routes: Routes = [
    { path: '', component: ParserProgressComponent, canActivate: [sessionGuard] },
    { path: 'sign-in', component: LoginComponent },
    { path: '**', redirectTo: '' },
];
