import { ChangeDetectorRef, Component } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { Team } from '../../../entities/teams';

@Component({
  selector: 'app-tvstage0',
  imports: [],
  templateUrl: './tvstage0.html',
  styleUrl: './tvstage0.scss',
})
export class TVStage0 {
  teams: Team[] = [];
  
  constructor(private storage: Storage, private router: Router,
  private cdr: ChangeDetectorRef){
    if(this.storage.code === ""){
      this.router.navigate([''])
    }else if(this.storage.messageObservable){
      const subs = this.storage.messageObservable.subscribe((mes)=>{
        switch(mes.type){
          case "welcome":
            if(mes.stage == "lobby"){
              this.teams = mes.teams;
            }else{
              subs.unsubscribe();
              this.router.navigate([mes.stage])
            }
            break;
          case "new_team":
            this.teams.push(mes.team);
            break;
          case "kick_team":
            this.teams = this.teams.filter(t => t.id !== mes.uuid);
            break;
        }
        this.cdr.markForCheck();
        
      });
    }
  }
}
