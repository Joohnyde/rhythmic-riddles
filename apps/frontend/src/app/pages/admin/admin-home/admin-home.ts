import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Login } from "../../../components/login/login";
import { AppService } from '../../../services/app.service';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-admin-home',
  imports: [FormsModule, Login],
  templateUrl: './admin-home.html',
  styleUrl: './admin-home.scss',
})
export class AdminHome {

  constructor(private appService: AppService){
  }

  public async killApp(){
    await firstValueFrom(this.appService.killApp());
  }
}
