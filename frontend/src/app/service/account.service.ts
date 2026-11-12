import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Connection, LinkSession, Provider } from '../type/account.type';
import { environment } from '../../environment';

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private baseUrl = environment.router_service_url + "/me";

  constructor(private http: HttpClient) { }

  getProviders(): Observable<Provider[]> {
    return this.http.get<Provider[]>(`${this.baseUrl}/providers`);
  }

  /** Starts a link session; the returned token and instructions are shown only now. */
  startLink(provider: Provider): Observable<LinkSession> {
    return this.http.post<LinkSession>(`${this.baseUrl}/connections/${provider.id}/link`, null);
  }

  getConnections(): Observable<Connection[]> {
    return this.http.get<Connection[]>(`${this.baseUrl}/connections`);
  }

  renameConnection(connection: Connection, title: string): Observable<Connection> {
    return this.http.patch<Connection>(`${this.baseUrl}/connections/${connection.id}`, { title });
  }

  deleteConnection(connection: Connection): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/connections/${connection.id}`);
  }
}
