import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Team } from '../../../entities/teams';
import { Router } from '@angular/router';
import { Storage } from '../../../utils/storage';
import { firstValueFrom } from 'rxjs';
import { TeamScore } from '../../../entities/team.scores';

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
    private cdr: ChangeDetectorRef
  ) {
    if (this.storage.code === "") {
      this.router.navigate(['admin'])
    }
  }
    
  async ngOnInit(): Promise<void> {
    if (this.storage.messageObservable) {
      const mes = await firstValueFrom(this.storage.messageObservable);
      if (mes.type == "welcome" && mes.stage == "winner") {
          this.scores = mes.scores;
          this.cdr.markForCheck();
      }
    }
  }

}
