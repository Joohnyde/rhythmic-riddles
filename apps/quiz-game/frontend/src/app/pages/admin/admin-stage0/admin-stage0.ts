import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { Team } from '../../../entities/teams';
import { TimService } from '../../../services/tim.service';
import { firstValueFrom } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { IgraService } from '../../../services/igra.service';

@Component({
  selector: 'app-admin-stage0',
  imports: [FormsModule],
  templateUrl: './admin-stage0.html',
  styleUrl: './admin-stage0.scss',
})
export class AdminStage0 implements OnInit {

  teams: Team[] = [];

  constructor(
    private storage: Storage,
    private router: Router,
    private cdr: ChangeDetectorRef,

    private timService: TimService,
    private igraService: IgraService
  ) {
    if (this.storage.code === "") {
      this.router.navigate(['admin'])
    }
  }
    
  async ngOnInit(): Promise<void> {
    if (this.storage.messageObservable) {
      const mes = await firstValueFrom(this.storage.messageObservable);
      if (mes.type == "welcome" && mes.stage == "lobby") {
          this.teams = mes.teams;
          this.cdr.markForCheck();
      }
    }
  }

  form = {
    ime: '',
    slika: '',
    dugme: '35c351d2-61a9-41aa-9231-17c19f905ee0'
  };

  in_transit = false;

  async createTeam() {
    if (this.in_transit) return;
    if (this.form.ime == "" || this.form.slika == "") return;

    const request = {
      ime: this.form.ime,
      slika: this.form.slika,
      dugme: this.form.dugme
    };


    this.in_transit = true;
    try {
      const new_team = await firstValueFrom(this.timService.createTeam(request));
      this.teams.push(new_team);

        this.cdr.markForCheck();
      this.form = {
        ime: '',
        slika: '',
        dugme: '35c351d2-61a9-41aa-9231-17c19f905ee0'
      }
    } catch (err) {

    }
    this.in_transit = false;
  }

  async kick(id: string) {

    if (this.in_transit) return;
    this.in_transit = true;
    try {
      await firstValueFrom(this.timService.kickTeam(id));
      this.teams = this.teams.filter(t => t.id !== id);
        this.cdr.markForCheck();
    } catch (err) {

    }
    this.in_transit = false;

  }

  async start(){
    if(this.in_transit) return;


    this.in_transit = true;
    try{
      await firstValueFrom(this.igraService.changeState(1));
      this.router.navigate(['admin', 'albums'])
    }catch(err){

    }
    this.in_transit = false;
  }
}
