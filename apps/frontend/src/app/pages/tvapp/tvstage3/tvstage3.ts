import { ChangeDetectorRef, Component } from '@angular/core';
import { Storage } from '../../../utils/storage';
import { Router } from '@angular/router';
import { TeamScore } from '../../../entities/team.scores';
import { S3WelcomeMessage } from '../../../entities/messages/stage3.messages';

@Component({
  selector: 'app-tvstage3',
  imports: [],
  templateUrl: './tvstage3.html',
  styleUrl: './tvstage3.scss',
})
export class Tvstage3 {
  scores: TeamScore[] = [];

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
            const welcomeMessage = mes as S3WelcomeMessage;
            if (welcomeMessage.stage == 'winner') {
              this.scores = welcomeMessage.scores;
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
}
