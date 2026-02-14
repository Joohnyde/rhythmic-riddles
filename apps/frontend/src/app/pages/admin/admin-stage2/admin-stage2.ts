import { ChangeDetectorRef, Component } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { Team } from '../../../entities/teams';
import { SeekTimer } from "../../../components/seek-timer/seek-timer";
import { firstValueFrom } from 'rxjs';
import { InterruptService } from '../../../services/interrupt.service';
import { ScheduleService } from '../../../services/schedule.service';
import { TeamScore } from '../../../entities/team.scores';
import { TeamScoreboard } from "../../../components/team-scoreboard/team-scoreboard";

@Component({
  selector: 'app-admin-stage2',
  imports: [SeekTimer, TeamScoreboard],
  templateUrl: './admin-stage2.html',
  styleUrl: './admin-stage2.scss',
})
export class AdminStage2 {

  songId !: string
  question !: string
  answer !: string
  answeringTeam !: Team | null;
  interruptId !: string | null;
  seek !: number
  remaining !: number;
  lastPlayedSong !: string;
  /* UI scenario state machine:
     0 = reveal answer screen
     1 = snippet ended (show "repeat / reveal / next" controls)
     2 = team is answering (paused for guess)
     3 = system error/pause (requires "resolve")
     4 = playing snippet */
  scenario: number = -1
  answerDuration !: number;

  teams: TeamScore[] = []
  bravo: TeamScore | undefined

  constructor(
    private storage: Storage,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private interruptService: InterruptService,
    private scheduleService: ScheduleService
  ) {
    if (this.storage.code === "") {
      this.router.navigate(['admin'])
    } else if (this.storage.messageObservable) {
      const subs = storage.messageObservable.subscribe(async (mes) => {
        if (mes.type == "song_next") {
          this.songId = mes.songId;
          this.question = mes.question;
          this.answer = mes.answer;
          this.seek = 0;
          this.remaining = mes.remaining;
          this.answerDuration = mes.answerDuration;

          this.lastPlayedSong = mes.scheduleId;
          this.scenario = 4;
          this.answeringTeam = null;
          this.interruptId = null;
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
          const item = this.teams.find(x => x.teamId === mes.teamId);
          if (item) {
            item.scheduleId = mes.scheduleId
            item.score += mes.correct ? 30 : -10;
          }
          this.scenario = mes.correct ? 0 : 4
        }
        else if (mes.type == "error_solved" && this.scenario == 3) {
          /* If we weren't loaded when the crash happened, backend tells us what we were doing before error-mode,
             so we can restore the correct UI state after "error_solved". */ 
          //Nisam bio ucitan kad je crko. Posto se ovo desi samo ako sam kliknuo dugme, a da se na spawnu desi to dugme znaci da sam bio u po pjesme
          this.scenario = (mes.previousScenario == null) ? 4 : mes.previousScenario 
        }
        else if (mes.type == "pause" && this.scenario != 2) {
          if (mes.team !== "null") {
            const item = this.teams.find(x => x.teamId === mes.answeringTeamId);
            if (item) {
              this.answeringTeam = new Team(item)  // Find team with UUID
            }
            if (mes.correct)
              this.bravo = item;
            this.interruptId = mes.interruptId
          } else {
            // System interrupt. Send my scenarion on the backend (if it's not 3)
            if (this.scenario != 3) {
              try {
                await firstValueFrom(interruptService.savePrevScenario(this.scenario));
              } catch (err) { }
            }
          }

          this.scenario = 2 + Number(mes.answeringTeamId === "null")
        }
        else if (mes.type == 'welcome') {
          if (mes.stage != 'songs') {
            subs.unsubscribe();
            this.router.navigate(['admin', mes.stage])
          } else {
            //Defaultna polja: pjesma_id, pitanje, odgovor, timovi, bodovi
            this.songId = mes.songId;
            this.question = mes.question;
            this.answer = mes.answer;
            this.lastPlayedSong = mes.scheduleId;
            this.teams = mes.scores;
            this.answerDuration = mes.answerDuration;
            if (mes.revealed != null) {
              //Kraj pjesme
              if (mes.revealed == true) {
                this.scenario = 0 //Pusti odgovor
                this.bravo = mes.bravo
              }
              else
                this.scenario = 1 //Prikazi refresh ili dalje dugmad
            } else {
              this.seek = mes.seek;
              this.remaining = mes.remaining;
              if (mes.answeringTeam != null) {
                this.answeringTeam = mes.answeringTeam
                this.interruptId = mes.interruptId;
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

  inTransit: boolean = false
  async resolveError() {
    if (!this.lastPlayedSong || this.inTransit) return;

    this.inTransit = true;
    try {
      await firstValueFrom(this.interruptService.resolveErrors(this.lastPlayedSong));
    } catch (err) {

    }

    this.inTransit = false;
  }
  async teamAnswered(correct: boolean) {
    if (!this.interruptId || this.inTransit) return;

    this.inTransit = true;
    try {
      await firstValueFrom(this.interruptService.answer(this.interruptId, correct));
    } catch (err) {

    }

    this.inTransit = false;
  }

  onStateSave(state: { seek: number; remaining: number }) {
    this.seek = state.seek;
    this.remaining = state.remaining;
  }

  // Advances the game flow: either replays the current song or reveals it and moves to the next one
  async advanceGame(repeatSong: boolean) {
    if (this.scenario != 1 || this.inTransit || !this.lastPlayedSong) return;
    this.inTransit = true;
    try {
      if (repeatSong) await firstValueFrom(this.scheduleService.replaySong(this.lastPlayedSong));
      else await firstValueFrom(this.scheduleService.revealAnswer(this.lastPlayedSong));
    } catch (err) { }
    this.inTransit = false;
  }

  async nextSong() {
    try {
      await firstValueFrom(this.scheduleService.next());
    } catch (err) {
    }
  }

}
