import {
  AfterViewInit,
  Component,
  OnDestroy,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { Router } from '@angular/router';
import { GameSession } from '../../../core/session/game-session.service';
import { teamIconColor } from '../../../domain/game/models/team-icon.utils';
import { LobbyStore } from '../../../domain/game/state/lobby.store';
import { BrandLetteringComponent } from '../../../shared/ui/brand-lettering/brand-lettering.component';
import { TvMusicLinesComponent } from './components/tv-music-lines/tv-music-lines.component';
import {
  TV_MUSIC_LINE_COUNT,
  TV_TEAMS_PER_PAGE,
  clampPage,
  nextPage,
  pageCount,
  pathPointAtFraction,
  teamLinePosition,
  teamsForPage,
} from './tv-lobby-layout';

const PAGE_DURATION_MS = 5000;

@Component({
  selector: 'rr-tv-lobby-page',
  imports: [BrandLetteringComponent, TvMusicLinesComponent],
  templateUrl: './tv-lobby.page.html',
  styleUrl: './tv-lobby.page.scss',
})
export class TvLobbyPage implements OnInit, AfterViewInit, OnDestroy {
  readonly session = inject(GameSession);
  readonly store = inject(LobbyStore);

  private readonly router = inject(Router);
  private readonly teams = computed(() => this.store.vm().teams);
  private readonly currentPage = signal(0);
  private pageTimer?: number;
  private viewInitialized = false;

  readonly visibleTeams = computed(() =>
    teamsForPage(this.teams(), this.currentPage(), TV_TEAMS_PER_PAGE),
  );

  private readonly resizeHandler = () => this.scheduleTeamLayout();

  constructor() {
    effect(() => {
      const teams = this.teams();
      untracked(() => this.syncPagination(teams.length));
    });

    effect(() => {
      this.visibleTeams();
      untracked(() => this.scheduleTeamLayout());
    });
  }

  ngOnInit(): void {
    if (!this.session.code || !this.session.messages$) {
      void this.router.navigate(['']);
      return;
    }

    this.store.connect(this.session.messages$, 'tv');
  }

  ngAfterViewInit(): void {
    this.viewInitialized = true;
    this.scheduleTeamLayout();
    window.addEventListener('resize', this.resizeHandler);
  }

  ngOnDestroy(): void {
    this.store.disconnect();
    this.stopPageTimer();
    window.removeEventListener('resize', this.resizeHandler);
  }

  getTeamColor(image: string): string {
    return teamIconColor(image) ?? 'var(--primary)';
  }

  private syncPagination(teamCount: number): void {
    const validPage = clampPage(this.currentPage(), teamCount, TV_TEAMS_PER_PAGE);
    if (validPage !== this.currentPage()) {
      this.currentPage.set(validPage);
    }

    this.stopPageTimer();
    if (pageCount(teamCount, TV_TEAMS_PER_PAGE) <= 1) {
      return;
    }

    this.pageTimer = window.setInterval(() => {
      this.currentPage.update((page) => nextPage(page, this.teams().length, TV_TEAMS_PER_PAGE));
    }, PAGE_DURATION_MS);
  }

  private stopPageTimer(): void {
    if (this.pageTimer === undefined) {
      return;
    }
    window.clearInterval(this.pageTimer);
    this.pageTimer = undefined;
  }

  private scheduleTeamLayout(): void {
    if (!this.viewInitialized) {
      return;
    }
    window.requestAnimationFrame(() => this.moveTeams());
  }

  private moveTeams(): void {
    const svg = document.querySelector<SVGSVGElement>('.tv-lobby__lines');
    const teamsContainer = document.querySelector<HTMLElement>('.tv-lobby__teams');
    const paths = Array.from(document.querySelectorAll<SVGPathElement>('.music-line'));
    if (!svg || !teamsContainer || paths.length !== TV_MUSIC_LINE_COUNT) {
      return;
    }

    const allTeams = this.teams();
    const containerRect = teamsContainer.getBoundingClientRect();
    for (const teamElement of Array.from(document.querySelectorAll<HTMLElement>('.tv-team'))) {
      const teamId = teamElement.dataset['teamId'];
      const teamIndex = teamId ? allTeams.findIndex((team) => team.id === teamId) : -1;
      if (teamIndex < 0) {
        continue;
      }

      const team = allTeams[teamIndex];
      const position = teamLinePosition(team, teamIndex);
      const path = paths[position.lineIndex];
      const matrix = path.getScreenCTM();
      if (!matrix) {
        continue;
      }

      const point = pathPointAtFraction(path, position.fraction);
      const svgPoint = svg.createSVGPoint();
      svgPoint.x = point.x;
      svgPoint.y = point.y;
      const screenPoint = svgPoint.matrixTransform(matrix);
      const iconWidth =
        teamElement.querySelector<HTMLElement>('.tv-team__icon-wrap')?.getBoundingClientRect()
          .width ?? 0;

      teamElement.style.left = `${screenPoint.x - containerRect.left - iconWidth / 2}px`;
      teamElement.style.top = `${screenPoint.y - containerRect.top}px`;
    }
  }
}
