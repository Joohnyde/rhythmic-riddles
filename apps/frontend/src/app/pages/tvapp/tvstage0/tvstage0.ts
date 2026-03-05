import { ChangeDetectorRef, Component } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { Team } from '../../../entities/teams';
import {
  S0KickTeamMessage,
  S0NewTeamMessage,
  S0WelcomeMessage,
} from '../../../entities/messages/stage0.messages';

@Component({
  selector: 'app-tvstage0',
  imports: [],
  templateUrl: './tvstage0.html',
  styleUrl: './tvstage0.scss',
})
export class TVStage0 {
  teams: Team[] = [];

  constructor(
    private storage: Storage,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {
    if (this.storage.code === '') {
      this.router.navigate(['']);
    } else if (this.storage.messageObservable) {
      const subs = this.storage.messageObservable.subscribe((mes) => {
        switch (mes.type) {
          case 'welcome': {
            const welcomeMessage = mes as S0WelcomeMessage;
            if (welcomeMessage.stage == 'lobby') {
              this.teams = welcomeMessage.teams;
            } else {
              subs.unsubscribe();
              this.router.navigate([welcomeMessage.stage]);
            }
            break;
          }
          case 'new_team': {
            const newTeamMessage = mes as S0NewTeamMessage;
            this.teams.push(newTeamMessage.team);
            break;
          }
          case 'kick_team': {
            const kickTeamMessage = mes as S0KickTeamMessage;
            this.teams = this.teams.filter((t) => t.id !== kickTeamMessage.uuid);
            break;
          }
        }
        this.cdr.markForCheck();
      });
    }
  }
}
