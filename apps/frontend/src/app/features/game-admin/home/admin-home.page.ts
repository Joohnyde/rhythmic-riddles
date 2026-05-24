import { Component } from '@angular/core';
import { LoginComponent } from '../../auth/login/login.component';
@Component({
  selector: 'rr-admin-home-page',
  imports: [LoginComponent],
  templateUrl: './admin-home.page.html',
  styleUrl: './admin-home.page.scss',
})
export class AdminHomePage {}
