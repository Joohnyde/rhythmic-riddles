import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Login } from "../../../components/login/login";

@Component({
  selector: 'app-admin-home',
  imports: [FormsModule, Login],
  templateUrl: './admin-home.html',
  styleUrl: './admin-home.scss',
})
export class AdminHome {

}
