import { ChangeDetectorRef, Component } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { Team } from '../../../entities/teams';
import { SeekTimer } from "../../../components/seek-timer/seek-timer";
import { firstValueFrom } from 'rxjs';
import { OdgovorService } from '../../../services/odgovor.service';
import { RedoslijedService } from '../../../services/redoslijed.service';
import { TeamScore } from '../../../entities/team.scores';
import { TeamScoreboard } from "../../../components/team-scoreboard/team-scoreboard";

@Component({
  selector: 'app-admin-stage2',
  imports: [SeekTimer, TeamScoreboard],
  templateUrl: './admin-stage2.html',
  styleUrl: './admin-stage2.scss',
})
export class AdminStage2 {

  pesma_id !: string
  pitanje !: string
  odgovor !: string
  tim !: Team | null;
  prekid_id !: string | null;
  seek !: number
  remaining !: number;
  zadnja_numera !: string;
  scenario: number = -1
  answer_duration !: number;

  teams : TeamScore[] = []

  constructor(
    private storage: Storage,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private odgovor_service: OdgovorService,
    private redoslijed_service: RedoslijedService
  ) {
    if (this.storage.code === "") {
      this.router.navigate(['admin'])
    } else if (this.storage.messageObservable) {
      const subs = storage.messageObservable.subscribe(async (mes) => {
        if (mes.type == "song_next") {
          this.pesma_id = mes.pesma;
          this.pitanje = mes.pitanje;
          this.odgovor = mes.odgovor;
          this.seek = 0;
          this.remaining = mes.remaining;
          this.answer_duration = mes.answer_duration;
          this.zadnja_numera = mes.zadnji;
          this.scenario = 4;
          this.tim = null;
          this.prekid_id = null;
        }
        else if (mes.type == "song_reveal") {
          this.scenario = 0;
        }
        else if (mes.type == "song_repeat") {
          this.seek = 0;
          this.remaining = mes.remaining;
          this.scenario = 4;
        }
        else if (mes.type == "answer") {
          const item = this.teams.find(x => x.tim === mes.team_id);
          if(item){
              item.odgovarao = mes.redoslijed_id
              item.bodovi += mes.correct ? 30 : -10;
          }
          this.scenario = mes.correct ? 0 : 4
        }
        else if (mes.type == "error_solved" && this.scenario == 3) {
          this.scenario = (mes.prev == null) ? 4 : mes.prev //Nisam bio ucitan kad je crko. Posto se ovo desi samo ako sam kliknuo dugme, a da se na spawnu desi to dugme znaci da sam bio u po pjesme
        }
        else if (mes.type == "pause" && this.scenario != 2) {
          if (mes.team !== "null") {
              const item = this.teams.find(x => x.tim === mes.team);
              if(item){
                this.tim = new Team()  //Find team with UUID
                this.tim.id = item.tim
                this.tim.ime = item.naziv
                this.tim.slika = item.slika
              }
            this.prekid_id = mes.prekid_id
          }else{
            //System crkao. Zapisi u koje si stanje bio a da nije 3
            if(this.scenario != 3){
              try{
                await firstValueFrom(odgovor_service.savePrevScenario(this.scenario));
              }catch(err){}
            }
          }
          
          this.scenario = 2 + Number(mes.team === "null")
        }
        else if (mes.type == 'welcome') {
          if (mes.stage != 'songs') { 
              subs.unsubscribe();
              this.router.navigate(['admin',mes.stage])
          } else {
            //Defaultna polja: pjesma_id, pitanje, odgovor, timovi, bodovi
            this.pesma_id = mes.pesma;
            this.pitanje = mes.pitanje;
            this.odgovor = mes.odgovor;
            this.zadnja_numera = mes.zadnji;
            this.teams = mes.scores;

            if (mes.revealed != null) {
              //Kraj pjesme
              if (mes.revealed == true)
                this.scenario = 0 //Pusti odgovor
              else
                this.scenario = 1 //Prikazi refresh ili dalje dugmad
            } else {
              this.seek = mes.seek;
              this.remaining = mes.remaining;
              this.answer_duration = mes.answer_duration;
              if (mes.team != null) {
                this.tim = mes.team
                this.prekid_id = mes.prekid_id;
                this.scenario = 2 //Prikazi tim koji odgovara i podakle
              } else if (mes.error != null) {
                this.scenario = 3 //Prikazi error
              } else {
                this.scenario = 4 //Pusti pesmu
              }
            }
          }
        }
        this.cdr.markForCheck();
      });
    }
  }
  onTimeChange(t: number) {
  }

  public async onTrackFinished() {
    if (this.scenario == 4) {
      this.scenario = 1;
    } else if (this.scenario == 0) {
      //Send dalje request
    }
  }

  in_transit: boolean = false
  async resolve_error() {
    if (!this.zadnja_numera || this.in_transit) return;

    this.in_transit = true;
    try {
      await firstValueFrom(this.odgovor_service.resolve_error(this.zadnja_numera));
    } catch (err) {

    }

    this.in_transit = false;
  }
  async answer(correct: boolean) {
    if (!this.prekid_id || this.in_transit) return;

    this.in_transit = true;
    try {
      await firstValueFrom(this.odgovor_service.answer(this.prekid_id, correct));
    } catch (err) {

    }

    this.in_transit = false;
  }

  onStateSave(state: { seek: number; remaining: number }) {
    this.seek = state.seek;
    this.remaining = state.remaining;
  }

  async nastavi(repeat: boolean) {
    if (this.scenario != 1 || this.in_transit || !this.zadnja_numera) return;
    this.in_transit = true;
    try {
      if(repeat) await firstValueFrom(this.redoslijed_service.refresh(this.zadnja_numera));
      else await firstValueFrom(this.redoslijed_service.reveal(this.zadnja_numera));
    } catch (err) {}
    this.in_transit = false;
  }

  async nextSong(){

    try {
        await firstValueFrom(this.redoslijed_service.next());
      } catch (err) { 
      }
  }

}
