import { Component } from '@angular/core';
import { LoginComponent } from '../../auth/login/login.component';
@Component({
  selector: 'rr-tv-home-page',
  imports: [LoginComponent],
  templateUrl: './tv-home.page.html',
  styleUrl: './tv-home.page.scss',
})
export class TvHomePage {}
