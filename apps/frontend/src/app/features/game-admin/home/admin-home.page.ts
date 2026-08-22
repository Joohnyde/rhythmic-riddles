import { Component } from '@angular/core';

import { LoginComponent } from '../../auth/login/login.component';
import { BrandLogoComponent } from '../../../shared/ui/brand-logo/brand-logo.component';

@Component({
  selector: 'rr-admin-home-page',
  imports: [LoginComponent, BrandLogoComponent],
  templateUrl: './admin-home.page.html',
  styleUrl: './admin-home.page.scss',
})
export class AdminHomePage {}
