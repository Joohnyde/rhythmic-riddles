import { ChangeDetectorRef, Component } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { Team } from '../../../entities/teams';
import { AudioPlayer } from "../../../components/audio-player/audio-player";
import { TeamScore } from '../../../entities/team.scores';
import { TeamScoreboard } from "../../../components/team-scoreboard/team-scoreboard";
import { firstValueFrom } from 'rxjs';
import { OdgovorService } from '../../../services/odgovor.service';

@Component({
  selector: 'app-tvstage2',
  imports: [AudioPlayer, TeamScoreboard],
  templateUrl: './tvstage2.html',
  styleUrl: './tvstage2.scss',
})
export class TVStage2 {

  pesma_id !: string
  pitanje !: string
  odgovor !: string
  tim !: Team | null;
  seek !: number
  remaining !: number;
  answer_duration !: number
  current_redoslijed_id !: string

  bravo: any | null;

  scenario: number = -1

  teams: TeamScore[] = []

  constructor(private storage: Storage, private router: Router,
    private odgovor_service: OdgovorService,
    private cdr: ChangeDetectorRef) {
    if (this.storage.code === "") {
      this.router.navigate([''])
    } else if (this.storage.messageObservable) {
      const subs = this.storage.messageObservable.subscribe(async (mes) => {
        switch (mes.type) {
          case "song_next":
            this.pesma_id = mes.pesma;
            this.pitanje = mes.pitanje;
            this.odgovor = mes.odgovor;
            this.current_redoslijed_id = mes.zadnji;
            this.bravo = null;
            this.seek = 0;
            this.remaining = mes.remaining;
            this.answer_duration = mes.answer_duration;
            this.scenario = 4;
            this.tim = null;
            break;
          case "song_reveal":
            this.scenario = 0;
            break;
          case "song_repeat":
            this.seek = 0;
            this.remaining = mes.remaining;
            this.scenario = 4;
            break;
          case "answer":
            const item = this.teams.find(x => x.tim === mes.team_id);
            if (item) {
              item.odgovarao = mes.redoslijed_id
              item.bodovi += mes.correct ? 30 : -10;
            }
            if (mes.correct) this.bravo = item;
            this.scenario = mes.correct ? 0 : 4;
            break;
          case "error_solved":
            this.scenario = (mes.prev == null) ? 4 : mes.prev
            break;
          case "pause":
            if (this.scenario == 2) break;

            if (mes.team !== "null") {
              const item = this.teams.find(x => x.tim === mes.team);
              if (item) {
                this.tim = new Team()  //Find team with UUID
                this.tim.id = item.tim
                this.tim.ime = item.naziv
                this.tim.slika = item.slika
              }
            } else {
              if (this.scenario != 3) {
                try {
                  await firstValueFrom(odgovor_service.savePrevScenario(this.scenario));
                } catch (err) { }
              }
            }
            this.scenario = 2 + Number(mes.team === "null")
            break;
          case "welcome":
            if (mes.stage == "songs") {
              //Defaultna polja: pjesma_id, pitanje, odgovor, timovi, bodovi

              this.pesma_id = mes.pesma;
              this.pitanje = mes.pitanje;
              this.odgovor = mes.odgovor;
              this.teams = mes.scores;
              this.current_redoslijed_id = mes.zadnji;

              if (mes.revealed != null) {
                //Kraj pjesme
                if (mes.revealed == true) {
                  this.bravo = this.teams.find(x => x.tim === mes.bravo);
                  this.scenario = 0 //Pusti odgovor
                } else
                  this.scenario = 1 //Prikazi refresh ili dalje dugmad
              } else {
                this.seek = mes.seek;
                this.remaining = mes.remaining;
                this.answer_duration = mes.answer_duration;
                if (mes.team != null) {
                  this.tim = mes.team
                  this.scenario = 2 //Prikazi tim koji odgovara i podakle
                } else if (mes.error != null) {
                  this.scenario = 3 //Prikazi error
                } else {
                  this.scenario = 4 //Pusti pesmu
                }
              }
            } else {
              subs.unsubscribe();
              this.router.navigate([mes.stage])
            }
            break;
        }
        this.cdr.markForCheck();

      });
    }
  }

  public onTrackFinished() {
    if (this.scenario == 4) {
      this.scenario = 1;
    } else if (this.scenario == 0) {
      //Napravi animaciju loadanja sledece runde
    }
  }

  onStateSave(state: { seek: number; remaining: number }) {
    this.seek = state.seek;
    this.remaining = state.remaining;
  }
}
