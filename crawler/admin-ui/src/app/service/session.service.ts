import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, of, tap } from 'rxjs';
import { AdminSession } from '../type/session.type';

/**
 * Holds the logged-in admin.
 *
 * The session itself is the crawler's cookie, which script cannot read; what is kept here only
 * decides what the panel shows. The crawler checks the cookie again on every request.
 */
@Injectable({
  providedIn: 'root'
})
export class SessionService {
  readonly admin = signal<AdminSession | null>(null);

  constructor(private http: HttpClient, private router: Router) { }

  /**
   * Asks the crawler who the session belongs to; null without one. Whatever the answer, the
   * response carries the XSRF-TOKEN cookie a following login needs.
   */
  loadSession(): Observable<AdminSession | null> {
    return this.http.get<AdminSession>('/crawler/session').pipe(
      tap((admin) => this.admin.set(admin)),
      catchError(() => {
        this.admin.set(null);
        return of(null);
      })
    );
  }

  /** The admin already in memory, or loaded once after a page reload. */
  findSession(): Observable<AdminSession | null> {
    const admin = this.admin();
    return admin ? of(admin) : this.loadSession();
  }

  login(username: string, password: string): Observable<AdminSession> {
    return this.http.post<AdminSession>('/login', { username, password }).pipe(
      tap((admin) => this.admin.set(admin))
    );
  }

  /** Leaves the panel even if the crawler could not be told: the page no longer shows the room. */
  logout(): void {
    this.http.post('/logout', null).subscribe({
      next: () => this.expireSession(),
      error: () => this.expireSession()
    });
  }

  expireSession(): void {
    this.admin.set(null);
    this.router.navigate(['/sign-in']);
  }
}
