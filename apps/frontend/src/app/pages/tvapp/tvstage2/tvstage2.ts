import { ChangeDetectorRef, Component } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { Team } from '../../../entities/teams';
import { AudioPlayer } from "../../../components/audio-player/audio-player";
import { TeamScore } from '../../../entities/team.scores';
import { TeamScoreboard } from "../../../components/team-scoreboard/team-scoreboard";
import { firstValueFrom } from 'rxjs';
import { InterruptService } from '../../../services/interrupt.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-tvstage2',
  imports: [AudioPlayer, TeamScoreboard],
  templateUrl: './tvstage2.html',
  styleUrl: './tvstage2.scss',
})
export class TVStage2 {

  public environment = environment;

  songId !: string
  question !: string
  answer !: string
  answeringTeam !: Team | null;
  seek !: number
  remaining !: number;
  answerDuration !: number
  lastPlayedSong !: string

  bravo: any | null;

  scenario: number = -1

  teams: TeamScore[] = []

  constructor(private storage: Storage, private router: Router,
    private interruptService: InterruptService,
    private cdr: ChangeDetectorRef) {
    if (this.storage.code === "") {
      this.router.navigate([''])
    } else if (this.storage.messageObservable) {
      const subs = this.storage.messageObservable.subscribe(async (mes) => {
        switch (mes.type) {
          case "song_next":
            this.songId = mes.songId;
            this.question = mes.question;
            this.answer = mes.answer;
            this.seek = 0;
            this.remaining = mes.remaining;
            this.answerDuration = mes.answerDuration;

            this.lastPlayedSong = mes.scheduleId;
            this.bravo = null;
            this.scenario = 4;
            this.answeringTeam = null;
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
            const item = this.teams.find(x => x.teamId === mes.teamId);
            if (item) {
              item.scheduleId = mes.scheduleId
              item.score += mes.correct ? 30 : -10;
            }
            if (mes.correct) this.bravo = item;
            this.scenario = mes.correct ? 0 : 4;
            break;
          case "error_solved":
            this.scenario = (mes.previousScenario == null) ? 4 : mes.previousScenario //Nisam bio ucitan kad je crko. Posto se ovo desi samo ako sam kliknuo dugme, a da se na spawnu desi to dugme znaci da sam bio u po pjesme
            break;
          case "pause":
            if (this.scenario == 2) break;

            if (mes.team !== "null") {
              const item = this.teams.find(x => x.teamId === mes.answeringTeamId);
              if (item) {
                this.answeringTeam = new Team(item)  //Find team with UUID
              }
            } else {
              if (this.scenario != 3) {
                try {
                  await firstValueFrom(interruptService.savePrevScenario(this.scenario));
                } catch (err) { }
              }
            }
            this.scenario = 2 + Number(mes.answeringTeamId === "null")
            break;
          case "welcome":
            if (mes.stage == "songs") {
              /* Default fields: songId, question,
               answer, scheduleId, answerDuration, scores */
              this.songId = mes.songId;
              this.question = mes.question;
              this.answer = mes.answer;
              this.teams = mes.scores;
              this.lastPlayedSong = mes.scheduleId;
              this.answerDuration = mes.answerDuration;

              if (mes.revealed != null) {
                // The song has finished
                if (mes.revealed == true) {
                  this.bravo = this.teams.find(x => x.teamId === mes.bravo);
                  this.scenario = 0 // The song was revealed so we should show the guesser
                } else
                  this.scenario = 1 // The song was NOT revealed so show "replay" and "reveal" buttons
              } else {
                this.seek = mes.seek;
                this.remaining = mes.remaining;
                if (mes.team != null) {
                  this.answeringTeam = mes.answeringTeam
                  this.scenario = 2 // A team is currently answering. Show team data
                } else if (mes.error != null) {
                  this.scenario = 3 // An error occured
                } else {
                  this.scenario = 4 // The song is playing
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
      // TODO: Play loading animation for the next round
    }
  }

  onStateSave(state: { seek: number; remaining: number }) {
    this.seek = state.seek;
    this.remaining = state.remaining;
  }
}
