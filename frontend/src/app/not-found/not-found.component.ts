import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

/** Shown for any url no route matches. */
@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './not-found.component.html'
})
export class NotFoundComponent { }
