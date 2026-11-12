import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

/** Shown when a signed-in account opens a page its role does not allow. */
@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './forbidden.component.html'
})
export class ForbiddenComponent { }
