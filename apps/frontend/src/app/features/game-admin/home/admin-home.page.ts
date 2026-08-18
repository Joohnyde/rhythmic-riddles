import { Component } from '@angular/core';

import { LoginComponent } from '../../auth/login/login.component';
import { BrandWordmarkComponent } from '../../../shared/ui/brand-wordmark/brand-wordmark.component';

@Component({
  selector: 'rr-admin-home-page',
  imports: [LoginComponent, BrandWordmarkComponent],
  templateUrl: './admin-home.page.html',
  styleUrl: './admin-home.page.scss',
})
export class AdminHomePage {}
