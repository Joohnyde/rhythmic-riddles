import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Login } from "../../../components/login/login";

@Component({
  selector: 'app-tvhome',
  imports: [FormsModule, Login],
  templateUrl: './tvhome.html',
  styleUrl: './tvhome.scss',
})
export class TVHome {
  
}
