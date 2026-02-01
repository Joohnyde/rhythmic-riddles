import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { CategorySimple } from '../../../entities/albums';
import { LastCategory } from '../../../entities/selected.album';
import { Team } from '../../../entities/teams';

@Component({
  selector: 'app-tvstage1',
  imports: [],
  templateUrl: './tvstage1.html',
  styleUrl: './tvstage1.scss',
})
export class TVStage1{

  albums : CategorySimple[] = [];
  pickedByTeam !: Team;
  selectedAlbum !: LastCategory;
  loaded : boolean = false;
  showButtons : boolean = false;

 constructor(private storage: Storage, private router: Router,
  private cdr: ChangeDetectorRef){
    if(this.storage.code === ""){
      this.router.navigate([''])
    }else if(this.storage.messageObservable){
      const subs = this.storage.messageObservable.subscribe((mes)=>{
        switch(mes.type){
          case "welcome":
            if(mes.stage == "albums"){
              if(mes.albums != null){
                this.albums = mes.albums;
                this.pickedByTeam = mes.team;
              }else{
                this.selectedAlbum = mes.selected;
                this.pickedByTeam = this.selectedAlbum.pickedByTeam
                this.showButtons = true;
              }
              this.loaded = true;
            }else{
              subs.unsubscribe();
              this.router.navigate([mes.stage])
            }
            break;
          case "album_picked":
            this.showButtons = true;
            this.selectedAlbum = mes.selected;
            this.pickedByTeam = this.selectedAlbum.pickedByTeam
            break;
        }
        this.cdr.markForCheck();
        
      });
    }
  }

}