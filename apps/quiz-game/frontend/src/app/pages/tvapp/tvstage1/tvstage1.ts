import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { firstValueFrom } from 'rxjs';
import { Album } from '../../../entities/albums';
import { SelectedAlbum } from '../../../entities/selected.album';
import { Team } from '../../../entities/teams';

@Component({
  selector: 'app-tvstage1',
  imports: [],
  templateUrl: './tvstage1.html',
  styleUrl: './tvstage1.scss',
})
export class TVStage1{

  albums : Album[] = [];
  birac !: Team;
  selected !: SelectedAlbum;
  loaded : boolean = false;
  show_buttons : boolean = false;

 constructor(private storage: Storage, private router: Router,
  private cdr: ChangeDetectorRef){
    if(this.storage.code === ""){
      this.router.navigate([''])
    }else if(this.storage.messageObservable){
      const subs = this.storage.messageObservable.subscribe((mes)=>{
        switch(mes.type){
          case "welcome":
            if(mes.stage == "albums"){
              
              this.loaded = true;
              if(mes.albums != null){
                this.albums = mes.albums;
                this.birac = mes.team;
              }else{
                this.selected = mes.selected;
                this.birac = this.selected.birac
                this.show_buttons = true;
              }

            }else{
              subs.unsubscribe();
              this.router.navigate([mes.stage])
            }
            break;
          case "album_picked":
            this.show_buttons = true;
            this.selected = mes.selected;
            this.birac = this.selected.birac
            break;
        }
        this.cdr.markForCheck();
        
      });
    }
  }

}