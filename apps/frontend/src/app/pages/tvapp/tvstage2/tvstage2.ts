import { ChangeDetectorRef, Component } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { Team } from '../../../entities/teams';
import { AudioPlayer } from '../../../components/audio-player/audio-player';
import { TeamScore } from '../../../entities/team.scores';
import { TeamScoreboard } from '../../../components/team-scoreboard/team-scoreboard';
import { firstValueFrom } from 'rxjs';
import { InterruptService } from '../../../services/interrupt.service';
import {
  S2AnswerMessage,
  S2ErrorSolvedMessage,
  S2PauseMessage,
  S2SongRepeatMessage,
  S2WelcomeMessage,
} from '../../../entities/messages/stage2.message';

@Component({
  selector: 'app-tvstage2',
  imports: [AudioPlayer, TeamScoreboard],
  templateUrl: './tvstage2.html',
  styleUrl: './tvstage2.scss',
})
export class TVStage2 {
  songId!: string;
  question!: string;
  answer!: string;
  answeringTeam!: Team | null;
  seek?: number;
  remaining?: number;
  answerDuration!: number;
  lastPlayedSong!: string;

  bravo: TeamScore | undefined;

  scenario: number = -1;

  teams: TeamScore[] = [];

  constructor(
    private storage: Storage,
    private router: Router,
    private interruptService: InterruptService,
    private cdr: ChangeDetectorRef,
  ) {
    if (this.storage.code === '') {
      this.router.navigate(['']);
    } else if (this.storage.messageObservable) {
      const subs = this.storage.messageObservable.subscribe(async (mes) => {
        switch (mes.type) {
          case 'song_next': {
            const songNextMessage = mes as S2WelcomeMessage;
            this.songId = songNextMessage.songId;
            this.question = songNextMessage.question;
            this.answer = songNextMessage.answer;
            this.seek = 0;
            this.remaining = songNextMessage.remaining;
            this.answerDuration = songNextMessage.answerDuration;

            this.lastPlayedSong = songNextMessage.scheduleId;
            this.bravo = undefined;
            this.scenario = 4;
            this.answeringTeam = null;
            break;
          }
          case 'song_reveal': {
            this.scenario = 0;
            break;
          }
          case 'song_repeat': {
            this.seek = 0;
            this.remaining = (mes as S2SongRepeatMessage).remaining;
            this.scenario = 4;
            break;
          }
          case 'answer': {
            const answerMessage = mes as S2AnswerMessage;
            const item = this.teams.find((x) => x.teamId === answerMessage.teamId);
            if (item) {
              item.scheduleId = answerMessage.scheduleId;
              item.score += answerMessage.correct ? 30 : -10;
            }
            if (answerMessage) this.bravo = item;
            this.scenario = answerMessage.correct ? 0 : 4;
            break;
          }
          case 'error_solved': {
            this.scenario = (mes as S2ErrorSolvedMessage).previousScenario; //Nisam bio ucitan kad je crko. Posto se ovo desi samo ako sam kliknuo dugme, a da se na spawnu desi to dugme znaci da sam bio u po pjesme
            break;
          }
          case 'pause': {
            const pauseMessage = mes as S2PauseMessage;
            if (this.scenario == 2) break;

            if (pauseMessage.answeringTeamId !== 'null') {
              const item = this.teams.find((x) => x.teamId === pauseMessage.answeringTeamId);
              if (item) {
                this.answeringTeam = new Team(item); //Find team with UUID
              }
            } else {
              if (this.scenario != 3) {
                try {
                  await firstValueFrom(interruptService.savePrevScenario(this.scenario));
                } catch (_ignored) {
                  /*TODO: Toast*/
                }
              }
            }
            this.scenario = 2 + Number(pauseMessage.answeringTeamId === 'null');
            break;
          }
          case 'welcome': {
            const welcomeMessage = mes as S2WelcomeMessage;
            if (welcomeMessage.stage == 'songs') {
              /* Default fields: songId, question,
               answer, scheduleId, answerDuration, scores */
              this.songId = welcomeMessage.songId;
              this.question = welcomeMessage.question;
              this.answer = welcomeMessage.answer;
              this.teams = welcomeMessage.scores;
              this.lastPlayedSong = welcomeMessage.scheduleId;
              this.answerDuration = welcomeMessage.answerDuration;

              if (welcomeMessage.revealed != null) {
                // The song has finished
                if (welcomeMessage.revealed == true) {
                  this.bravo = this.teams.find((x) => x.teamId === welcomeMessage.bravo);
                  this.scenario = 0; // The song was revealed so we should show the guesser
                } else this.scenario = 1; // The song was NOT revealed so show "replay" and "reveal" buttons
              } else {
                this.seek = welcomeMessage.seek;
                this.remaining = welcomeMessage.remaining;
                if (welcomeMessage.answeringTeam != null) {
                  this.answeringTeam = welcomeMessage.answeringTeam;
                  this.scenario = 2; // A team is currently answering. Show team data
                } else if (welcomeMessage.error != null) {
                  this.scenario = 3; // An error occured
                } else {
                  this.scenario = 4; // The song is playing
                }
              }
            } else {
              subs.unsubscribe();
              this.router.navigate([welcomeMessage.stage]);
            }
            break;
          }
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
