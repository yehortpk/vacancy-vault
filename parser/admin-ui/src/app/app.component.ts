import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SessionService } from './service/session.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  standalone: true,
  imports: [CommonModule, RouterOutlet]
})
export class AppComponent {
  constructor(protected sessionService: SessionService) { }

  logout(): void {
    this.sessionService.logout();
  }
}
