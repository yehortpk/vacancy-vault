import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Connection } from '../type/account.type';
import { environment } from '../../environment';

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private baseUrl = environment.router_service_url + "/me";

  constructor(private http: HttpClient) { }

  /** Replaces the stored key; the returned one is the only time it is shown in clear. */
  generateApiKey(): Observable<{ apiKey: string }> {
    return this.http.post<{ apiKey: string }>(`${this.baseUrl}/api-key`, {});
  }

  getConnections(): Observable<Connection[]> {
    return this.http.get<Connection[]>(`${this.baseUrl}/connections`);
  }

  renameConnection(connection: Connection, title: string): Observable<Connection> {
    return this.http.patch<Connection>(
      `${this.baseUrl}/connections/${connection.provider}/${connection.id}`, { title });
  }

  deleteConnection(connection: Connection): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/connections/${connection.provider}/${connection.id}`);
  }
}
