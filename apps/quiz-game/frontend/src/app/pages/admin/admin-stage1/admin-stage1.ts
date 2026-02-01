import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { firstValueFrom } from 'rxjs';
import { Album } from '../../../entities/albums';
import { SelectedAlbum } from '../../../entities/selected.album';
import { Team } from '../../../entities/teams';
import { KategorijaService } from '../../../services/kategorija.service';

@Component({
  selector: 'app-admin-stage1',
  imports: [],
  templateUrl: './admin-stage1.html',
  styleUrl: './admin-stage1.scss',
})
export class AdminStage1 implements OnInit{

  albums : Album[] = [];
  birac !: Team;
  selected !: SelectedAlbum;
  loaded : boolean = false;
  show_buttons : boolean = false;

 constructor(
    private storage: Storage,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private kategorija_service: KategorijaService
  ) {
    if (this.storage.code === "") {
      this.router.navigate(['admin'])
    }
  }

    async ngOnInit(): Promise<void> {
      if (this.storage.messageObservable) {
        const mes = await firstValueFrom(this.storage.messageObservable);
        if (mes.type == "welcome" && mes.stage == "albums") {
            this.loaded = true;

            if(mes.albums != null){
              this.albums = mes.albums;
              this.birac = mes.team;
            }else{
              this.selected = mes.selected;
              this.birac = this.selected.birac
              this.show_buttons = true;
            }
            
            this.cdr.markForCheck();
        }
      }
    }

    in_transit : boolean = false;
    async pick(kategorija_id: string){
      if(this.in_transit) return;
      let team_id = null;
      if(this.birac) team_id = this.birac.id;
      this.in_transit = true;
      try{
        this.selected = await firstValueFrom(this.kategorija_service.pick(kategorija_id, team_id))
        this.birac = this.selected.birac
        this.show_buttons = true;

        this.cdr.markForCheck();
      }catch(err){

      }
      this.in_transit = false;
    }


    async start(){
      if(this.in_transit || !this.selected) return;
      this.in_transit = true;
      try{
        await firstValueFrom(this.kategorija_service.start(this.selected.kategorija))
        //Idemo u stage 2
        this.router.navigate(['admin','songs'])
      }catch(err){

      }
      this.in_transit = false;
    }
}
