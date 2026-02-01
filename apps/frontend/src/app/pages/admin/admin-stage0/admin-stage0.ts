import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { Team } from '../../../entities/teams';
import { TeamService } from '../../../services/team.service';
import { firstValueFrom } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { GameService } from '../../../services/game.service';

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

    private teamService: TeamService,
    private gameService: GameService
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
    name: '',
    image: '',
    buttonCode: '1671'
  };

  inTransit = false;

  async createTeam() {
    if (this.inTransit) return;
    if (this.form.name == "" || this.form.image == "") return;

    const request = {
      name: this.form.name,
      image: this.form.image,
      buttonCode: this.form.buttonCode
    };


    this.inTransit = true;
    try {
      const new_team = await firstValueFrom(this.teamService.createTeam(request));
      this.teams.push(new_team);

        this.cdr.markForCheck();
      this.form = {
        name: '',
        image: '',
        buttonCode: '1671'
      }
    } catch (err) {

    }
    this.inTransit = false;
  }

  async kickTeam(teamId: string) {
    if (this.inTransit) return;
    this.inTransit = true;
    try {
      await firstValueFrom(this.teamService.kickTeam(teamId));
      this.teams = this.teams.filter(t => t.id !== teamId);
        this.cdr.markForCheck();
    } catch (err) {

    }
    this.inTransit = false;

  }

  async start(){
    if(this.inTransit) return;


    this.inTransit = true;
    try{
      await firstValueFrom(this.gameService.changeState(1));
      this.router.navigate(['admin', 'albums'])
    }catch(err){

    }
    this.inTransit = false;
  }
}
