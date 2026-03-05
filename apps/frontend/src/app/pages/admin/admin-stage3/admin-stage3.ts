import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { firstValueFrom } from 'rxjs';
import { TeamScore } from '../../../entities/team.scores';
import { S3WelcomeMessage } from '../../../entities/messages/stage3.messages';

@Component({
  selector: 'app-admin-stage3',
  imports: [],
  templateUrl: './admin-stage3.html',
  styleUrl: './admin-stage3.scss',
})
export class AdminStage3 implements OnInit {
  scores: TeamScore[] = [];

  constructor(
    private storage: Storage,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {
    if (this.storage.code === '') {
      this.router.navigate(['admin']);
    }
  }

  async ngOnInit(): Promise<void> {
    if (this.storage.messageObservable) {
      try {
        const mes = (await firstValueFrom(this.storage.messageObservable)) as S3WelcomeMessage;
        if (mes.type == 'welcome' && mes.stage == 'winner') {
          this.scores = mes.scores;
          this.cdr.markForCheck();
        }
      } catch (_ignored) {
        //Do nothing
      }
    }
  }
}
