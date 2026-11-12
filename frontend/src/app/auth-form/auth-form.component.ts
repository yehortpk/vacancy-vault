import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../service/auth.service';

type AuthMode = 'login' | 'register';

/** Login and registration share one form; the route's data says which one it is. */
@Component({
  selector: 'app-auth-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './auth-form.component.html'
})
export class AuthFormComponent {
  protected mode: AuthMode;
  protected email: string = '';
  protected password: string = '';
  protected error: string | null = null;
  protected isSubmitting: boolean = false;

  constructor(private authService: AuthService, private router: Router, private route: ActivatedRoute) {
    this.mode = this.route.snapshot.data['mode'] ?? 'login';
  }

  private describeError(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'Something went wrong. Please try again.';
    }
    switch (error.status) {
      case 401: return 'Invalid email or password.';
      case 409: return 'An account with this email already exists.';
      case 400: return 'Enter a valid email and a password of 8 to 72 characters.';
      default: return 'The server is unavailable. Please try again later.';
    }
  }

  onSubmit(): void {
    this.isSubmitting = true;
    this.error = null;

    const request = this.mode === 'login'
      ? this.authService.login(this.email, this.password)
      : this.authService.register(this.email, this.password);

    request.subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/account';
        this.router.navigateByUrl(returnUrl);
      },
      error: (error) => {
        this.error = this.describeError(error);
        this.isSubmitting = false;
      }
    });
  }
}
