import { Component } from '@angular/core';

import { LoginComponent } from '../../auth/login/login.component';
import { BrandLogoComponent } from '../../../shared/ui/brand-logo/brand-logo.component';

@Component({
  selector: 'rr-tv-home-page',
  imports: [LoginComponent, BrandLogoComponent],
  templateUrl: './tv-home.page.html',
  styleUrl: './tv-home.page.scss',
})
export class TvHomePage {}
