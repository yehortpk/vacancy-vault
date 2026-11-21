import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { SessionService } from '../service/session.service';

/** The panel's only way in. There is no registration: admins come from the parser's configuration. */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html'
})
export class LoginComponent implements OnInit {
  protected username: string = '';
  protected password: string = '';
  protected error: string | null = null;
  protected isSubmitting: boolean = false;

  constructor(private sessionService: SessionService, private router: Router) { }

  ngOnInit(): void {
    // An admin who is already logged in has nothing to do here. For everyone else this request
    // is what brings the XSRF-TOKEN cookie the login needs, e.g. right after a logout cleared it
    this.sessionService.loadSession().subscribe((admin) => {
      if (admin) {
        this.router.navigate(['/']);
      }
    });
  }

  private describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Invalid username or password.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'The page is out of date. Reload it and try again.';
    }
    return 'The parser is unavailable. Please try again later.';
  }

  onSubmit(): void {
    this.isSubmitting = true;
    this.error = null;

    this.sessionService.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(['/']),
      error: (error) => {
        this.error = this.describeError(error);
        this.isSubmitting = false;
      }
    });
  }
}
