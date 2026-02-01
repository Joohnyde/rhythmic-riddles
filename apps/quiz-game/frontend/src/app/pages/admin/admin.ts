import { Component} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterOutlet } from "../../../../node_modules/@angular/router/types/_router_module-chunk";

@Component({
  selector: 'app-admin',
  imports: [FormsModule, RouterOutlet],
  templateUrl: './admin.html',
  styleUrl: './admin.scss',
})
export class Admin {

}
