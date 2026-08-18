import { Component } from '@angular/core';

import { LoginComponent } from '../../auth/login/login.component';
import { BrandWordmarkComponent } from '../../../shared/ui/brand-wordmark/brand-wordmark.component';

@Component({
  selector: 'rr-tv-home-page',
  imports: [LoginComponent, BrandWordmarkComponent],
  templateUrl: './tv-home.page.html',
  styleUrl: './tv-home.page.scss',
})
export class TvHomePage {}
