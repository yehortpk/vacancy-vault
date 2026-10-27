import { Injectable, computed, signal } from '@angular/core';
import { HttpClient, HttpContext, HttpContextToken } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { Account, TokenPair } from '../type/account.type';
import { environment } from '../../environment';

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

/**
 * Set to false on a request whose failed refresh should just fail, not send the user to the
 * login page — the startup account check, which also runs for guests on the dashboard.
 */
export const REDIRECT_ON_EXPIRED_SESSION = new HttpContextToken<boolean>(() => true);

/**
 * Holds the signed-in account and its tokens.
 *
 * The role kept here only decides which rooms the UI offers; the router checks it again
 * on every request, so editing it in the browser opens nothing.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private baseUrl = environment.router_service_url;
  private refreshInFlight: Observable<TokenPair> | null = null;

  readonly account = signal<Account | null>(null);
  readonly isAuthenticated = computed(() => this.account() !== null);
  readonly isAdmin = computed(() => this.account()?.role === 'ADMIN');

  constructor(private http: HttpClient, private router: Router) { }

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  private storeTokens(tokens: TokenPair): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  }

  private clearSession(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.account.set(null);
  }

  register(email: string, password: string): Observable<Account | null> {
    return this.http.post<TokenPair>(`${this.baseUrl}/auth/register`, { email, password }).pipe(
      tap((tokens) => this.storeTokens(tokens)),
      switchMap(() => this.loadAccount())
    );
  }

  login(email: string, password: string): Observable<Account | null> {
    return this.http.post<TokenPair>(`${this.baseUrl}/auth/login`, { email, password }).pipe(
      tap((tokens) => this.storeTokens(tokens)),
      switchMap(() => this.loadAccount())
    );
  }

  /**
   * Exchanges the refresh token for a new pair. Requests that fail together share one
   * exchange instead of each starting its own.
   */
  refresh(): Observable<TokenPair> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }

    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<TokenPair>(`${this.baseUrl}/auth/refresh`, { refreshToken }).pipe(
        tap((tokens) => this.storeTokens(tokens)),
        finalize(() => this.refreshInFlight = null),
        shareReplay(1)
      );
    }
    return this.refreshInFlight;
  }

  /** Reads the account behind the stored token; an unusable token ends the session. */
  loadAccount(): Observable<Account | null> {
    if (!this.getAccessToken()) {
      this.account.set(null);
      return of(null);
    }

    const context = new HttpContext().set(REDIRECT_ON_EXPIRED_SESSION, false);
    return this.http.get<Account>(`${this.baseUrl}/me`, { context }).pipe(
      tap((account) => this.account.set(account)),
      catchError(() => {
        this.clearSession();
        return of(null);
      })
    );
  }

  /** The account already in memory, or loaded once after a page reload. */
  findAccount(): Observable<Account | null> {
    const account = this.account();
    return account ? of(account) : this.loadAccount();
  }

  logout(): void {
    this.clearSession();
    this.router.navigate(['/dashboard']);
  }

  expireSession(): void {
    this.clearSession();
    this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
  }
}
