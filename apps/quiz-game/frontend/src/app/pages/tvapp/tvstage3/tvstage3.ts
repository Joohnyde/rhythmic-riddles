import { ChangeDetectorRef, Component } from '@angular/core';
import { Team } from '../../../entities/teams';
import { Storage } from '../../../utils/storage';
import { Router } from '@angular/router';

@Component({
  selector: 'app-tvstage3',
  imports: [],
  templateUrl: './tvstage3.html',
  styleUrl: './tvstage3.scss',
})
export class Tvstage3 {
  teams: Team[] = [];
  
  constructor(private storage: Storage, private router: Router,
  private cdr: ChangeDetectorRef){
    if(this.storage.code === ""){
      this.router.navigate([''])
    }else if(this.storage.messageObservable){
      const subs = this.storage.messageObservable.subscribe((mes)=>{
        switch(mes.type){
          case "welcome":
            if(mes.stage == "winner"){
              this.teams = mes.teams;
            }else{
              subs.unsubscribe();
              this.router.navigate([mes.stage])
            }
            break;
        }
        this.cdr.markForCheck();
        
      });
    }
  }
}